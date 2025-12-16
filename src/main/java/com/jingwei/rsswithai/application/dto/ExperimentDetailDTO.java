package com.jingwei.rsswithai.application.dto;

import com.jingwei.rsswithai.domain.model.AnalysisResult;
import com.jingwei.rsswithai.domain.model.Experiment;
import com.jingwei.rsswithai.domain.model.ExperimentStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

public record ExperimentDetailDTO(
    Long id,
    String name,
    String description,
    ExperimentStatus status,
    List<Long> articleIds,
    Long modelConfigId,
    String modelConfigName,
    Long promptTemplateId,
    String promptTemplateName,
    Integer promptVersion,
    LocalDateTime createdAt,
    List<AnalysisResultDTO> results
) {
    public static ExperimentDetailDTO fromEntity(Experiment experiment, List<AnalysisResult> results) {
        return new ExperimentDetailDTO(
            experiment.getId(),
            experiment.getName(),
            experiment.getDescription(),
            experiment.getStatus(),
            experiment.getArticleIds(),
            experiment.getModelConfig() != null ? experiment.getModelConfig().getId() : null,
            experiment.getModelConfig() != null ? experiment.getModelConfig().getName() : null,
            experiment.getPromptTemplate() != null ? experiment.getPromptTemplate().getId() : null,
            experiment.getPromptTemplate() != null ? experiment.getPromptTemplate().getName() : null,
            experiment.getPromptVersion(),
            experiment.getCreatedAt(),
            results != null ? results.stream()
                    .map(AnalysisResultDTO::fromEntity)
                    .collect(Collectors.toList()) : null
        );
    }
}