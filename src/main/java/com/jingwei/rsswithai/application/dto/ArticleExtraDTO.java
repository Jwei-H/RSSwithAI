package com.jingwei.rsswithai.application.dto;

import com.jingwei.rsswithai.domain.model.AnalysisStatus;
import com.jingwei.rsswithai.domain.model.ArticleExtra;
import com.jingwei.rsswithai.domain.repository.ArticleExtraRepository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;

public record ArticleExtraDTO(
        Long id,
        Long articleId,
        String overview,
        List<String> keyInformation,
        List<String> tags,
    List<TocItemDTO> toc,
        AnalysisStatus status,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static ArticleExtraDTO from(ArticleExtra extra) {
        return new ArticleExtraDTO(
                extra.getId(),
                extra.getArticleId(),
                extra.getOverview(),
                extra.getKeyInformation(),
                extra.getTags(),
        parseToc(extra.getToc()),
                extra.getStatus(),
                extra.getErrorMessage(),
                extra.getCreatedAt(),
                extra.getUpdatedAt()
        );
    }

    public static ArticleExtraDTO from(ArticleExtraRepository.ArticleExtraNoVectorView extra) {
        return new ArticleExtraDTO(
                extra.getId(),
                extra.getArticleId(),
                extra.getOverview(),
                extra.getKeyInformation(),
                extra.getTags(),
                parseToc(extra.getToc()),
                extra.getStatus(),
                extra.getErrorMessage(),
                extra.getCreatedAt(),
                extra.getUpdatedAt()
        );
    }

    private static List<TocItemDTO> parseToc(String tocJson) {
        if (tocJson == null || tocJson.isBlank()) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(tocJson, new TypeReference<>() {
            });
        } catch (Exception ignored) {
            return List.of();
        }
    }

    public record TocItemDTO(
            String title,
            String anchor
    ) {
    }
}