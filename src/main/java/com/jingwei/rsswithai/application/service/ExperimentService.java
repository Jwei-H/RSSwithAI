package com.jingwei.rsswithai.application.service;

import com.jingwei.rsswithai.application.dto.*;
import com.jingwei.rsswithai.config.AppConfig;
import com.jingwei.rsswithai.domain.model.*;
import com.jingwei.rsswithai.domain.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExperimentService {

    private final ExperimentRepository experimentRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final ArticleRepository articleRepository;
    private final ModelConfigRepository modelConfigRepository;
    private final PromptTemplateRepository promptTemplateRepository;
    private final PromptVersionRepository promptVersionRepository;
    private final AppConfig appConfig;
    private final ObjectMapper objectMapper;

    @Transactional
    public ExperimentDTO createExperiment(CreateExperimentRequest request) {
        ModelConfig modelConfig = modelConfigRepository.findById(request.getModelConfigId())
                .orElseThrow(() -> new IllegalArgumentException("ModelConfig not found"));

        PromptTemplate promptTemplate = promptTemplateRepository.findById(request.getPromptTemplateId())
                .orElseThrow(() -> new IllegalArgumentException("PromptTemplate not found"));

        PromptVersion promptVersion = promptVersionRepository.findByTemplateIdAndVersion(request.getPromptTemplateId(), request.getPromptVersion())
                .orElseThrow(() -> new IllegalArgumentException("PromptVersion not found"));

        if (!promptVersion.getImmutable()) {
            throw new IllegalArgumentException("Prompt version must be immutable (locked) to be used in an experiment");
        }

        Experiment experiment = Experiment.builder()
                .name(request.getName())
                .description(request.getDescription())
                .status(ExperimentStatus.RUNNING)
                .articleIds(request.getArticleIds())
                .modelConfig(modelConfig)
                .promptTemplate(promptTemplate)
                .promptVersion(request.getPromptVersion())
                .build();

        Experiment savedExperiment = experimentRepository.save(experiment);

        Thread.ofVirtual().start(() -> executeExperimentAsync(savedExperiment.getId(), modelConfig));

        return ExperimentDTO.fromEntity(savedExperiment);
    }

    private void executeExperimentAsync(Long experimentId, ModelConfig modelConfig) {
        log.info("Starting experiment {}", experimentId);
        Experiment experiment = experimentRepository.findById(experimentId).orElse(null);
        if (experiment == null) return;

        try {
            List<Long> articleIds = experiment.getArticleIds();
            PromptVersion promptVersion = promptVersionRepository.findByTemplateIdAndVersion(experiment.getPromptTemplate().getId(), experiment.getPromptVersion())
                    .orElseThrow();

            OpenAiChatModel chatModel = createChatModel(modelConfig);
            log.info("Chat model created for experiment {}", experimentId);
            org.springframework.ai.chat.prompt.PromptTemplate aiPromptTemplate = new org.springframework.ai.chat.prompt.PromptTemplate(promptVersion.getContent());

            for (Long articleId : articleIds) {
                processArticle(experiment, articleId, modelConfig, aiPromptTemplate, chatModel);
            }

            experiment.setStatus(ExperimentStatus.COMPLETED);
            experimentRepository.save(experiment);

        } catch (Exception e) {
            log.error("Error executing experiment {}", experimentId, e);
            experiment.setStatus(ExperimentStatus.FAILED);
            experimentRepository.save(experiment);
        }
    }

    private void processArticle(Experiment experiment, Long articleId, ModelConfig modelConfig,
                                org.springframework.ai.chat.prompt.PromptTemplate aiPromptTemplate, OpenAiChatModel chatModel) {
        Article article = articleRepository.findById(articleId).orElse(null);
        if (article == null) return;
        log.info("Processing article {} for experiment {}", articleId, experiment.getId());
        AnalysisResult.AnalysisResultBuilder resultBuilder = AnalysisResult.builder()
                .experiment(experiment)
                .article(article)
                .status(AnalysisStatus.FAILED)
                .modelConfigJson(objectMapper.writeValueAsString(ModelConfig.builder()
                        .id(modelConfig.getId())
                        .name(modelConfig.getName())
                        .description(modelConfig.getDescription())
                        .modelId(modelConfig.getModelId())
                        .temperature(modelConfig.getTemperature())
                        .topP(modelConfig.getTopP())
                        .topK(modelConfig.getTopK())
                        .maxTokens(modelConfig.getMaxTokens())
                        .seed(modelConfig.getSeed())
                        .build()));

        long startTime = System.currentTimeMillis();

        try {
            Map<String, Object> model = Map.of(
                    "title", article.getTitle(),
                    "author", article.getAuthor() != null ? article.getAuthor() : "",
                    "content", article.getContent() != null ? article.getContent() : ""
            );

            Prompt prompt = aiPromptTemplate.create(model);
            String promptContent = prompt.getContents();
            log.debug("Generated prompt for article {}: {}", articleId, promptContent);
            resultBuilder.promptContent(promptContent);

            ChatResponse response = chatModel.call(prompt);

            long endTime = System.currentTimeMillis();

            String content = response.getResult().getOutput().getText();
            Integer inputTokens = response.getMetadata().getUsage().getPromptTokens();
            Integer outputTokens = response.getMetadata().getUsage().getCompletionTokens();

            resultBuilder.analysisResult(content)
                    .inputTokens(inputTokens)
                    .outputTokens(outputTokens)
                    .executionTimeMs(endTime - startTime)
                    .status(AnalysisStatus.SUCCESS);

        } catch (Exception e) {
            log.error("Error analyzing article {}", articleId, e);
            resultBuilder.errorMessage(e.getMessage())
                    .executionTimeMs(System.currentTimeMillis() - startTime);
        }
        log.info("Saving analysis result for article {} in experiment {}", articleId, experiment.getId());
        analysisResultRepository.save(resultBuilder.build());
    }

    private OpenAiChatModel createChatModel(ModelConfig config) {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .apiKey(appConfig.getLlmApiKey())
                .baseUrl(appConfig.getLlmBaseUrl())
                .build();
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(config.getModelId())
                .temperature(config.getTemperature())
                .topP(config.getTopP())
                .maxTokens(config.getMaxTokens())
//                .extraBody(Map.of("topK", config.getTopK()))
                .build();
        return OpenAiChatModel.builder().openAiApi(openAiApi).defaultOptions(options).build();
    }

    public Page<ExperimentDTO> getExperiments(ExperimentStatus status, Pageable pageable) {
        Page<Experiment> page;
        if (status != null) {
            page = experimentRepository.findByStatus(status, pageable);
        } else {
            page = experimentRepository.findAll(pageable);
        }
        return page.map(ExperimentDTO::fromEntity);
    }

    public ExperimentDetailDTO getExperimentDetail(Long id) {
        Experiment experiment = experimentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Experiment not found"));

        List<AnalysisResult> results = analysisResultRepository.findByExperimentId(id);

        return ExperimentDetailDTO.fromEntity(experiment, results);
    }

    public void deleteExperiment(Long id) {
        Experiment experiment = experimentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Experiment not found"));

        if (experiment.getStatus() == ExperimentStatus.RUNNING) {
            throw new IllegalStateException("Cannot delete running experiment");
        }

        experimentRepository.delete(experiment);
    }

    public List<AnalysisResultWithExperimentDTO> getAnalysisResultsByArticle(Long articleId) {
        List<AnalysisResult> results = analysisResultRepository.findByArticleId(articleId);
        return results.stream().map(AnalysisResultWithExperimentDTO::fromEntity).collect(Collectors.toList());
    }

    public AnalysisResultDetailDTO getAnalysisResultDetail(Long id) {
        AnalysisResult result = analysisResultRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("AnalysisResult not found"));
        return AnalysisResultDetailDTO.fromEntity(result);
    }

}