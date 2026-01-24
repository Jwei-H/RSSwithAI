package com.jingwei.rsswithai.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * RSS源实体 - 表示一个RSS订阅源的配置信息
 */
@Entity
@Table(name = "rss_sources")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RssSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 源名称
     */
    @Column(nullable = false)
    private String name;

    /**
     * RSS源URL或RSSHub路由
     */
    @Column(nullable = false)
    private String url;

    /**
     * 源类型 (ORIGIN, RSSHUB)
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private SourceType type = SourceType.ORIGIN;

    /**
     * 源描述
     */
    @Column(length = 1000)
    private String description;

    /**
     * 抓取频率（分钟）
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer fetchIntervalMinutes = 30;

    /**
     * 源状态
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private SourceStatus status = SourceStatus.ENABLED;

    /**
     * 最后一次抓取状态
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private FetchStatus lastFetchStatus = FetchStatus.NEVER;

    /**
     * 最后一次抓取时间
     */
    private LocalDateTime lastFetchTime;

    /**
     * 最后一次抓取错误信息
     */
    @Column(length = 2000)
    private String lastFetchError;

    /**
     * 连续失败次数
     */
    @Builder.Default
    private Integer failureCount = 0;

    /**
     * RSS源图标URL（非必须）
     */
    @Column(length = 256)
    private String link;

    /**
     * RSS源分类
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private SourceCategory category = SourceCategory.OTHER;

    /**
     * 创建时间
     */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    /**
     * 判断是否需要抓取（基于抓取间隔）
     */
    public boolean shouldFetch() {
        if (!(status == SourceStatus.ENABLED && lastFetchStatus != FetchStatus.FETCHING)) {
            return false;
        }
        if (lastFetchTime == null) {
            return true;
        }
        return LocalDateTime.now().isAfter(lastFetchTime.plusMinutes(fetchIntervalMinutes));
    }

    /**
     * 记录抓取成功
     */
    public void recordFetchSuccess() {
        this.lastFetchStatus = FetchStatus.SUCCESS;
        this.lastFetchTime = LocalDateTime.now();
        this.lastFetchError = null;
        this.failureCount = 0;
    }

    /**
     * 记录抓取失败
     */
    public void recordFetchFailure(String errorMessage) {
        this.lastFetchStatus = FetchStatus.FAILED;
        this.lastFetchError = errorMessage;
        this.failureCount++;
    }

    /**
     * 标记为正在抓取
     */
    public void markAsFetching() {
        this.lastFetchStatus = FetchStatus.FETCHING;
    }
}