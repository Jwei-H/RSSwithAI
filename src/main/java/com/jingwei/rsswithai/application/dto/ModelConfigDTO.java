package com.jingwei.rsswithai.application.dto;

import com.jingwei.rsswithai.domain.model.ModelConfig;

import java.time.LocalDateTime;

public record ModelConfigDTO(
        Long id,
        String name,
        String description,
        String modelId,
        Double temperature,
        Double topP,
        Integer topK,
        Integer maxTokens,
        Integer seed,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ModelConfigDTO from(ModelConfig entity) {
        return new ModelConfigDTO(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getModelId(),
                entity.getTemperature(),
                entity.getTopP(),
                entity.getTopK(),
                entity.getMaxTokens(),
                entity.getSeed(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}