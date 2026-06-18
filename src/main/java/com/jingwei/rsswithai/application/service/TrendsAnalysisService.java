package com.jingwei.rsswithai.application.service;

import com.jingwei.rsswithai.config.AppConfig;
import com.jingwei.rsswithai.domain.model.Article;
import com.jingwei.rsswithai.domain.model.RssSource;
import com.jingwei.rsswithai.domain.model.TrendsData;
import com.jingwei.rsswithai.domain.repository.ArticleExtraRepository;
import com.jingwei.rsswithai.domain.repository.ArticleRepository;
import com.jingwei.rsswithai.domain.repository.RssSourceRepository;
import com.jingwei.rsswithai.domain.repository.TrendsDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class TrendsAnalysisService {

    private static final int HOT_EVENTS_MAP_CHUNK_SIZE = 30;

    private final ArticleRepository articleRepository;
    private final ArticleExtraRepository articleExtraRepository;
    private final TrendsDataRepository trendsDataRepository;
    private final RssSourceRepository rssSourceRepository;
    private final AppConfig appConfig;
    private final ObjectMapper objectMapper;
    private final AiChatService aiChatService;
    private final SubscriptionService subscriptionService;

    // --- Word Cloud Logic ---

    @Transactional
    public void generateWordCloudForSource(Long sourceId) {
        log.info("Starting Word Cloud generation for source: {}", sourceId);
        try {
            // 1. Data Preparation
            List<Article> articles = fetchArticlesForWordCloud(sourceId);
            if (articles.isEmpty()) {
                log.info("No articles found for source {}, skipping word cloud.", sourceId);
                return;
            }

            // 2. Initial Statistics (Java)
            Map<String, Integer> rawTagCounts = new HashMap<>();
            for (Article article : articles) {
                articleExtraRepository.findByArticleId(article.getId())
                        .ifPresent(extra -> {
                            if (extra.getTags() != null) {
                                for (String tag : extra.getTags()) {
                                    rawTagCounts.merge(tag, 1, Integer::sum);
                                }
                            }
                        });
            }

            if (rawTagCounts.isEmpty()) {
                log.info("No tags found for source {}, skipping word cloud.", sourceId);
                return;
            }

            // Take Top 100
            List<Map.Entry<String, Integer>> topTags = rawTagCounts.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(100)
                    .toList();

            // 3. LLM Cleaning
            String tagsInput = topTags.stream()
                    .map(e -> e.getKey() + ":" + e.getValue())
                    .collect(Collectors.joining("\n"));

            Map<String, List<String>> synonymMap = fetchSynonymsFromLlm(tagsInput);

            // 4. Merge and Save
            List<Map<String, Object>> finalWordCloud = mergeTags(rawTagCounts, synonymMap);

            saveTrendsData(sourceId, "WORD_CLOUD", finalWordCloud);
            log.info("Word Cloud generated for source {}", sourceId);

        } catch (Exception e) {
            log.error("Error generating word cloud for source {}", sourceId, e);
        }
    }

    private List<Article> fetchArticlesForWordCloud(Long sourceId) {
        Pageable limit = PageRequest.of(0, 50);
        List<Article> candidates = articleRepository.findBySourceIdOrderByPubDateDesc(sourceId, limit).getContent();

        if (candidates.isEmpty())
            return Collections.emptyList();

        LocalDateTime threeDaysAgo = LocalDateTime.now().minusDays(3);
        Article latest = candidates.getFirst();
        if (latest.getPubDate() == null || latest.getPubDate().isBefore(threeDaysAgo)) {
            return Collections.emptyList();
        }

        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        List<Article> recent = candidates.stream()
                .filter(a -> a.getPubDate() != null && a.getPubDate().isAfter(sevenDaysAgo))
                .collect(Collectors.toList());

        if (recent.size() < 20) {
            return candidates.subList(0, Math.min(candidates.size(), 20));
        }
        return recent;
    }

    private Map<String, List<String>> fetchSynonymsFromLlm(String tagsInput) {
        if (appConfig.getTrendsWordCloudPrompt() == null)
            return Collections.emptyMap();

        try {
            PromptTemplate promptTemplate = new PromptTemplate(appConfig.getTrendsWordCloudPrompt());
            Prompt prompt = promptTemplate.create(Map.of("tags", tagsInput));

            String content = aiChatService.callText(prompt);
            return parseJsonToMap(content);
        } catch (Exception e) {
            log.error("LLM error during synonym fetching", e);
            return Collections.emptyMap();
        }
    }

    private List<Map<String, Object>> mergeTags(Map<String, Integer> rawCounts, Map<String, List<String>> synonymMap) {
        Map<String, Integer> mergedCounts = new HashMap<>(rawCounts);

        Set<String> processedVariants = new HashSet<>();

        synonymMap.forEach((standard, variants) -> {
            int total = 0;
            for (String variant : variants) {
                if (processedVariants.contains(variant))
                    continue;
                total += mergedCounts.getOrDefault(variant, 0);
                if (!variant.equals(standard)) {
                    mergedCounts.remove(variant);
                    processedVariants.add(variant);
                }
            }
            mergedCounts.put(standard, total > 0 ? total : mergedCounts.getOrDefault(standard, 0));
        });

        return mergedCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(50)
                .map(e -> Map.of("text", (Object) e.getKey(), "value", e.getValue()))
                .collect(Collectors.toList());
    }

    // --- Hot Events Logic ---

    @Transactional
    public void generateHotEvents() {
        log.info("Starting Hot Events generation");
        try {
            // 1. Map Phase (Per Source)
            Map<String, List<String>> sourceEvents = new LinkedHashMap<>();

            for (RssSource source : rssSourceRepository.findAllEnabled()) {
                List<Article> articles = fetchArticlesForHotEvents(source.getId());
                if (articles.isEmpty())
                    continue;
                String sourceName = resolveSourceName(source);

                List<List<Article>> chunks = splitArticlesIntoChunks(articles, HOT_EVENTS_MAP_CHUNK_SIZE);
                for (int i = 0; i < chunks.size(); i++) {
                    List<Article> chunk = chunks.get(i);
                    String mapSourceName = chunks.size() > 1
                            ? sourceName + "-part" + (i + 1)
                            : sourceName;

                    String articlesOverview = buildArticlesDetailsForMap(chunk);
                    String eventsJson = fetchEventsFromLlm(articlesOverview, mapSourceName);
                    log.info("Source {} [{}]: Extracted events JSON: {}", source.getId(), mapSourceName, eventsJson);
                    List<String> rankedEvents = parseRankedEvents(eventsJson);
                    if (!rankedEvents.isEmpty()) {
                        sourceEvents.put(mapSourceName, rankedEvents);
                    }
                }
            }

            if (sourceEvents.isEmpty()) {
                log.info("No events extracted from any source.");
                return;
            }

            // 2. Reduce Phase (Global)
            String combinedEvents = objectMapper.writeValueAsString(sourceEvents.entrySet().stream()
                    .map(e -> Map.of("source", (Object) e.getKey(), "events", e.getValue()))
                    .toList());

            List<Map<String, Object>> reducedEvents = reduceGlobalEventsWithRetry(combinedEvents);
            if (reducedEvents.isEmpty()) {
                log.warn("Reduce phase produced no events after retries, skipping save.");
                return;
            }

            List<Map<String, Object>> eventsWithTopics = attachTopicIds(reducedEvents);

            // 3. Save
            if (!eventsWithTopics.isEmpty()) {
                saveTrendsData(0L, "HOT_EVENTS", eventsWithTopics);
                log.info("Hot Events generated successfully");
            } else {
                log.warn("All events filtered out after attaching topic IDs, skipping save.");
            }

        } catch (Exception e) {
            log.error("Error generating hot events", e);
        }
    }

    private List<Map<String, Object>> attachTopicIds(List<Map<String, Object>> events) {
        return events.stream()
                .map(event -> {
                    String eventText = event.get("event") instanceof String text ? text.trim() : "";
                    if (eventText.isBlank()) {
                        return event;
                    }
                    Map<String, Object> enriched = new LinkedHashMap<>(event);
                    enriched.put("event", eventText);
                    try {
                        Long topicId = subscriptionService.getOrCreateTopic(eventText).getId();
                        enriched.put("topicId", topicId);
                    } catch (Exception e) {
                        log.warn("Failed to create topic for hot event '{}', will resolve on read: {}",
                                eventText, e.getMessage());
                    }
                    return enriched;
                })
                .toList();
    }

    private List<Article> fetchArticlesForHotEvents(Long sourceId) {
        return articleRepository.findBySourceIdAndPubDateSinceOrderByPubDateDesc(
                sourceId,
                LocalDateTime.now().minusHours(40));
    }

    private List<List<Article>> splitArticlesIntoChunks(List<Article> articles, int chunkSize) {
        if (articles.isEmpty()) {
            return Collections.emptyList();
        }
        List<List<Article>> chunks = new ArrayList<>();
        for (int i = 0; i < articles.size(); i += chunkSize) {
            int end = Math.min(i + chunkSize, articles.size());
            chunks.add(articles.subList(i, end));
        }
        return chunks;
    }

    private String fetchEventsFromLlm(String articlesDetails, String sourceName) {
        if (appConfig.getTrendsHotEventsMapPrompt() == null)
            return "[]";
        try {
            PromptTemplate template = new PromptTemplate(appConfig.getTrendsHotEventsMapPrompt());
            Prompt prompt = template.create(Map.of("articles", articlesDetails, "sourcename", sourceName));
            return cleanJsonBlock(aiChatService.callText(prompt));
        } catch (Exception e) {
            log.error("LLM Map error", e);
            return "[]";
        }
    }

    private String fetchGlobalEventsFromLlm(String allEvents) {
        if (appConfig.getTrendsHotEventsReducePrompt() == null)
            return "[]";
        try {
            PromptTemplate template = new PromptTemplate(appConfig.getTrendsHotEventsReducePrompt());
            Prompt prompt = template.create(Map.of("events", allEvents));
            return cleanJsonBlock(aiChatService.callText(prompt));
        } catch (Exception e) {
            log.error("LLM Reduce error", e);
            return "[]";
        }
    }

    private List<Map<String, Object>> reduceGlobalEventsWithRetry(String combinedEvents) {
        int[] retryDelaysSec = {10, 20, 40};
        int maxRetries = retryDelaysSec.length;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            String globalEvents = fetchGlobalEventsFromLlm(combinedEvents);
            log.info("Reduce attempt {}: raw response: {}", attempt + 1, globalEvents);

            if ("[]".equals(globalEvents)) {
                if (attempt < maxRetries) {
                    log.warn("Reduce returned empty, retrying ({}/{}) after {}s...",
                            attempt + 1, maxRetries + 1, retryDelaysSec[attempt]);
                    sleepSeconds(retryDelaysSec[attempt]);
                    continue;
                }
                log.warn("Reduce returned empty after all retries");
                return Collections.emptyList();
            }

            try {
                List<Map<String, Object>> events = objectMapper.readValue(globalEvents,
                        new TypeReference<List<Map<String, Object>>>() {});
                if (!events.isEmpty()) {
                    log.info("Reduce succeeded with {} events", events.size());
                    return events;
                }
                if (attempt < maxRetries) {
                    log.warn("Reduce returned valid but empty list, retrying ({}/{}) after {}s...",
                            attempt + 1, maxRetries + 1, retryDelaysSec[attempt]);
                    sleepSeconds(retryDelaysSec[attempt]);
                    continue;
                }
            } catch (Exception e) {
                log.error("Failed to parse reduce JSON (attempt {}/{}): {}",
                        attempt + 1, maxRetries + 1, e.getMessage());
                if (attempt < maxRetries) {
                    log.warn("Retrying after {}s...", retryDelaysSec[attempt]);
                    sleepSeconds(retryDelaysSec[attempt]);
                    continue;
                }
            }
        }

        return Collections.emptyList();
    }

    private void sleepSeconds(int seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted during retry sleep");
        }
    }

    private String resolveSourceName(RssSource source) {
        if (source.getName() != null && !source.getName().isBlank()) {
            return source.getName();
        }
        return "source-" + source.getId();
    }

    private String buildArticlesDetailsForMap(List<Article> articles) {
        StringBuilder builder = new StringBuilder();
        for (Article article : articles) {
            String overview = articleExtraRepository.findByArticleId(article.getId())
                    .map(ArticleExtraRepository.ArticleExtraNoVectorView::getOverview)
                    .orElse(null);

            if (overview != null && !overview.isBlank()) {
                builder.append("- 标题：")
                        .append(Optional.ofNullable(article.getTitle()).orElse(""))
                        .append("\n  概览：")
                        .append(overview)
                        .append("\n");
                continue;
            }

            // No overview: skip long articles, use content for short ones
            long wordCount = article.getWordCount() != null ? article.getWordCount() : 0;
            if (wordCount > 100) {
                continue;
            }
            String content = article.getContent();
            if (content != null && !content.isBlank()) {
                builder.append("- 标题：")
                        .append(Optional.ofNullable(article.getTitle()).orElse(""))
                        .append("\n  概览：")
                        .append(content)
                        .append("\n");
            }
        }
        return builder.toString().trim();
    }

    private List<String> parseRankedEvents(String eventsJson) {
        if (eventsJson == null || eventsJson.isBlank() || eventsJson.equals("[]")) {
            return Collections.emptyList();
        }
        try {
            List<Object> events = objectMapper.readValue(
                    cleanJsonBlock(eventsJson),
                    new TypeReference<List<Object>>() {
                    });

            List<String> normalized = new ArrayList<>();
            for (Object eventObj : events) {
                if (eventObj instanceof String text && !text.isBlank()) {
                    normalized.add(text.trim());
                    continue;
                }
                if (eventObj instanceof Map<?, ?> eventMap) {
                    Object eventText = eventMap.get("event");
                    if (eventText instanceof String text && !text.isBlank()) {
                        normalized.add(text.trim());
                        continue;
                    }
                    Object descriptionText = eventMap.get("description");
                    if (descriptionText instanceof String text && !text.isBlank()) {
                        normalized.add(text.trim());
                    }
                }
            }
            return normalized;
        } catch (Exception e) {
            log.error("Failed to parse ranked events JSON", e);
            return Collections.emptyList();
        }
    }

    private void saveTrendsData(Long sourceId, String type, Object dataObj) throws Exception {
        String jsonStr = objectMapper.writeValueAsString(dataObj);

        TrendsData data = TrendsData.builder()
                .sourceId(sourceId)
                .type(type)
                .data(jsonStr)
                .build();
        trendsDataRepository.save(data);
    }

    private Map<String, List<String>> parseJsonToMap(String json) {
        try {
            return objectMapper.readValue(cleanJsonBlock(json), new TypeReference<Map<String, List<String>>>() {
            });
        } catch (Exception e) {
            log.error("JSON parse error", e);
            return Collections.emptyMap();
        }
    }

    private String cleanJsonBlock(String text) {
        if (text == null)
            return "{}";
        String cleaned = text.trim();

        // Remove markdown code block markers
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        cleaned = cleaned.trim();

        // Try to extract the outermost JSON array/object to strip surrounding text
        int jsonStart = -1;
        for (int i = 0; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            if (c == '[' || c == '{') {
                jsonStart = i;
                break;
            }
        }
        if (jsonStart < 0) {
            return cleaned;
        }

        char openChar = cleaned.charAt(jsonStart);
        char closeChar = (openChar == '[') ? ']' : '}';
        int depth = 0;
        int jsonEnd = -1;
        for (int i = jsonStart; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            if (c == openChar) {
                depth++;
            } else if (c == closeChar) {
                depth--;
                if (depth == 0) {
                    jsonEnd = i + 1;
                    break;
                }
            }
        }

        if (jsonEnd > jsonStart) {
            return cleaned.substring(jsonStart, jsonEnd).trim();
        }
        return cleaned;
    }
}