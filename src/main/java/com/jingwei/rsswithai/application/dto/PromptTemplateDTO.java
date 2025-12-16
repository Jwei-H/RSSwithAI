package com.jingwei.rsswithai.application.dto;

import com.jingwei.rsswithai.domain.model.PromptTemplate;
import com.jingwei.rsswithai.domain.model.PromptVersion;

import java.time.LocalDateTime;

public record PromptTemplateDTO(
        Long id,
        String name,
        String description,
        Integer latestVersion,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        PromptVersionDTO latestVersionDetail
) {
    public static PromptTemplateDTO from(PromptTemplate template, PromptVersion latestVersion) {
        return new PromptTemplateDTO(
                template.getId(),
                template.getName(),
                template.getDescription(),
                template.getLatestVersion(),
                template.getCreatedAt(),
                template.getUpdatedAt(),
                latestVersion != null ? PromptVersionDTO.from(latestVersion) : null
        );
    }
}