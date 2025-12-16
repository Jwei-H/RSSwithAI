package com.jingwei.rsswithai.domain.repository;

import com.jingwei.rsswithai.domain.model.AnalysisResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnalysisResultRepository extends JpaRepository<AnalysisResult, Long> {
    List<AnalysisResult> findByExperimentId(Long experimentId);
    List<AnalysisResult> findByArticleId(Long articleId);
}
