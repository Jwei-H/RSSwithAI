package com.jingwei.rsswithai.application.dto;

import com.jingwei.rsswithai.domain.model.Experiment;
import com.jingwei.rsswithai.domain.model.ExperimentStatus;
import com.jingwei.rsswithai.domain.model.PromptVersion;

import java.time.LocalDateTime;
import java.util.List;

public record ExperimentDetailDTO(
        Long id,
        String name,
        String description,
        ExperimentStatus status,
        List<Long> articleIds,
        ModelConfigDTO modelConfig,
        PromptTemplateDTO promptTemplate,
        Integer promptVersion,
        LocalDateTime createdAt
) {
    public static ExperimentDetailDTO fromEntity(Experiment experiment, PromptVersion promptVersion) {
        return new ExperimentDetailDTO(
                experiment.getId(),
                experiment.getName(),
                experiment.getDescription(),
                experiment.getStatus(),
                experiment.getArticleIds(),
                ModelConfigDTO.from(experiment.getModelConfig()),
                PromptTemplateDTO.from(experiment.getPromptTemplate(), promptVersion),
                experiment.getPromptVersion(),
                experiment.getCreatedAt()
        );
    }
}