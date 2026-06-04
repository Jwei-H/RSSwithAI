package com.jingwei.rsswithai.application.service;

import com.jingwei.rsswithai.application.Event.ArticleProcessEvent;
import com.jingwei.rsswithai.config.AppConfig;
import com.jingwei.rsswithai.domain.model.AnalysisStatus;
import com.jingwei.rsswithai.domain.model.Article;
import com.jingwei.rsswithai.domain.model.ArticleExtra;
import com.jingwei.rsswithai.domain.repository.ArticleExtraRepository;
import com.jingwei.rsswithai.domain.repository.ArticleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * LLM处理服务
 * 负责对文章进行AI增强处理，包括概览生成、关键信息提取、标签生成和向量化
 */
@Service
@Slf4j
@RequiredArgsConstructor
@DependsOn("settingsService")
public class LlmProcessService {

    private final ArticleRepository articleRepository;
    private final ArticleExtraRepository articleExtraRepository;
    private final AppConfig appConfig;
    private final ObjectMapper objectMapper;
    private final AiChatService aiChatService;
    private final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * 监听文章处理事件
     */
    @EventListener
    public void onArticleProcessEvent(ArticleProcessEvent event) {
        Long articleId = event.getArticleId();
        log.info("Received article process event for article: {}", articleId);
        executorService.submit(() -> processArticleAsync(articleId));
    }

    /**
     * 异步处理文章增强任务
     */
    private void processArticleAsync(Long articleId) {
        try {
            // 检查是否已处理过
            if (articleExtraRepository.existsByArticleId(articleId)) {
                log.info("Article {} already processed, skipping", articleId);
                return;
            }

            Article article = articleRepository.findById(articleId).orElse(null);
            if (article == null) {
                log.warn("Article not found: {}", articleId);
                return;
            }

            log.info("Processing article: {} - {}", articleId, article.getTitle());

            // 生成内容
            ArticleExtra articleExtra = generateContent(article);

            // 生成向量
            if (articleExtra.getOverview() != null && !articleExtra.getOverview().isBlank()) {
                String vectorText = articleExtra.getOverview() + "\n" +
                        String.join("\n", articleExtra.getKeyInformation());
                articleExtra.setVector(aiChatService.generateVector(vectorText));
            } else {
                articleExtra.setVector(aiChatService.generateVector(article.getTitle()));
            }

            // 保存结果
            articleExtraRepository.save(articleExtra);
            log.info("Article {} processing completed successfully", articleId);

        } catch (Exception e) {
            log.error("Error processing article {}", articleId, e);
            saveFailedResult(articleId, e.getMessage());
        }
    }

    /**
     * 生成文章内容（概览、关键信息、标签）
     */
    private ArticleExtra generateContent(Article article) {
        ArticleExtra.ArticleExtraBuilder resultBuilder = ArticleExtra.builder()
                .articleId(article.getId())
                .status(AnalysisStatus.FAILED);

        try {
            Prompt prompt = buildPrompt(article);
            String content = aiChatService.callText(prompt);
            // 解析JSON响应
            JsonNode jsonResponse = objectMapper
                    .readTree(Objects.requireNonNull(content).replace("```json", "").replace("```", ""));

            String overview = jsonResponse.has("overview") ? jsonResponse.get("overview").asString() : "";
            List<String> keyInfoList = List.of();
            List<String> tagsList = List.of();
            List<Map<String, String>> tocList = List.of();
            if (jsonResponse.has("key_info") && jsonResponse.get("key_info").isArray()) {
                keyInfoList = objectMapper.convertValue(
                        jsonResponse.get("key_info"),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
                keyInfoList = keyInfoList.stream().map(s -> s.replace("**", "")).toList();
            }

            if (jsonResponse.has("tags") && jsonResponse.get("tags").isArray()) {
                // Jackson 3 API usage: convertValue and TypeFactory
                tagsList = objectMapper.convertValue(
                        jsonResponse.get("tags"),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
            }

            if (jsonResponse.has("toc") && jsonResponse.get("toc").isArray()) {
                tocList = parseTocList(jsonResponse.get("toc"));
            }

            resultBuilder.overview(overview)
                    .keyInformation(keyInfoList)
                    .tags(tagsList)
                    .toc(objectMapper.writeValueAsString(tocList))
                    .status(AnalysisStatus.SUCCESS);

        } catch (Exception e) {
            log.error("Error generating content for article {}", article.getId(), e);
            resultBuilder.errorMessage(e.getMessage());
        }

        return resultBuilder.createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private List<Map<String, String>> parseTocList(JsonNode tocNode) {
        List<Map<String, String>> result = new ArrayList<>();
        for (JsonNode node : tocNode) {
            if (!node.isObject()) {
                continue;
            }
            String title = node.has("title") ? node.get("title").asText("").trim() : "";
            String anchor = node.has("anchor") ? node.get("anchor").asText("").trim() : "";
            if (title.isBlank() || anchor.isBlank()) {
                continue;
            }
            result.add(Map.of("title", title, "anchor", anchor));
        }
        return result;
    }

    /**
     * 构建提示词
     */
    private Prompt buildPrompt(Article article) {
        PromptTemplate promptTemplate = PromptTemplate.builder()
                .template(appConfig.getLlmGenPrompt())
                .build();
        // 替换模板中的占位符
        return new Prompt(promptTemplate.createMessage(Map.of(
                "title", article.getTitle(),
                "source", article.getSourceName(),
                "content", article.getContent())));
    }

    /**
     * 保存失败结果
     */
    private void saveFailedResult(Long articleId, String errorMessage) {
        try {
            ArticleExtra articleExtra = ArticleExtra.builder()
                    .articleId(articleId)
                    .status(AnalysisStatus.FAILED)
                    .errorMessage(errorMessage)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            articleExtraRepository.save(articleExtra);
            log.error("Saved failed result for article {}: {}", articleId, errorMessage);
        } catch (Exception e) {
            log.error("Error saving failed result for article {}", articleId, e);
        }
    }

    public void regenerateArticleExtra(Long articleId) {
        try {
            articleExtraRepository.deleteByArticleId(articleId);
            log.info("Cleaned up existing article extra for article: {}", articleId);

            Article article = articleRepository.findById(articleId).orElse(null);
            if (article == null) {
                log.warn("Article not found: {}", articleId);
                return;
            }
            ArticleExtra articleExtra = generateContent(article);

            // 生成向量
            if (articleExtra.getOverview() != null && !articleExtra.getOverview().isBlank()) {
                String vectorText = articleExtra.getOverview() + "\n" +
                        String.join("\n", articleExtra.getKeyInformation());
                articleExtra.setVector(aiChatService.generateVector(vectorText));
            } else {
                articleExtra.setVector(aiChatService.generateVector(article.getTitle()));
            }


            articleExtraRepository.save(articleExtra);
            log.info("Article {} regeneration completed successfully", articleId);

        } catch (Exception e) {
            log.error("Error regenerating article {}", articleId, e);
            saveFailedResult(articleId, e.getMessage());
        }
    }
}
