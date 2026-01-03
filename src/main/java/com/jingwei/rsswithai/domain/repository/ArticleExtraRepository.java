package com.jingwei.rsswithai.domain.repository;

import com.jingwei.rsswithai.domain.model.ArticleExtra;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 文章增强数据仓储接口
 */
@Repository
public interface ArticleExtraRepository extends JpaRepository<ArticleExtra, Long> {

    /**
     * 检查文章是否已处理
     */
    boolean existsByArticleId(Long articleId);

    /**
     * 使用 @Query 显式指定查询字段，强制 Hibernate 不生成包含 vector 字段的 SQL。
     * 即使使用了 Projection，默认行为有时仍会加载完整实体导致 vector 映射错误。
     */
    @Query("SELECT a.id as id, a.articleId as articleId, a.overview as overview, " +
            "a.keyInformation as keyInformation, a.tags as tags, a.status as status, " +
            "a.errorMessage as errorMessage, a.createdAt as createdAt, a.updatedAt as updatedAt " +
            "FROM ArticleExtra a WHERE a.articleId = :articleId")
    Optional<ArticleExtraNoVectorView> findByArticleId(@Param("articleId") Long articleId);


    /**
     * 原生 SQL 删除，绕过 Hibernate 实体映射检查
     * 解决 vector 为 null 时无法读取导致无法删除的问题
     */
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM article_extra WHERE article_id = :articleId", nativeQuery = true)
    void deleteByArticleId(@Param("articleId") Long articleId);

    /**
     * getArticleExtra 场景专用：不读取 vector，避免 vector 为 null 时的映射异常/无用大字段读取
     */
    interface ArticleExtraNoVectorView {
        Long getId();

        Long getArticleId();

        String getOverview();

        List<String> getKeyInformation();

        List<String> getTags();

        String getStatus();

        String getErrorMessage();

        LocalDateTime getCreatedAt();

        LocalDateTime getUpdatedAt();
    }
}