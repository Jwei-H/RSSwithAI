package com.jingwei.rsswithai.application.service;

import com.jingwei.rsswithai.application.dto.ArticleDTO;
import com.jingwei.rsswithai.application.dto.ArticleDetailDTO;
import com.jingwei.rsswithai.application.dto.ArticleExtraDTO;
import com.jingwei.rsswithai.application.dto.ArticleStatsDTO;
import com.jingwei.rsswithai.domain.model.Article;
import com.jingwei.rsswithai.domain.repository.ArticleExtraRepository;
import com.jingwei.rsswithai.domain.repository.ArticleRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文章服务
 * 提供文章的查询功能
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ArticleService {

    private final ArticleRepository articleRepository;
    private final ArticleExtraRepository articleExtraRepository;

    /**
     * 获取文章详情
     */
    public ArticleDetailDTO getArticle(Long id) {
        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("文章不存在: " + id));
        return ArticleDetailDTO.from(article);
    }

    /**
     * 根据RSS源ID分页获取文章
     */
    public Page<ArticleDTO> getArticlesBySource(Long sourceId, Pageable pageable) {
        return articleRepository.findBySourceIdOrderByPubDateDesc(sourceId, pageable)
                .map(ArticleDTO::from);
    }

    /**
     * 根据RSS源ID和搜索词分页获取文章
     */
    public Page<ArticleDTO> getArticlesBySource(Long sourceId, String searchWord, Pageable pageable) {
        if (searchWord == null || searchWord.trim().isEmpty()) {
            return getArticlesBySource(sourceId, pageable);
        }
        return articleRepository.findBySourceIdAndSearchWordOrderByPubDateDesc(sourceId, searchWord.trim(), pageable)
                .map(ArticleDTO::from);
    }

    /**
     * 分页获取所有文章
     */
    public Page<ArticleDTO> getArticles(Pageable pageable) {
        return articleRepository.findAllByOrderByPubDateDesc(pageable)
                .map(ArticleDTO::from);
    }

    /**
     * 根据搜索词分页获取所有文章
     */
    public Page<ArticleDTO> getArticles(String searchWord, Pageable pageable) {
        if (searchWord == null || searchWord.trim().isEmpty()) {
            return getArticles(pageable);
        }
        return articleRepository.findAllBySearchWordOrderByPubDateDesc(searchWord.trim(), pageable)
                .map(ArticleDTO::from);
    }

    /**
     * 获取文章增强信息
     */
    public ArticleExtraDTO getArticleExtra(Long articleId) {
        return articleExtraRepository.findByArticleId(articleId)
                .map(ArticleExtraDTO::from)
                .orElse(null);
    }

    /**
     * 获取文章统计信息
     */
    public ArticleStatsDTO getStats() {
        long total = articleRepository.count();
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(6).withHour(0).withMinute(0).withSecond(0).withNano(0);
        List<Object[]> dailyCountsRaw = articleRepository.countDailyNewArticles(sevenDaysAgo);

        Map<LocalDate, Long> countsMap = new HashMap<>();
        for (Object[] row : dailyCountsRaw) {
            LocalDate date;
            if (row[0] instanceof java.sql.Date) {
                date = ((java.sql.Date) row[0]).toLocalDate();
            } else {
                date = LocalDate.parse(row[0].toString());
            }
            long count = ((Number) row[1]).longValue();
            countsMap.put(date, count);
        }

        List<ArticleStatsDTO.DailyCount> dailyCounts = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = sevenDaysAgo.toLocalDate().plusDays(i);
            dailyCounts.add(new ArticleStatsDTO.DailyCount(date, countsMap.getOrDefault(date, 0L)));
        }

        return new ArticleStatsDTO(total, dailyCounts);
    }

    @Transactional
    public Article saveArticleIfNotExists(Article article) {
        if (articleRepository.existsByGuidOrLink(article.getGuid(), article.getLink())) {
            return null;
        }
        try {
            return articleRepository.save(article);
        } catch (DataIntegrityViolationException e) {
            log.debug("文章保存跳过（并发重复）: guid={}", article.getGuid());
            return null;
        }
    }
}