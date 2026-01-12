package com.jingwei.rsswithai.application.dto;

import java.time.LocalDateTime;

public record FeedArticleDTO(
        Long id,
        Long sourceId,
        String sourceName,
        String title,
        String coverImage,
        LocalDateTime pubDate
) {
    public static FeedArticleDTO of(Long id,
                                    Long sourceId,
                                    String sourceName,
                                    String title,
                                    String coverImage,
                                    LocalDateTime pubDate) {
        return new FeedArticleDTO(id, sourceId, sourceName, title, coverImage, pubDate);
    }
}
