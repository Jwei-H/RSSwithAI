package com.jingwei.rsswithai.domain.repository;

import com.jingwei.rsswithai.domain.model.ArticleFavorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ArticleFavoriteRepository extends JpaRepository<ArticleFavorite, Long> {

    boolean existsByUserIdAndArticle_Id(Long userId, Long articleId);

    Optional<ArticleFavorite> findByUserIdAndArticle_Id(Long userId, Long articleId);

}