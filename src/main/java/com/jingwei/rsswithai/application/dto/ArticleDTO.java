package com.jingwei.rsswithai.application.dto;

import com.jingwei.rsswithai.domain.model.Article;

import java.time.LocalDateTime;

/**
 * 文章数据传输对象
 */
public record ArticleDTO(
    Long id,
    Long sourceId,
    String sourceName,
    String title,
    String link,
    String guid,
    String description,
    String content,
    String author,
    LocalDateTime pubDate,
    String categories,
    LocalDateTime fetchedAt,
    LocalDateTime createdAt
) {
    /**
     * 从实体转换为DTO
     */
    public static ArticleDTO from(Article article) {
        return new ArticleDTO(
            article.getId(),
            article.getSource().getId(),
            article.getSource().getName(),
            article.getTitle(),
            article.getLink(),
            article.getGuid(),
            article.getDescription(),
            article.getContent(),
            article.getAuthor(),
            article.getPubDate(),
            article.getCategories(),
            article.getFetchedAt(),
            article.getCreatedAt()
        );
    }
}
