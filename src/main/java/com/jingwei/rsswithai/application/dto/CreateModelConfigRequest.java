package com.jingwei.rsswithai.application.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateModelConfigRequest(
    @NotBlank
    String name,
    String description,
    @NotBlank
    String modelId,
    Double temperature,
    Double topP,
    Integer topK,
    Integer maxTokens,
    Integer seed
) {}