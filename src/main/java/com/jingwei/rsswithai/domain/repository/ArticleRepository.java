package com.jingwei.rsswithai.domain.repository;

import com.jingwei.rsswithai.domain.model.Article;
import com.jingwei.rsswithai.domain.model.RssSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ArticleRepository extends JpaRepository<Article, Long> {
    boolean existsByLink(String link);

    boolean existsByGuid(String guid);

    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM Article a WHERE a.guid = :guid OR a.link = :link")
    boolean existsByGuidOrLink(@Param("guid") String guid, @Param("link") String link);

    Page<Article> findBySourceIdOrderByPubDateDesc(Long sourceId, Pageable pageable);

    @Query("SELECT a FROM Article a WHERE a.source.id = :sourceId AND (a.title LIKE %:searchWord% OR a.author LIKE %:searchWord%) ORDER BY a.pubDate DESC")
    Page<Article> findBySourceIdAndSearchWordOrderByPubDateDesc(@Param("sourceId") Long sourceId, @Param("searchWord") String searchWord, Pageable pageable);

    List<Article> findBySourceAndPubDateAfter(RssSource source, LocalDateTime after);

    Page<Article> findAllByOrderByPubDateDesc(Pageable pageable);

    @Query("SELECT a FROM Article a WHERE a.title LIKE %:searchWord% OR a.author LIKE %:searchWord% ORDER BY a.pubDate DESC")
    Page<Article> findAllBySearchWordOrderByPubDateDesc(@Param("searchWord") String searchWord, Pageable pageable);

    @Query(value = "SELECT CAST(created_at AS DATE), COUNT(*) FROM articles WHERE created_at >= :startDate GROUP BY CAST(created_at AS DATE)", nativeQuery = true)
    List<Object[]> countDailyNewArticles(@Param("startDate") LocalDateTime startDate);

    // 在 ArticleRepository 中添加
    @Modifying
    @Query("UPDATE Article a SET a.source = NULL WHERE a.source.id = :sourceId")
    void detachSource(@Param("sourceId") Long sourceId);

}