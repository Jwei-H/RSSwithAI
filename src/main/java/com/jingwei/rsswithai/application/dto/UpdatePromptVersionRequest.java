package com.jingwei.rsswithai.application.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdatePromptVersionRequest(
    @NotBlank
    String content
) {}