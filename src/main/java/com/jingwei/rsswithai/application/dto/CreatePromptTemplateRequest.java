package com.jingwei.rsswithai.application.dto;

import jakarta.validation.constraints.NotBlank;

public record CreatePromptTemplateRequest(
    @NotBlank
    String name,
    String description
) {}