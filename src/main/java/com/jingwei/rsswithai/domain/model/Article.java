package com.jingwei.rsswithai.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 文章实体 - 表示从RSS源抓取的原始文章
 */
@Entity
@Table(name = "articles", indexes = {
        @Index(name = "idx_article_link", columnList = "link"),
        @Index(name = "idx_article_guid", columnList = "guid"),
        @Index(name = "idx_article_pub_date", columnList = "pubDate")
/*实际上还有：
@Index(name = "idx_article_source_pubdate_covered", columnList = "source_id, pubDate")
include了source_name, title, word_count, cover_image这些字段*/
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
    @JoinColumn(name = "source_id")
    private RssSource source;

    /**
     * RSS源名称（冗余字段，方便查询）
     */
    @Column(length = 200)
    private String sourceName;

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
     * 文章正文原始内容 (HTML格式)
     */
    @Column(columnDefinition = "TEXT")
    private String rawContent;

    /**
     * 文章正文内容 (Markdown格式)
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
     * 文章字数（中文按一个字，英文按一个单词）
     */
    private Long wordCount;

    /**
     * 文章封面图片URL（从markdown内容提取）
     */
    @Column(length = 2000)
    private String coverImage;

    /**
     * 创建时间
     */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}