package com.jingwei.rsswithai.application.service;

import com.jingwei.rsswithai.application.dto.TopicEventTrackingDTO;
import com.jingwei.rsswithai.config.AppConfig;
import com.jingwei.rsswithai.domain.model.Subscription;
import com.jingwei.rsswithai.domain.model.SubscriptionType;
import com.jingwei.rsswithai.domain.model.Topic;
import com.jingwei.rsswithai.domain.repository.SubscriptionRepository;
import com.jingwei.rsswithai.domain.repository.TopicRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class TopicEventTrackingService {

    private static final int MIN_TOPIC_LENGTH = 12;
    private static final int MIN_CANDIDATE_COUNT = 5;
    private static final int MAX_CANDIDATE_COUNT = 20;
    private static final int MAX_NODE_COUNT = 12;
    private static final int MAX_NODE_ARTICLE_COUNT = 3;
    private static final long GENERATION_INTERVAL_HOURS = 1;
    private static final long SSE_TIMEOUT_MILLIS = 5 * 60 * 1000L;
    private static final Pattern DATE_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    private final SubscriptionRepository subscriptionRepository;
    private final TopicRepository topicRepository;
    private final AiChatService aiChatService;
    private final AppConfig appConfig;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final Set<Long> generatingTopicIds = ConcurrentHashMap.newKeySet();

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public TopicEventTrackingDTO getLatest(Long userId, Long subscriptionId) {
        Subscription subscription = requireTopicSubscription(userId, subscriptionId);
        return getLatestForTopic(subscription.getTopic());
    }

    @Transactional(readOnly = true)
    public TopicEventTrackingDTO getLatestByTopicId(Long topicId) {
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new EntityNotFoundException("Topic not found: " + topicId));
        return getLatestForTopic(topic);
    }

    private TopicEventTrackingDTO getLatestForTopic(Topic topic) {
        if (topic.getContent() == null || topic.getContent().trim().length() < MIN_TOPIC_LENGTH) {
            return TopicEventTrackingDTO.empty("Topic 内容较短，暂不支持事件追踪");
        }

        String rawResult = topic.getEventTrackingResult();
        if (rawResult == null || rawResult.isBlank()) {
            return TopicEventTrackingDTO.empty("尚未生成事件追踪");
        }

        try {
            TopicEventTrackingDTO.Result result = objectMapper.readValue(rawResult, TopicEventTrackingDTO.Result.class);
            return TopicEventTrackingDTO.success(result);
        } catch (Exception e) {
            log.warn("Invalid event tracking result for topic {}", topic.getId(), e);
            return TopicEventTrackingDTO.invalid("事件追踪结果解析失败");
        }
    }

    public SseEmitter generate(Long userId, Long subscriptionId) {
        Long topicId = transactionTemplate.execute(status -> requireTopicSubscription(userId, subscriptionId).getTopic().getId());
        if (topicId == null) {
            throw new EntityNotFoundException("Topic not found for subscription: " + subscriptionId);
        }
        return generateByTopicId(topicId);
    }

    public SseEmitter generateByTopicId(Long topicId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        Thread.ofVirtual().start(() -> doGenerate(topicId, emitter));
        return emitter;
    }

    private void doGenerate(Long topicId, SseEmitter emitter) {
        AtomicBoolean cleaned = new AtomicBoolean(false);
        AtomicReference<Disposable> disposableRef = new AtomicReference<>();
        AtomicReference<Long> generatingTopicIdRef = new AtomicReference<>();
        StringBuilder rawOutput = new StringBuilder();

        Runnable cleanup = () -> {
            if (!cleaned.compareAndSet(false, true)) {
                return;
            }
            Disposable disposable = disposableRef.get();
            if (disposable != null && !disposable.isDisposed()) {
                disposable.dispose();
            }
            Long generatingTopicId = generatingTopicIdRef.get();
            if (generatingTopicId != null) {
                generatingTopicIds.remove(generatingTopicId);
            }
        };

        emitter.onCompletion(cleanup);
        emitter.onTimeout(() -> {
            cleanup.run();
            emitter.complete();
        });
        emitter.onError(error -> cleanup.run());

        try {
            Topic topic = transactionTemplate.execute(status -> {
                Topic freshTopic = topicRepository.findById(topicId)
                        .orElseThrow(() -> new EntityNotFoundException("Topic not found: " + topicId));
                freshTopic.getContent();
                freshTopic.getVector();
                freshTopic.getEventTrackingResult();
                freshTopic.getUpdatedAt();
                return freshTopic;
            });
            if (topic == null) {
                completeWithDone(emitter, cleanup, false, "Topic 不存在");
                return;
            }
            String topicContent = topic.getContent() == null ? "" : topic.getContent().trim();
            if (topicContent.length() < MIN_TOPIC_LENGTH) {
                completeWithDone(emitter, cleanup, false, "Topic 内容较短，暂不支持事件追踪");
                return;
            }

            if (!generatingTopicIds.add(topicId)) {
                completeWithDone(emitter, cleanup, false, "该 Topic 正在生成事件追踪，请稍后再试");
                return;
            }
            generatingTopicIdRef.set(topicId);

            Topic lockedTopic = transactionTemplate.execute(status -> {
                Topic freshTopic = topicRepository.findById(topicId)
                        .orElseThrow(() -> new EntityNotFoundException("Topic not found: " + topicId));
                freshTopic.getContent();
                freshTopic.getVector();
                freshTopic.getEventTrackingResult();
                freshTopic.getUpdatedAt();
                return freshTopic;
            });

            if (lockedTopic.getEventTrackingResult() != null
                    && lockedTopic.getUpdatedAt() != null
                    && lockedTopic.getUpdatedAt().isAfter(LocalDateTime.now().minusHours(GENERATION_INTERVAL_HOURS))) {
                completeWithDone(emitter, cleanup, false, "距离上次生成不足 1 小时，请稍后再试");
                return;
            }

            List<TopicEventArticleInput> candidates = transactionTemplate.execute(status -> fetchCandidateArticles(lockedTopic));
            if (candidates == null || candidates.size() < MIN_CANDIDATE_COUNT) {
                completeWithDone(emitter, cleanup, false, "相关文章不足 5 篇，暂不生成事件追踪");
                return;
            }

            if (!sendEvent(emitter, "meta", Map.of("topic", topicContent, "articleCount", candidates.size()))) {
                cleanup.run();
                emitter.complete();
                return;
            }

            Prompt prompt = buildPrompt(topicContent, candidates);
            Disposable disposable = aiChatService.streamText(
                    prompt,
                    chunk -> {
                        if (cleaned.get()) {
                            return;
                        }
                        rawOutput.append(chunk);
                        if (!sendEvent(emitter, "chunk", Map.of("text", chunk))) {
                            cleanup.run();
                            emitter.complete();
                        }
                    },
                    () -> {
                        if (cleaned.get()) {
                            return;
                        }
                        try {
                            TopicEventTrackingDTO.Result result = validateAndEnrich(lockedTopic, candidates, rawOutput.toString());
                            transactionTemplate.executeWithoutResult(status -> saveResult(lockedTopic.getId(), result));
                            sendDone(emitter, true, null);
                        } catch (Exception e) {
                            log.warn("Failed to validate event tracking result for topic {}", lockedTopic.getId(), e);
                            sendDone(emitter, false, "模型输出格式无效，请重试");
                        } finally {
                            cleanup.run();
                            emitter.complete();
                        }
                    },
                    error -> {
                        if (cleaned.get()) {
                            return;
                        }
                        log.error("Event tracking generation failed for topic {}", lockedTopic.getId(), error);
                        sendEvent(emitter, "error", Map.of("message", "生成失败，请稍后重试"));
                        cleanup.run();
                        emitter.complete();
                    }
            );
            disposableRef.set(disposable);
        } catch (Exception e) {
            log.error("Failed to start event tracking generation", e);
            sendEvent(emitter, "error", Map.of("message", "生成失败，请稍后重试"));
            cleanup.run();
            emitter.complete();
        }
    }

    private void completeWithDone(SseEmitter emitter, Runnable cleanup, boolean saved, String message) {
        sendDone(emitter, saved, message);
        cleanup.run();
        emitter.complete();
    }

    private void sendDone(SseEmitter emitter, boolean saved, String message) {
        if (saved) {
            sendEvent(emitter, "done", Map.of("saved", true));
            return;
        }
        sendEvent(emitter, "done", Map.of("saved", false, "message", Objects.requireNonNullElse(message, "生成失败，请稍后重试")));
    }

    private boolean sendEvent(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
            return true;
        } catch (IOException | IllegalStateException e) {
            log.debug("SSE send failed: event={}", name, e);
            return false;
        }
    }

    private Subscription requireTopicSubscription(Long userId, Long subscriptionId) {
        Subscription subscription = subscriptionRepository.findByIdAndUserId(subscriptionId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Subscription not found: " + subscriptionId));
        if (subscription.getType() != SubscriptionType.TOPIC || subscription.getTopic() == null) {
            throw new IllegalArgumentException("Only topic subscriptions support event tracking");
        }
        return subscription;
    }

    private List<TopicEventArticleInput> fetchCandidateArticles(Topic topic) {
        if (topic.getVector() == null || topic.getVector().length == 0) {
            return List.of();
        }

        String sql = "SELECT a.id, a.title, a.description, a.cover_image, a.pub_date, ae.overview, "
                + "COALESCE(array_to_json(ae.key_information)::text, '[]') AS key_information_json "
                + "FROM articles a "
                + "JOIN article_extra ae ON ae.article_id = a.id "
                + "WHERE ae.vector IS NOT NULL "
                + "AND ae.status = 'SUCCESS' "
                + "AND a.pub_date IS NOT NULL "
                + "AND (ae.vector <=> CAST(:topicVector AS vector)) < :threshold "
                + "ORDER BY a.pub_date DESC, a.id DESC "
                + "LIMIT :limit";

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("topicVector", toPgVectorLiteral(topic.getVector()));
        query.setParameter("threshold", resolveThreshold(topic));
        query.setParameter("limit", MAX_CANDIDATE_COUNT);

        List<?> rows = query.getResultList();
        List<TopicEventArticleInput> result = new ArrayList<>(rows.size());
        for (Object row : rows) {
            if (row instanceof Object[] columns) {
                TopicEventArticleInput input = mapCandidate(columns);
                if (input != null) {
                    result.add(input);
                }
            }
        }
        return result;
    }

    private double resolveThreshold(Topic topic) {
        double threshold = appConfig.getTopicThreshold() == null ? 0.4 : appConfig.getTopicThreshold();
        if (topic.getContent() != null && topic.getContent().length() < 16) {
            threshold += 0.05;
        }
        return threshold;
    }

    private TopicEventArticleInput mapCandidate(Object[] columns) {
        Long id = columns[0] != null ? ((Number) columns[0]).longValue() : null;
        String title = columns[1] != null ? columns[1].toString() : null;
        LocalDateTime pubDate = toLocalDateTime(columns[4]);
        if (id == null || title == null || title.isBlank() || pubDate == null) {
            return null;
        }

        return new TopicEventArticleInput(
                id,
                title,
                columns[2] != null ? columns[2].toString() : null,
                columns[3] != null ? columns[3].toString() : null,
                pubDate,
                columns[5] != null ? columns[5].toString() : null,
                parseKeyInformation(columns[6])
        );
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        return null;
    }

    private List<String> parseKeyInformation(Object value) {
        if (value == null) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value.toString(), new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    private Prompt buildPrompt(String topicContent, List<TopicEventArticleInput> candidates) throws IOException {
        List<Map<String, Object>> articleInput = candidates.stream()
                .map(article -> Map.of(
                        "id", (Object) article.id(),
                        "date", article.pubDate().toLocalDate().toString(),
                        "title", article.title(),
                        "summary", truncate(firstNonBlank(article.overview(), article.description()), 300),
                        "key_information", article.keyInformation().stream().limit(3).toList()
                ))
                .toList();

        PromptTemplate template = new PromptTemplate(appConfig.getTopicEventTrackingPrompt());
        return template.create(Map.of(
                "topic", topicContent,
                "articlesJson", objectMapper.writeValueAsString(articleInput)
        ));
    }

    private TopicEventTrackingDTO.Result validateAndEnrich(
            Topic topic,
            List<TopicEventArticleInput> candidates,
            String rawOutput
    ) throws IOException {
        String json = extractJsonObject(rawOutput);
        JsonNode root = objectMapper.readTree(json);
        JsonNode nodesNode = root.get("nodes");

        List<ValidatedNode> validatedNodes = new ArrayList<>();
        Map<Long, TopicEventArticleInput> candidateMap = candidates.stream()
                .collect(java.util.stream.Collectors.toMap(TopicEventArticleInput::id, item -> item));

        if (nodesNode != null && nodesNode.isArray()) {
            int order = 0;
            for (JsonNode node : nodesNode) {
                ValidatedNode validated = validateNode(node, candidateMap, order++);
                if (validated != null) {
                    validatedNodes.add(validated);
                }
            }
        }

        List<TopicEventTrackingDTO.Node> nodes = validatedNodes.stream()
                .sorted((a, b) -> {
                    int dateCompare = b.date().compareTo(a.date());
                    if (dateCompare != 0) {
                        return dateCompare;
                    }
                    return Integer.compare(a.order(), b.order());
                })
                .limit(MAX_NODE_COUNT)
                .map(ValidatedNode::node)
                .toList();

        return new TopicEventTrackingDTO.Result(
                1,
                topic.getId(),
                topic.getContent(),
                LocalDateTime.now(),
                candidates.stream().map(TopicEventArticleInput::id).toList(),
                nodes
        );
    }

    private ValidatedNode validateNode(JsonNode node, Map<Long, TopicEventArticleInput> candidateMap, int order) {
        if (node == null || !node.isObject()) {
            return null;
        }
        String date = readText(node, "date");
        if (date == null || !DATE_PATTERN.matcher(date).matches() || !isValidDate(date)) {
            return null;
        }

        String progress = readText(node, "progress");
        if (progress == null || progress.isBlank()) {
            return null;
        }
        progress = truncate(progress.trim(), 25);

        JsonNode articlesNode = node.get("articles");
        if (articlesNode == null || !articlesNode.isArray()) {
            return null;
        }

        Set<Long> articleIds = new LinkedHashSet<>();
        for (JsonNode articleNode : articlesNode) {
            if (articleNode == null || !articleNode.isObject() || !articleNode.has("id")) {
                continue;
            }
            Long articleId = articleNode.get("id").asLong();
            if (candidateMap.containsKey(articleId)) {
                articleIds.add(articleId);
            }
            if (articleIds.size() >= MAX_NODE_ARTICLE_COUNT) {
                break;
            }
        }
        if (articleIds.isEmpty()) {
            return null;
        }

        List<TopicEventTrackingDTO.Article> articles = articleIds.stream()
                .map(candidateMap::get)
                .map(input -> new TopicEventTrackingDTO.Article(input.id(), input.title()))
                .toList();
        String coverImage = articleIds.stream()
                .map(candidateMap::get)
                .map(TopicEventArticleInput::coverImage)
                .filter(image -> image != null && !image.isBlank())
                .findFirst()
                .orElse(null);

        return new ValidatedNode(
                date,
                order,
                new TopicEventTrackingDTO.Node(date, progress, coverImage, articles)
        );
    }

    private boolean isValidDate(String date) {
        try {
            LocalDate.parse(date);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String readText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText("").trim();
    }

    private String extractJsonObject(String rawOutput) {
        String cleaned = rawOutput == null ? "" : rawOutput
                .replace("```json", "")
                .replace("```", "")
                .trim();
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("No JSON object found");
        }
        return cleaned.substring(start, end + 1);
    }

    private void saveResult(Long topicId, TopicEventTrackingDTO.Result result) {
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new EntityNotFoundException("Topic not found: " + topicId));
        try {
            topic.setEventTrackingResult(objectMapper.writeValueAsString(result));
            topicRepository.save(topic);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to save event tracking result", e);
        }
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return "";
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String toPgVectorLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        sb.append(']');
        return sb.toString();
    }

    private record TopicEventArticleInput(
            Long id,
            String title,
            String description,
            String coverImage,
            LocalDateTime pubDate,
            String overview,
            List<String> keyInformation
    ) {
    }

    private record ValidatedNode(
            String date,
            int order,
            TopicEventTrackingDTO.Node node
    ) {
    }
}
