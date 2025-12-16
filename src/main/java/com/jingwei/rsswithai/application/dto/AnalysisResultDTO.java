package com.jingwei.rsswithai.application.dto;

import com.jingwei.rsswithai.domain.model.AnalysisStatus;
import com.jingwei.rsswithai.domain.model.AnalysisResult;
import java.time.LocalDateTime;

public record AnalysisResultDTO(
    Long id,
    Long experimentId,
    Long articleId,
    String articleTitle,
    String modelConfigJson,
    String promptContent,
    String analysisResult,
    Integer inputTokens,
    Integer outputTokens,
    Long executionTimeMs,
    AnalysisStatus status,
    String errorMessage,
    LocalDateTime createdAt
) {
    public static AnalysisResultDTO fromEntity(AnalysisResult result) {
        return new AnalysisResultDTO(
            result.getId(),
            result.getExperiment() != null ? result.getExperiment().getId() : null,
            result.getArticle() != null ? result.getArticle().getId() : null,
            result.getArticle() != null ? result.getArticle().getTitle() : null,
            result.getModelConfigJson(),
            result.getPromptContent(),
            result.getAnalysisResult(),
            result.getInputTokens(),
            result.getOutputTokens(),
            result.getExecutionTimeMs(),
            result.getStatus(),
            result.getErrorMessage(),
            result.getCreatedAt()
        );
    }
}