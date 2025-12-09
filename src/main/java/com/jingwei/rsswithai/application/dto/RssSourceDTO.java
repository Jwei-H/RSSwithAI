package com.jingwei.rsswithai.application.dto;

import com.jingwei.rsswithai.domain.model.FetchStatus;
import com.jingwei.rsswithai.domain.model.RssSource;
import com.jingwei.rsswithai.domain.model.SourceStatus;
import com.jingwei.rsswithai.domain.model.SourceType;

import java.time.LocalDateTime;

/**
 * RSS源数据传输对象
 */
public record RssSourceDTO(
    Long id,
    String name,
    String url,
    SourceType type,
    String description,
    Integer fetchIntervalMinutes,
    SourceStatus status,
    FetchStatus lastFetchStatus,
    LocalDateTime lastFetchTime,
    String lastFetchError,
    Integer failureCount,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    /**
     * 从实体转换为DTO
     */
    public static RssSourceDTO from(RssSource source) {
        return new RssSourceDTO(
            source.getId(),
            source.getName(),
            source.getUrl(),
            source.getType(),
            source.getDescription(),
            source.getFetchIntervalMinutes(),
            source.getStatus(),
            source.getLastFetchStatus(),
            source.getLastFetchTime(),
            source.getLastFetchError(),
            source.getFailureCount(),
            source.getCreatedAt(),
            source.getUpdatedAt()
        );
    }
}
