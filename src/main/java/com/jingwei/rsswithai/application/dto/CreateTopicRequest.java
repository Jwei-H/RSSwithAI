package com.jingwei.rsswithai.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTopicRequest(
        @NotBlank(message = "content cannot be blank")
        @Size(max = 50, message = "content must be within 50 characters")
        String content
) {
}