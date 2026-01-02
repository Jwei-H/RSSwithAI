package com.jingwei.rsswithai.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章增强数据模型
 * 与Article表为一对一关系，存储AI增强处理结果
 */
@Entity
@Table(name = "article_extra", indexes = {
        @Index(name = "idx_article_extra_article_id", columnList = "article_id", unique = true)
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ArticleExtra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 关联的文章ID，外键指向articles.id
     */
    @Column(name = "article_id", nullable = false, unique = true)
    private Long articleId;

    /**
     * 文章概览
     */
    @Column(columnDefinition = "TEXT")
    private String overview;

    /**
     * 关键信息列表（1-3条，每条40字以内）
     */
    @Column(columnDefinition = "TEXT[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<String> keyInformation;

    /**
     * 标签列表（5个左右）
     */
    @Column(columnDefinition = "TEXT[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<String> tags;

    /**
     * 文章向量表示（1024维，由embedding模型生成）
     */
    @Column
    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = 1024)
    private float[] vector;

    /**
     * 最终状态：SUCCESS, FAILED
     */
    @Column(nullable = false, length = 50)
    private String status;

    /**
     * 处理错误信息
     */
    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * 创建时间
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}