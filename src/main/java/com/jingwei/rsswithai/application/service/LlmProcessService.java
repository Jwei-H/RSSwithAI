package com.jingwei.rsswithai.application.service;

import com.jingwei.rsswithai.application.Event.ArticleProcessEvent;
import com.jingwei.rsswithai.application.Event.ConfigUpdateEvent;
import com.jingwei.rsswithai.config.AppConfig;
import com.jingwei.rsswithai.domain.model.AnalysisStatus;
import com.jingwei.rsswithai.domain.model.Article;
import com.jingwei.rsswithai.domain.model.ArticleExtra;
import com.jingwei.rsswithai.domain.repository.ArticleExtraRepository;
import com.jingwei.rsswithai.domain.repository.ArticleRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

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
    private final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicInteger currentLimit = new AtomicInteger();
    private ResizableSemaphore semaphore;
    private OpenAiChatModel chatModel;
    private OpenAiEmbeddingModel embeddingModel;

    /**
     * 初始化时创建AI客户端和信号量
     */
    @PostConstruct
    public void init() {
        int limit = appConfig.getConcurrentLimit();
        currentLimit.set(limit);
        semaphore = new ResizableSemaphore(limit);
        initializeOpenAiClient();
    }

    /**
     * 初始化OpenAI客户端
     */
    private void initializeOpenAiClient() {
        try {
            OpenAiApi chatOpenAiApi = OpenAiApi.builder()
                    .apiKey(appConfig.getLlmApiKey())
                    .baseUrl(appConfig.getLlmBaseUrl())
                    .build();

            OpenAiChatOptions options = buildChatOptions();
            this.chatModel = OpenAiChatModel.builder()
                    .openAiApi(chatOpenAiApi)
                    .defaultOptions(options)
                    .build();

            OpenAiApi embeddingOpenAiApi = OpenAiApi.builder()
                    .apiKey(resolveEmbeddingApiKey())
                    .baseUrl(resolveEmbeddingBaseUrl())
                    .build();

            this.embeddingModel = new OpenAiEmbeddingModel(
                    embeddingOpenAiApi,
                    MetadataMode.EMBED,
                    OpenAiEmbeddingOptions.builder()
                            .model(appConfig.getEmbeddingModel())
                            .dimensions(1024)
                            .build(),
                    RetryUtils.DEFAULT_RETRY_TEMPLATE);

            log.info("OpenAI client initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize OpenAI client", e);
        }
    }

    private String resolveEmbeddingBaseUrl() {
        String embeddingBaseUrl = appConfig.getEmbeddingBaseUrl();
        return (embeddingBaseUrl == null || embeddingBaseUrl.isBlank())
                ? appConfig.getLlmBaseUrl()
                : embeddingBaseUrl;
    }

    private String resolveEmbeddingApiKey() {
        String embeddingApiKey = appConfig.getEmbeddingApiKey();
        return (embeddingApiKey == null || embeddingApiKey.isBlank())
                ? appConfig.getLlmApiKey()
                : embeddingApiKey;
    }

    /**
     * 构建聊天选项
     */
    private OpenAiChatOptions buildChatOptions() {
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder()
                .model(appConfig.getLanguageModel());

        // 从配置中读取模型参数
        JsonNode config = appConfig.getLlmGenModelConfig();
        if (config != null) {
            if (config.has("temperature")) {
                builder.temperature(config.get("temperature").asDouble());
            }
            if (config.has("top_p")) {
                builder.topP(config.get("top_p").asDouble());
            }
            if (config.has("max_tokens")) {
                builder.maxTokens(config.get("max_tokens").asInt());
            }
            if (config.has("seed")) {
                builder.seed(config.get("seed").asInt());
            }
            if (config.has("top_k")) {
                Map<String, Object> extraBody = Map.of("topK", config.get("top_k").asInt());
                builder.extraBody(extraBody);
            }
        }
        return builder.build();
    }

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
     * 监听配置更新事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onConfigUpdateEvent(ConfigUpdateEvent event) {
        log.info("Received config update event, reinitializing OpenAI client");

        // 重建API客户端
        initializeOpenAiClient();

        // 调整信号量大小
        int newLimit = appConfig.getConcurrentLimit();
        int oldLimit = currentLimit.getAndSet(newLimit);
        int delta = newLimit - oldLimit;

        if (delta > 0) {
            semaphore.release(delta);
        } else if (delta < 0) {
            semaphore.reduce(-delta);
        }

        log.info("Concurrent limit updated to: {}", newLimit);
    }

    /**
     * 异步处理文章增强任务
     */
    private void processArticleAsync(Long articleId) {
        try {
            // 获取许可，控制并发
            semaphore.acquire();
            // log.debug("Acquired semaphore permit for article: {}", articleId);

            // 检查是否已处理过
            if (articleExtraRepository.existsByArticleId(articleId)) {
                log.info("Article {} already processed, skipping", articleId);
                semaphore.release();
                return;
            }

            Article article = articleRepository.findById(articleId).orElse(null);
            if (article == null) {
                log.warn("Article not found: {}", articleId);
                semaphore.release();
                return;
            }

            log.info("Processing article: {} - {}", articleId, article.getTitle());

            // 生成内容
            ArticleExtra articleExtra = generateContent(article);

            // 生成向量
            if (articleExtra.getOverview() != null && !articleExtra.getOverview().isBlank()) {
                String vectorText = articleExtra.getOverview() + "\n" +
                        String.join("\n", articleExtra.getKeyInformation());
                articleExtra.setVector(generateVector(vectorText));
            } else {
                articleExtra.setVector(generateVector(article.getTitle()));
            }

            // 保存结果
            articleExtraRepository.save(articleExtra);
            log.info("Article {} processing completed successfully", articleId);

        } catch (Exception e) {
            log.error("Error processing article {}", articleId, e);
            saveFailedResult(articleId, e.getMessage());
        } finally {
            semaphore.release();
            // log.debug("Released semaphore permit for article: {}", articleId);
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
            ChatResponse response = chatModel.call(prompt);

            String content = response.getResult().getOutput().getText();
            // 解析JSON响应
            JsonNode jsonResponse = objectMapper
                    .readTree(Objects.requireNonNull(content).replace("```json", "").replace("```", ""));

            String overview = jsonResponse.has("overview") ? jsonResponse.get("overview").asString() : "";
            List<String> keyInfoList = List.of();
            List<String> tagsList = List.of();
            if (jsonResponse.has("key_info") && jsonResponse.get("key_info").isArray()) {
                keyInfoList = objectMapper.convertValue(
                        jsonResponse.get("key_info"),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
            }

            if (jsonResponse.has("tags") && jsonResponse.get("tags").isArray()) {
                // Jackson 3 API usage: convertValue and TypeFactory
                tagsList = objectMapper.convertValue(
                        jsonResponse.get("tags"),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
            }

            resultBuilder.overview(overview)
                    .keyInformation(keyInfoList)
                    .tags(tagsList)
                    .status(AnalysisStatus.SUCCESS);

        } catch (Exception e) {
            log.error("Error generating content for article {}", article.getId(), e);
            resultBuilder.errorMessage(e.getMessage());
        }

        return resultBuilder.createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
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
     * 生成向量表示
     */
    public float[] generateVector(String text) {
        try {
            EmbeddingResponse embeddingResponse = embeddingModel.embedForResponse(List.of(text));
            return embeddingResponse.getResult().getOutput();
        } catch (Exception e) {
            log.error("Error generating vector for text", e);
        }
        return null;
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
            semaphore.acquire();
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
                articleExtra.setVector(generateVector(vectorText));
            } else {
                articleExtra.setVector(generateVector(article.getTitle()));
            }


            articleExtraRepository.save(articleExtra);
            log.info("Article {} regeneration completed successfully", articleId);

        } catch (Exception e) {
            log.error("Error regenerating article {}", articleId, e);
            saveFailedResult(articleId, e.getMessage());
        } finally {
            semaphore.release();
        }
    }

    static final class ResizableSemaphore extends Semaphore {
        ResizableSemaphore(int permits) {
            super(permits);
        }

        void reduce(int reduction) {
            super.reducePermits(reduction);
        }
    }
}