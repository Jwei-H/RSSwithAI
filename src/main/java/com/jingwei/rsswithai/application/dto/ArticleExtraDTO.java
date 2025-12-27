package com.jingwei.rsswithai.application.dto;

import com.jingwei.rsswithai.domain.model.ArticleExtra;

import java.time.LocalDateTime;
import java.util.List;

public record ArticleExtraDTO(
        Long id,
        Long articleId,
        String overview,
        List<String> keyInformation,
        List<String> tags,
        String status,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ArticleExtraDTO from(ArticleExtra extra) {
        return new ArticleExtraDTO(
                extra.getId(),
                extra.getArticleId(),
                extra.getOverview(),
                extra.getKeyInformation(),
                extra.getTags(),
                extra.getStatus(),
                extra.getErrorMessage(),
                extra.getCreatedAt(),
                extra.getUpdatedAt()
        );
    }
}