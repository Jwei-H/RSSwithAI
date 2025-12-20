package com.jingwei.rsswithai.application.dto;

import com.jingwei.rsswithai.domain.model.AnalysisResult;
import com.jingwei.rsswithai.domain.model.AnalysisStatus;

import java.time.LocalDateTime;

public record AnalysisResultDTO(
        Long id,
        Long experimentId,
        String experimentName,
        Long articleId,
        String articleTitle,
        AnalysisStatus status,
        String errorMessage,
        LocalDateTime createdAt
) {
    public static AnalysisResultDTO fromEntity(AnalysisResult result) {
        return new AnalysisResultDTO(
                result.getId(),
                result.getExperiment() != null ? result.getExperiment().getId() : null,
                result.getExperiment() != null ? result.getExperiment().getName() : null,
                result.getArticle() != null ? result.getArticle().getId() : null,
                result.getArticle() != null ? result.getArticle().getTitle() : null,
                result.getStatus(),
                result.getErrorMessage(),
                result.getCreatedAt()
        );
    }
}