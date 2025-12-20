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

@Repository
public interface ArticleRepository extends JpaRepository<Article, Long> {
    boolean existsByLink(String link);

    boolean existsByGuid(String guid);

    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM Article a WHERE a.guid = :guid OR a.link = :link")
    boolean existsByGuidOrLink(@Param("guid") String guid, @Param("link") String link);

    Page<Article> findBySourceIdOrderByPubDateDesc(Long sourceId, Pageable pageable);

    List<Article> findBySourceAndPubDateAfter(RssSource source, LocalDateTime after);

    Page<Article> findAllByOrderByPubDateDesc(Pageable pageable);
}