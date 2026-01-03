package com.jingwei.rsswithai.domain.repository;

import com.jingwei.rsswithai.domain.model.AnalysisResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AnalysisResultRepository extends JpaRepository<AnalysisResult, Long> {
    @EntityGraph(attributePaths = {"experiment"})
    Page<AnalysisResult> findPageByArticleId(Long articleId, Pageable pageable);

    @EntityGraph(attributePaths = {"article"})
    Page<AnalysisResult> findPageByExperimentId(Long experimentId, Pageable pageable);

    @EntityGraph(attributePaths = {"experiment", "article"})
    Optional<AnalysisResult> findWithExperimentAndArticleById(Long id);

    void deleteByExperimentId(Long id);
}