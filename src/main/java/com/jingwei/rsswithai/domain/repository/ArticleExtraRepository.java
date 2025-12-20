package com.jingwei.rsswithai.domain.repository;

import com.jingwei.rsswithai.domain.model.ArticleExtra;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 文章增强数据仓储接口
 */
@Repository
public interface ArticleExtraRepository extends JpaRepository<ArticleExtra, Long> {

    /**
     * 根据文章ID查询增强数据
     */
    Optional<ArticleExtra> findByArticleId(Long articleId);

    /**
     * 检查文章是否已处理
     */
    boolean existsByArticleId(Long articleId);
}
