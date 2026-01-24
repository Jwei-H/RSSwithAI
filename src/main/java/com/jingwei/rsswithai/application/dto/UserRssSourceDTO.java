package com.jingwei.rsswithai.application.dto;

import com.jingwei.rsswithai.domain.model.RssSource;
import com.jingwei.rsswithai.domain.model.SourceCategory;

public record UserRssSourceDTO(
        Long id,
        String name,
        String link,
        SourceCategory category,
        boolean isSubscribed,
        Long subscriptionId
) {
    public static UserRssSourceDTO from(RssSource source, Long subscriptionId) {
        return new UserRssSourceDTO(
                source.getId(),
                source.getName(),
                source.getLink(),
                source.getCategory(),
                subscriptionId != null,
                subscriptionId
        );
    }
}