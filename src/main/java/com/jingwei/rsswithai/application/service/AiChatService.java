package com.jingwei.rsswithai.application.service;

import com.jingwei.rsswithai.application.Event.ConfigUpdateEvent;
import com.jingwei.rsswithai.config.AppConfig;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
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
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.client.RestClient;
import reactor.core.Disposable;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@Service
@Slf4j
@RequiredArgsConstructor
@DependsOn("settingsService")
public class AiChatService {

    private final AppConfig appConfig;

    private volatile OpenAiChatModel chatModel;
    private volatile OpenAiEmbeddingModel embeddingModel;
    private final AtomicInteger currentLimit = new AtomicInteger();
    private ResizableSemaphore semaphore;

    @PostConstruct
    public void init() {
        initializeConcurrentLimiter();
        initializeChatClient();
        initializeEmbeddingClient();
    }

    @EventListener
    public void onConfigUpdateEvent(ConfigUpdateEvent event) {
        initializeChatClient();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onConfigUpdateCommitted(ConfigUpdateEvent event) {
        resizeConcurrentLimiter();
        initializeEmbeddingClient();
    }

    public String callText(Prompt prompt) {
        acquirePermit();
        try {
            OpenAiChatModel model = requireChatModel();
            ChatResponse response = model.call(prompt);
            if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
                return "";
            }
            return response.getResult().getOutput().getText();
        } finally {
            semaphore.release();
        }
    }

    public Disposable streamText(
            Prompt prompt,
            Consumer<String> onChunk,
            Runnable onComplete,
            Consumer<Throwable> onError
    ) {
        acquirePermit();
        AtomicBoolean released = new AtomicBoolean(false);
        try {
            OpenAiChatModel model = requireChatModel();
            return model.stream(prompt)
                    .doFinally(signalType -> releasePermitOnce(released))
                    .subscribe(
                            response -> {
                                String text = extractText(response);
                                if (text != null && !text.isEmpty()) {
                                    onChunk.accept(text);
                                }
                            },
                            onError,
                            onComplete
                    );
        } catch (RuntimeException e) {
            releasePermitOnce(released);
            throw e;
        }
    }

    /**
     * Synchronous streaming: blocks the current thread and invokes onChunk for each token,
     * then onComplete when done, or onError on failure.
     */
    public void streamTextSync(
            Prompt prompt,
            Consumer<String> onChunk,
            Runnable onComplete,
            Consumer<Throwable> onError
    ) {
        OpenAiChatModel model = requireChatModel();
        try {
            model.stream(prompt)
                    .toStream()
                    .forEach(response -> {
                        String text = extractText(response);
                        if (text != null && !text.isEmpty()) {
                            onChunk.accept(text);
                        }
                    });
            onComplete.run();
        } catch (Exception e) {
            onError.accept(e);
        }
    }

    public float[] generateVector(String text) {
        try {
            EmbeddingResponse embeddingResponse = requireEmbeddingModel().embedForResponse(List.of(text));
            return embeddingResponse.getResult().getOutput();
        } catch (Exception e) {
            log.error("Error generating vector for text", e);
        }
        return null;
    }

    private OpenAiChatModel requireChatModel() {
        OpenAiChatModel model = chatModel;
        if (model == null) {
            throw new IllegalStateException("AI chat model is not initialized");
        }
        return model;
    }

    private OpenAiEmbeddingModel requireEmbeddingModel() {
        OpenAiEmbeddingModel model = embeddingModel;
        if (model == null) {
            throw new IllegalStateException("AI embedding model is not initialized");
        }
        return model;
    }

    private void initializeConcurrentLimiter() {
        int limit = appConfig.getConcurrentLimit();
        currentLimit.set(limit);
        semaphore = new ResizableSemaphore(limit);
    }

    private void resizeConcurrentLimiter() {
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

    private void acquirePermit() {
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for AI chat permit", e);
        }
    }

    private void releasePermitOnce(AtomicBoolean released) {
        if (released.compareAndSet(false, true)) {
            semaphore.release();
        }
    }

    private void initializeChatClient() {
        try {
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(Duration.ofSeconds(10));
            requestFactory.setReadTimeout(Duration.ofSeconds(180));

            RestClient.Builder restClientBuilder = RestClient.builder()
                    .requestFactory(requestFactory);

            OpenAiApi openAiApi = OpenAiApi.builder()
                    .apiKey(appConfig.getLlmApiKey())
                    .baseUrl(appConfig.getLlmBaseUrl())
                    .restClientBuilder(restClientBuilder)
                    .build();

            this.chatModel = OpenAiChatModel.builder()
                    .openAiApi(openAiApi)
                    .defaultOptions(buildChatOptions())
                    .build();
            log.info("AI chat client initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize AI chat client", e);
            this.chatModel = null;
        }
    }

    private void initializeEmbeddingClient() {
        try {
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(Duration.ofSeconds(10));
            requestFactory.setReadTimeout(Duration.ofSeconds(60));

            RestClient.Builder restClientBuilder = RestClient.builder()
                    .requestFactory(requestFactory);

            OpenAiApi embeddingOpenAiApi = OpenAiApi.builder()
                    .apiKey(resolveEmbeddingApiKey())
                    .baseUrl(resolveEmbeddingBaseUrl())
                    .restClientBuilder(restClientBuilder)
                    .build();

            this.embeddingModel = new OpenAiEmbeddingModel(
                    embeddingOpenAiApi,
                    MetadataMode.EMBED,
                    OpenAiEmbeddingOptions.builder()
                            .model(appConfig.getEmbeddingModel())
                            .dimensions(1024)
                            .build(),
                    RetryUtils.DEFAULT_RETRY_TEMPLATE);

            log.info("AI embedding client initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize AI embedding client", e);
            this.embeddingModel = null;
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

    private OpenAiChatOptions buildChatOptions() {
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder()
                .model(appConfig.getLanguageModel());

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
                builder.extraBody(Map.of("topK", config.get("top_k").asInt()));
            }
        }
        return builder.build();
    }

    private String extractText(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return null;
        }
        return response.getResult().getOutput().getText();
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
