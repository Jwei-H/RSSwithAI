package com.jingwei.rsswithai.application.dto;

import com.jingwei.rsswithai.domain.model.PromptVersion;

import java.time.LocalDateTime;

public record PromptVersionDTO(
        Long id,
        Long templateId,
        Integer version,
        String content,
        Boolean immutable,
        LocalDateTime createdAt
) {
    public static PromptVersionDTO from(PromptVersion entity) {
        return new PromptVersionDTO(
                entity.getId(),
                entity.getTemplate().getId(),
                entity.getVersion(),
                entity.getContent(),
                entity.getImmutable(),
                entity.getCreatedAt()
        );
    }
}