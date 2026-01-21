package com.jingwei.rsswithai.application.service;

import com.jingwei.rsswithai.application.dto.CreateSubscriptionRequest;
import com.jingwei.rsswithai.application.dto.CreateTopicRequest;
import com.jingwei.rsswithai.application.dto.ArticleFeedDTO;
import com.jingwei.rsswithai.application.dto.SubscriptionDTO;
import com.jingwei.rsswithai.application.dto.TopicDTO;
import com.jingwei.rsswithai.application.dto.UserRssSourceDTO;
import com.jingwei.rsswithai.config.AppConfig;
import com.jingwei.rsswithai.domain.model.RssSource;
import com.jingwei.rsswithai.domain.model.SourceCategory;
import com.jingwei.rsswithai.domain.model.SourceStatus;
import com.jingwei.rsswithai.domain.model.Subscription;
import com.jingwei.rsswithai.domain.model.SubscriptionType;
import com.jingwei.rsswithai.domain.model.Topic;
import com.jingwei.rsswithai.domain.repository.RssSourceRepository;
import com.jingwei.rsswithai.domain.repository.SubscriptionRepository;
import com.jingwei.rsswithai.domain.repository.TopicRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService {

    private static final int DEFAULT_FEED_SIZE = 20;
    private static final int MAX_FEED_SIZE = 100;

    private final RssSourceRepository rssSourceRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final TopicRepository topicRepository;
    private final LlmProcessService llmProcessService;
    private final AppConfig appConfig;

    @PersistenceContext
    private EntityManager entityManager;

    public Page<UserRssSourceDTO> listRssSources(Long userId, SourceCategory category, Pageable pageable) {
        Map<Long, Long> subscriptionMap = subscriptionRepository.findByUserIdAndTypeWithSource(userId, SubscriptionType.RSS).stream()
                .filter(sub -> sub.getSource() != null)
                .collect(Collectors.toMap(sub -> sub.getSource().getId(), Subscription::getId, (a, b) -> a, LinkedHashMap::new));

        Page<RssSource> page = category != null
            ? rssSourceRepository.findByStatusAndCategory(SourceStatus.ENABLED, category, pageable)
            : rssSourceRepository.findByStatus(SourceStatus.ENABLED, pageable);

        return page.map(source -> UserRssSourceDTO.from(source, subscriptionMap.get(source.getId())));
    }

    @Transactional
    public TopicDTO createTopic(CreateTopicRequest request) {
        String content = request.content() == null ? "" : request.content().trim();
        if (content.isBlank()) {
            throw new IllegalArgumentException("Topic content cannot be blank");
        }
        if (content.length() > 30) {
            throw new IllegalArgumentException("Topic content length must be within 20 characters");
        }

        return topicRepository.findByContent(content)
                .map(TopicDTO::from)
                .orElseGet(() -> createNewTopic(content));
    }

    @Transactional
    public SubscriptionDTO createSubscription(Long userId, CreateSubscriptionRequest request) {
        if (request.type() == null) {
            throw new IllegalArgumentException("Subscription type is required");
        }
        if (request.targetId() == null || request.targetId() <= 0) {
            throw new IllegalArgumentException("targetId must be positive");
        }

        return switch (request.type()) {
            case RSS -> subscribeRss(userId, request.targetId());
            case TOPIC -> subscribeTopic(userId, request.targetId());
        };
    }

    @Transactional
    public void deleteSubscription(Long userId, Long subscriptionId) {
        Subscription subscription = subscriptionRepository.findByIdAndUserId(subscriptionId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Subscription not found: " + subscriptionId));
        subscriptionRepository.delete(subscription);
    }

    @Transactional(readOnly = true)
    public List<SubscriptionDTO> listSubscriptions(Long userId) {
        return subscriptionRepository.findByUserIdWithDetails(userId).stream()
                .map(SubscriptionDTO::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ArticleFeedDTO> getFeed(Long userId, Long subscriptionId, String cursor, Integer size) {
        FeedCursor feedCursor = parseCursor(cursor);
        int pageSize = (size == null || size <= 0) ? DEFAULT_FEED_SIZE : Math.min(size, MAX_FEED_SIZE);

        List<Long> sourceIds = new ArrayList<>();
        List<float[]> topicVectors = new ArrayList<>();

        if (subscriptionId != null) {
            Subscription subscription = subscriptionRepository.findByIdAndUserId(subscriptionId, userId)
                    .orElseThrow(() -> new EntityNotFoundException("Subscription not found: " + subscriptionId));
            if (subscription.getType() == SubscriptionType.RSS) {
                if (subscription.getSource() != null) {
                    sourceIds.add(subscription.getSource().getId());
                }
            } else if (subscription.getType() == SubscriptionType.TOPIC) {
                if (subscription.getTopic() != null && subscription.getTopic().getVector() != null) {
                    topicVectors.add(subscription.getTopic().getVector());
                }
            }
        } else {
            for (Subscription subscription : subscriptionRepository.findByUserIdWithDetails(userId)) {
                if (subscription.getType() == SubscriptionType.RSS && subscription.getSource() != null) {
                    sourceIds.add(subscription.getSource().getId());
                } else if (subscription.getType() == SubscriptionType.TOPIC && subscription.getTopic() != null && subscription.getTopic().getVector() != null) {
                    topicVectors.add(subscription.getTopic().getVector());
                }
            }
        }

        if (sourceIds.isEmpty() && topicVectors.isEmpty()) {
            return List.of();
        }

        Set<Long> dedupSourceIds = new LinkedHashSet<>(sourceIds);
        return executeHybridFeed(new ArrayList<>(dedupSourceIds), topicVectors, feedCursor.cursorTime(), feedCursor.cursorId(), pageSize);
    }

    private TopicDTO createNewTopic(String content) {
        float[] vector = llmProcessService.generateVector(content);
        if (vector == null) {
            throw new IllegalStateException("Failed to generate topic vector");
        }

        Topic topic = Topic.builder()
                .content(content)
                .vector(vector)
                .build();
        try {
            Topic saved = topicRepository.save(topic);
            return TopicDTO.from(saved);
        } catch (DataIntegrityViolationException e) {
            log.warn("Topic concurrent creation detected for content: {}", content);
            return topicRepository.findByContent(content)
                    .map(TopicDTO::from)
                    .orElseThrow(() -> new IllegalStateException("Failed to persist topic"));
        }
    }

    private SubscriptionDTO subscribeRss(Long userId, Long sourceId) {
        RssSource source = rssSourceRepository.findById(sourceId)
                .orElseThrow(() -> new EntityNotFoundException("RSS source not found: " + sourceId));

        return subscriptionRepository.findByUserIdAndTypeAndSource_Id(userId, SubscriptionType.RSS, sourceId)
                .map(SubscriptionDTO::from)
                .orElseGet(() -> {
                    ensureSubscriptionLimit(userId);
                    Subscription subscription = Subscription.builder()
                            .userId(userId)
                            .type(SubscriptionType.RSS)
                            .source(source)
                            .build();
                    return SubscriptionDTO.from(subscriptionRepository.save(subscription));
                });
    }

    private SubscriptionDTO subscribeTopic(Long userId, Long topicId) {
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new EntityNotFoundException("Topic not found: " + topicId));

        return subscriptionRepository.findByUserIdAndTypeAndTopic_Id(userId, SubscriptionType.TOPIC, topicId)
                .map(SubscriptionDTO::from)
                .orElseGet(() -> {
                    ensureSubscriptionLimit(userId);
                    Subscription subscription = Subscription.builder()
                            .userId(userId)
                            .type(SubscriptionType.TOPIC)
                            .topic(topic)
                            .build();
                    return SubscriptionDTO.from(subscriptionRepository.save(subscription));
                });
    }

    private void ensureSubscriptionLimit(Long userId) {
        Integer limit = appConfig.getSubscriptionLimit();
        if (limit != null && limit > 0) {
            long count = subscriptionRepository.countByUserId(userId);
            if (count >= limit) {
                throw new IllegalArgumentException("Subscription limit reached: " + limit);
            }
        }
    }

    private List<ArticleFeedDTO> executeHybridFeed(List<Long> sourceIds,
                                                   List<float[]> topicVectors,
                                                   LocalDateTime cursorTime,
                                                   long cursorId,
                                                   int size) {
        List<String> branches = new ArrayList<>();
        if (!sourceIds.isEmpty()) {
            branches.add("SELECT a.id, a.source_id, a.source_name, a.title, a.cover_image, a.pub_date " +
                    "FROM articles a WHERE a.source_id IN (:sourceIds) " +
                    "AND (a.pub_date < :cursorTime OR (a.pub_date = :cursorTime AND a.id < :cursorId))");
        }
        if (!topicVectors.isEmpty()) {
            String vectorConditions = IntStream.range(0, topicVectors.size())
                    .mapToObj(i -> "(ae.vector <=> CAST(:vector" + i + " AS vector)) < :threshold")
                    .collect(Collectors.joining(" OR "));
            branches.add("SELECT a.id, a.source_id, a.source_name, a.title, a.cover_image, a.pub_date " +
                    "FROM articles a JOIN article_extra ae ON a.id = ae.article_id WHERE (" + vectorConditions + ") " +
                    "AND (a.pub_date < :cursorTime OR (a.pub_date = :cursorTime AND a.id < :cursorId))");
        }

        if (branches.isEmpty()) {
            return List.of();
        }

        String sql = "SELECT * FROM (" + String.join(" UNION ", branches) + ") AS feed " +
            "ORDER BY pub_date DESC, id DESC";

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("cursorTime", Timestamp.valueOf(cursorTime));
        query.setParameter("cursorId", cursorId);
        query.setMaxResults(size);
        if (!sourceIds.isEmpty()) {
            query.setParameter("sourceIds", sourceIds);
        }
        if (!topicVectors.isEmpty()) {
            double threshold = appConfig.getFeedSimilarityThreshold() != null
                    ? appConfig.getFeedSimilarityThreshold()
                    : 0.3D;
            query.setParameter("threshold", threshold);
            for (int i = 0; i < topicVectors.size(); i++) {
                query.setParameter("vector" + i, toPgVectorLiteral(topicVectors.get(i)));
            }
        }

        List<?> rows = query.getResultList();
        List<ArticleFeedDTO> result = new ArrayList<>(rows.size());
        for (Object row : rows) {
            if (row instanceof Object[] columns) {
                result.add(mapRow(columns));
            }
        }
        return result;
    }

    private FeedCursor parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return new FeedCursor(LocalDateTime.now(), Long.MAX_VALUE);
        }
        String[] parts = cursor.split(",", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid cursor format");
        }
        try {
            LocalDateTime cursorTime = LocalDateTime.parse(parts[0]);
            long cursorId = Long.parseLong(parts[1]);
            return new FeedCursor(cursorTime, cursorId);
        } catch (DateTimeParseException | NumberFormatException e) {
            throw new IllegalArgumentException("Invalid cursor format", e);
        }
    }

    private ArticleFeedDTO mapRow(Object[] row) {
        Long id = row[0] != null ? ((Number) row[0]).longValue() : null;
        Long sourceId = row[1] != null ? ((Number) row[1]).longValue() : null;
        String sourceName = row[2] != null ? row[2].toString() : null;
        String title = row[3] != null ? row[3].toString() : null;
        String coverImage = row[4] != null ? row[4].toString() : null;
        LocalDateTime pubDate = null;
        Object dateObj = row[5];
        if (dateObj instanceof Timestamp timestamp) {
            pubDate = timestamp.toLocalDateTime();
        } else if (dateObj instanceof LocalDateTime time) {
            pubDate = time;
        }
        return ArticleFeedDTO.of(id, sourceId, sourceName, title, coverImage, pubDate);
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

    private record FeedCursor(LocalDateTime cursorTime, long cursorId) {
    }
}