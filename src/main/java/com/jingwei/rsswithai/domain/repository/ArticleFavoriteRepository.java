package com.jingwei.rsswithai.domain.repository;

import com.jingwei.rsswithai.domain.model.Article;
import com.jingwei.rsswithai.domain.model.ArticleFavorite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ArticleFavoriteRepository extends JpaRepository<ArticleFavorite, Long> {

    boolean existsByUserIdAndArticle_Id(Long userId, Long articleId);

    Optional<ArticleFavorite> findByUserIdAndArticle_Id(Long userId, Long articleId);

    @Query(value = "SELECT a FROM Article a WHERE a.id IN (SELECT af.article.id FROM ArticleFavorite af WHERE af.userId = :userId) " +
            "ORDER BY a.pubDate DESC, a.id DESC",
            countQuery = "SELECT COUNT(af) FROM ArticleFavorite af WHERE af.userId = :userId")
    Page<Article> findFavoriteArticles(@Param("userId") Long userId, Pageable pageable);
}