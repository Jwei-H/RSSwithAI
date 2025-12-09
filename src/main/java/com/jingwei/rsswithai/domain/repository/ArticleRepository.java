package com.jingwei.rsswithai.domain.repository;

import com.jingwei.rsswithai.domain.model.Article;
import com.jingwei.rsswithai.domain.model.RssSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 文章仓储接口
 */
@Repository
public interface ArticleRepository extends JpaRepository<Article, Long> {

    /**
     * 根据链接查找文章
     */
    Optional<Article> findByLink(String link);

    /**
     * 根据GUID查找文章
     */
    Optional<Article> findByGuid(String guid);

    /**
     * 检查链接是否已存在
     */
    boolean existsByLink(String link);

    /**
     * 检查GUID是否已存在
     */
    boolean existsByGuid(String guid);

    /**
     * 检查文章是否已存在（通过GUID或链接）
     */
    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM Article a WHERE a.guid = :guid OR a.link = :link")
    boolean existsByGuidOrLink(@Param("guid") String guid, @Param("link") String link);

    /**
     * 根据RSS源查找文章
     */
    List<Article> findBySource(RssSource source);

    /**
     * 根据RSS源ID分页查找文章
     */
    Page<Article> findBySourceIdOrderByPubDateDesc(Long sourceId, Pageable pageable);

    /**
     * 根据RSS源和发布时间范围查找文章
     */
    List<Article> findBySourceAndPubDateAfter(RssSource source, LocalDateTime after);
}
