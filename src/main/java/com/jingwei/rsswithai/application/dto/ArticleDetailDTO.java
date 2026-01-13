package com.jingwei.rsswithai.application.dto;

import com.jingwei.rsswithai.domain.model.Article;

import java.time.LocalDateTime;

public record ArticleDetailDTO(
        Long id,
        Long sourceId,
        String sourceName,
        String title,
        String link,
        String description,
        String content,
        String author,
        LocalDateTime pubDate,
        String categories,
        Long wordCount,
        String coverImage,
        LocalDateTime fetchedAt,
        LocalDateTime createdAt)
{
    public static ArticleDetailDTO from(Article article) {
        return new ArticleDetailDTO(
                article.getId(),
                article.getSource().getId(),
                article.getSourceName(),
                article.getTitle(),
                article.getLink(),
                article.getDescription(),
                article.getContent(),
                article.getAuthor(),
                article.getPubDate(),
                article.getCategories(),
                article.getWordCount(),
                article.getCoverImage(),
                article.getFetchedAt(),
                article.getCreatedAt()
        );
    }
}