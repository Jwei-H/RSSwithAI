package com.jingwei.rsswithai.application.dto;

public record UpdateModelConfigRequest(
    String name,
    String description,
    String modelId,
    Double temperature,
    Double topP,
    Integer topK,
    Integer maxTokens,
    Integer seed
) {}