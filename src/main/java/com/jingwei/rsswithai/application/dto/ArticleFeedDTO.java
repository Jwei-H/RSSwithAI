package com.jingwei.rsswithai.application.dto;

import com.jingwei.rsswithai.domain.model.Article;

import java.time.LocalDateTime;

public record ArticleFeedDTO(
        Long id,
        Long sourceId,
        String sourceName,
        String title,
        String coverImage,
        LocalDateTime pubDate,
        Long wordCount
) {
    public static ArticleFeedDTO of(Long id,
                                    Long sourceId,
                                    String sourceName,
                                    String title,
                                    String coverImage,
                                    LocalDateTime pubDate,
                                    Long wordCount) {
        return new ArticleFeedDTO(id, sourceId, sourceName, title, coverImage, pubDate,wordCount);
    }
    public static ArticleFeedDTO from(Article article){
        return new ArticleFeedDTO(
                article.getId(),
                article.getSource() != null ? article.getSource().getId() : null,
                article.getSourceName(),
                article.getTitle(),
                article.getCoverImage(),
                article.getPubDate(),
                article.getWordCount()
        );
    }
}