package com.jingwei.rsswithai.application.service;

import com.jingwei.rsswithai.application.Event.ConfigUpdateEvent;
import com.jingwei.rsswithai.config.AppConfig;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import tools.jackson.databind.JsonNode;

import java.util.Map;
import java.util.function.Consumer;

@Service
@Slf4j
@RequiredArgsConstructor
@DependsOn("settingsService")
public class AiChatService {

    private final AppConfig appConfig;

    private volatile OpenAiChatModel chatModel;

    @PostConstruct
    public void init() {
        initializeOpenAiClient();
    }

    @EventListener
    public void onConfigUpdateEvent(ConfigUpdateEvent event) {
        initializeOpenAiClient();
    }

    public String callText(Prompt prompt) {
        OpenAiChatModel model = requireChatModel();
        ChatResponse response = model.call(prompt);
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        return response.getResult().getOutput().getText();
    }

    public Disposable streamText(
            Prompt prompt,
            Consumer<String> onChunk,
            Runnable onComplete,
            Consumer<Throwable> onError
    ) {
        OpenAiChatModel model = requireChatModel();
        return model.stream(prompt)
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

    private OpenAiChatModel requireChatModel() {
        OpenAiChatModel model = chatModel;
        if (model == null) {
            throw new IllegalStateException("AI chat model is not initialized");
        }
        return model;
    }

    private void initializeOpenAiClient() {
        try {
            OpenAiApi openAiApi = OpenAiApi.builder()
                    .apiKey(appConfig.getLlmApiKey())
                    .baseUrl(appConfig.getLlmBaseUrl())
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
}
