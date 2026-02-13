package com.jingwei.rsswithai.application.service;

import com.jingwei.rsswithai.application.Event.ConfigUpdateEvent;
import com.jingwei.rsswithai.config.AppConfig;
import com.jingwei.rsswithai.domain.model.Article;
import com.jingwei.rsswithai.domain.model.RssSource;
import com.jingwei.rsswithai.domain.model.TrendsData;
import com.jingwei.rsswithai.domain.repository.ArticleExtraRepository;
import com.jingwei.rsswithai.domain.repository.ArticleRepository;
import com.jingwei.rsswithai.domain.repository.RssSourceRepository;
import com.jingwei.rsswithai.domain.repository.TrendsDataRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.context.event.EventListener;
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

    private final ArticleRepository articleRepository;
    private final ArticleExtraRepository articleExtraRepository;
    private final TrendsDataRepository trendsDataRepository;
    private final RssSourceRepository rssSourceRepository;
    private final AppConfig appConfig;
    private final ObjectMapper objectMapper;

    private OpenAiChatModel chatModel;

    @PostConstruct
    public void init() {
        initializeOpenAiClient();
    }

    private void initializeOpenAiClient() {
        try {
            OpenAiApi openAiApi = OpenAiApi.builder()
                    .apiKey(appConfig.getLlmApiKey())
                    .baseUrl(appConfig.getLlmBaseUrl())
                    .build();

            OpenAiChatOptions options = OpenAiChatOptions.builder()
                    .model(appConfig.getLanguageModel())
                    .temperature(0.3)
                    .build();

            this.chatModel = OpenAiChatModel.builder()
                    .openAiApi(openAiApi)
                    .defaultOptions(options)
                    .build();
        } catch (Exception e) {
            log.error("Failed to initialize OpenAI client for TrendsAnalysisService", e);
        }
    }

    /**
     * Re-initialize capability similar to LlmProcessService
     */
    @EventListener
    public void onConfigUpdateEvent(ConfigUpdateEvent event) {
        initializeOpenAiClient();
    }

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

            ChatResponse response = chatModel.call(prompt);
            String content = response.getResult().getOutput().getText();
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

                String articlesOverview = buildArticlesDetailsForMap(articles);

                String sourceName = resolveSourceName(source);
                String eventsJson = fetchEventsFromLlm(articlesOverview, sourceName);
                log.info("Source {}: Extracted events JSON: {}", source.getId(), eventsJson);
                List<String> rankedEvents = parseRankedEvents(eventsJson);
                if (!rankedEvents.isEmpty()) {
                    sourceEvents.put(sourceName, rankedEvents);
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

            String globalEvents = fetchGlobalEventsFromLlm(combinedEvents);

            log.info("Global reduced events JSON: {}", globalEvents);

            // 3. Save
            saveTrendsData(0L, "HOT_EVENTS",
                    objectMapper.readValue(globalEvents, new TypeReference<List<Map<String, Object>>>() {
                    }));
            log.info("Hot Events generated successfully");

        } catch (Exception e) {
            log.error("Error generating hot events", e);
        }
    }

    private List<Article> fetchArticlesForHotEvents(Long sourceId) {
        Pageable limit = PageRequest.of(0, 40);
        List<Article> candidates = articleRepository.findBySourceIdOrderByPubDateDesc(sourceId, limit).getContent();

        if (candidates.isEmpty())
            return Collections.emptyList();

        return candidates.stream()
                .filter(a -> a.getPubDate() != null && a.getPubDate().isAfter(LocalDateTime.now().minusDays(5)))
                .collect(Collectors.toList());
    }

    private String fetchEventsFromLlm(String articlesDetails, String sourceName) {
        if (appConfig.getTrendsHotEventsMapPrompt() == null)
            return "[]";
        try {
            PromptTemplate template = new PromptTemplate(appConfig.getTrendsHotEventsMapPrompt());
            Prompt prompt = template.create(Map.of("articles", articlesDetails, "sourcename", sourceName));
            ChatResponse response = chatModel.call(prompt);
            return cleanJsonBlock(response.getResult().getOutput().getText());
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
            ChatResponse response = chatModel.call(prompt);
            return cleanJsonBlock(response.getResult().getOutput().getText());
        } catch (Exception e) {
            log.error("LLM Reduce error", e);
            return "[]";
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

            builder.append("- 标题：")
                    .append(Optional.ofNullable(article.getTitle()).orElse(""))
                    .append("\n  概览：")
                    .append((overview == null || overview.isBlank()) ? "无" : overview)
                    .append("\n");
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
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        return cleaned.trim();
    }
}