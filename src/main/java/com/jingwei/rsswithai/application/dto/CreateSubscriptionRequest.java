package com.jingwei.rsswithai.application.dto;

import com.jingwei.rsswithai.domain.model.SubscriptionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateSubscriptionRequest(
        @NotNull(message = "type is required")
        SubscriptionType type,
        @NotNull(message = "targetId is required")
        @Positive(message = "targetId must be positive")
        Long targetId
) {
}
