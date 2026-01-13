package com.jingwei.rsswithai.application.dto;

import java.time.LocalDateTime;

public record ArticleFeedDTO(
        Long id,
        Long sourceId,
        String sourceName,
        String title,
        String coverImage,
        LocalDateTime pubDate
) {
    public static ArticleFeedDTO of(Long id,
                                    Long sourceId,
                                    String sourceName,
                                    String title,
                                    String coverImage,
                                    LocalDateTime pubDate) {
        return new ArticleFeedDTO(id, sourceId, sourceName, title, coverImage, pubDate);
    }
}