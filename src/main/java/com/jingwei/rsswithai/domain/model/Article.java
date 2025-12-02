package com.jingwei.rsswithai.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 文章实体 - 表示从RSS源抓取的原始文章
 */
@Entity
@Table(name = "articles", indexes = {
    @Index(name = "idx_article_link", columnList = "link"),
    @Index(name = "idx_article_guid", columnList = "guid"),
    @Index(name = "idx_article_pub_date", columnList = "pubDate"),
    @Index(name = "idx_article_source", columnList = "source_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Article {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 所属RSS源
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id", nullable = false)
    private RssSource source;

    /**
     * 文章标题
     */
    @Column(nullable = false, length = 500)
    private String title;

    /**
     * 文章链接
     */
    @Column(nullable = false, length = 2000)
    private String link;

    /**
     * 文章唯一标识（用于去重）
     */
    @Column(length = 500)
    private String guid;

    /**
     * 文章摘要/描述
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * 文章正文内容
     */
    @Column(columnDefinition = "TEXT")
    private String content;

    /**
     * 作者
     */
    @Column(length = 200)
    private String author;

    /**
     * 文章发布时间
     */
    private LocalDateTime pubDate;

    /**
     * 文章分类
     */
    @Column(length = 500)
    private String categories;

    /**
     * 抓取时间
     */
    @Column(nullable = false)
    private LocalDateTime fetchedAt;

    /**
     * 创建时间
     */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (fetchedAt == null) {
            fetchedAt = LocalDateTime.now();
        }
    }

    /**
     * 获取用于去重的唯一标识
     * 优先使用guid，其次使用link
     */
    public String getUniqueIdentifier() {
        return guid != null && !guid.isBlank() ? guid : link;
    }
}
