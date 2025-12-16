package com.jingwei.rsswithai.application.dto;

import com.jingwei.rsswithai.domain.model.ExperimentStatus;
import com.jingwei.rsswithai.domain.model.Experiment;
import java.time.LocalDateTime;
import java.util.List;

public record ExperimentDTO(
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
    LocalDateTime createdAt
) {
    public static ExperimentDTO fromEntity(Experiment experiment) {
        return new ExperimentDTO(
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
            experiment.getCreatedAt()
        );
    }
}