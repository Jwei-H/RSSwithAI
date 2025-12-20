package com.jingwei.rsswithai.application.dto;

import com.jingwei.rsswithai.domain.model.Article;

import java.time.LocalDateTime;

public record ArticleDTO(
        Long id,
        Long sourceId,
        String sourceName,
        String title,
        String link,
        String guid,
        String description,
        String rawContent,
        String content,
        String author,
        LocalDateTime pubDate,
        String categories,
        LocalDateTime fetchedAt,
        LocalDateTime createdAt
) {
    // 两个方法，一个不包含content等字段，用于列表查询；另一个包含完整信息
    public static ArticleDTO from(Article article) {
        return new ArticleDTO(
                article.getId(),
                article.getSource().getId(),
                article.getSourceName(),
                article.getTitle(),
                article.getLink(),
                article.getGuid(),
                article.getDescription(),
                article.getRawContent(),
                article.getContent(),
                article.getAuthor(),
                article.getPubDate(),
                article.getCategories(),
                article.getFetchedAt(),
                article.getCreatedAt()
        );
    }

    public static ArticleDTO fromBasic(Article article) {
        return new ArticleDTO(
                article.getId(),
                article.getSource().getId(),
                article.getSourceName(),
                article.getTitle(),
                article.getLink(),
                article.getGuid(),
                null,
                null,
                null,
                article.getAuthor(),
                article.getPubDate(),
                article.getCategories(),
                article.getFetchedAt(),
                article.getCreatedAt()
        );
    }

}