package com.jingwei.rsswithai.domain.repository;

import com.jingwei.rsswithai.domain.model.Article;
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

    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM Article a WHERE a.guid = :guid OR a.link = :link")
    boolean existsByGuidOrLink(@Param("guid") String guid, @Param("link") String link);

    Page<Article> findBySourceIdOrderByPubDateDesc(Long sourceId, Pageable pageable);

    @Query(value = "SELECT * FROM articles WHERE source_id = :sourceId AND (title ILIKE CONCAT('%', :searchWord, '%') OR author ILIKE CONCAT('%', :searchWord, '%')) ORDER BY pub_date DESC", countQuery = "SELECT count(*) FROM articles WHERE source_id = :sourceId AND (title ILIKE CONCAT('%', :searchWord, '%') OR author ILIKE CONCAT('%', :searchWord, '%'))", nativeQuery = true)
    Page<Article> findBySourceIdAndSearchWordOrderByPubDateDesc(@Param("sourceId") Long sourceId,
            @Param("searchWord") String searchWord, Pageable pageable);

    Page<Article> findAllByOrderByPubDateDesc(Pageable pageable);

    @Query(value = "SELECT * FROM articles WHERE title ILIKE CONCAT('%', :searchWord, '%') OR author ILIKE CONCAT('%', :searchWord, '%') OR source_name ILIKE CONCAT('%', :searchWord, '%') ORDER BY pub_date DESC", countQuery = "SELECT count(*) FROM articles WHERE title ILIKE CONCAT('%', :searchWord, '%') OR author ILIKE CONCAT('%', :searchWord, '%') OR source_name ILIKE CONCAT('%', :searchWord, '%')", nativeQuery = true)
    Page<Article> findAllBySearchWordOrderByPubDateDesc(@Param("searchWord") String searchWord, Pageable pageable);

    @Query(value = "SELECT id FROM articles WHERE title ILIKE CONCAT('%', :likePattern, '%') OR author ILIKE CONCAT('%', :likePattern, '%') OR source_name ILIKE CONCAT('%', :likePattern, '%') ORDER BY pub_date DESC LIMIT :limit", nativeQuery = true)
    List<Long> searchIdsByFuzzy(@Param("likePattern") String likePattern, @Param("limit") int limit);

    @Query(value = "SELECT id FROM articles WHERE source_id IN (:sourceIds) AND (title ILIKE CONCAT('%', :likePattern, '%') OR author ILIKE CONCAT('%', :likePattern, '%') OR source_name ILIKE CONCAT('%', :likePattern, '%')) ORDER BY pub_date DESC LIMIT :limit", nativeQuery = true)
    List<Long> searchIdsByFuzzyInSources(@Param("likePattern") String likePattern, @Param("sourceIds") List<Long> sourceIds, @Param("limit") int limit);

    @Query(value = "SELECT CAST(created_at AS DATE), COUNT(*) FROM articles WHERE created_at >= :startDate GROUP BY CAST(created_at AS DATE)", nativeQuery = true)
    List<Object[]> countDailyNewArticles(@Param("startDate") LocalDateTime startDate);

    @Modifying
    @Query("UPDATE Article a SET a.source = NULL WHERE a.source.id = :sourceId")
    void detachSource(@Param("sourceId") Long sourceId);

    @Query(value = "SELECT a.id FROM articles a LEFT JOIN article_extra ae ON a.id = ae.article_id WHERE ae.id IS NULL AND a.created_at >= :since", nativeQuery = true)
    List<Long> findArticleIdsWithoutExtraSince(@Param("since") LocalDateTime since);

}