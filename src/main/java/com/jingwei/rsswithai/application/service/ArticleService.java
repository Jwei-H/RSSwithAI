package com.jingwei.rsswithai.application.service;

import com.jingwei.rsswithai.application.dto.ArticleDTO;
import com.jingwei.rsswithai.application.dto.ArticleDetailDTO;
import com.jingwei.rsswithai.application.dto.ArticleExtraDTO;
import com.jingwei.rsswithai.application.dto.ArticleFeedDTO;
import com.jingwei.rsswithai.application.dto.ArticleStatsDTO;
import com.jingwei.rsswithai.domain.model.Article;
import com.jingwei.rsswithai.domain.repository.ArticleExtraRepository;
import com.jingwei.rsswithai.domain.repository.ArticleRepository;
import com.jingwei.rsswithai.config.AppConfig;
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
import java.util.*;
import java.util.stream.Collectors;

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
    private final LlmProcessService llmProcessService;
    private final AppConfig appConfig;

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

    @Transactional(readOnly = true)
    public List<ArticleFeedDTO> searchArticles(String query) {
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Search query cannot be blank");
        }

        List<Long> fuzzyIds = articleRepository.searchIdsByFuzzy(trimmed, 20);

        float[] queryVector = llmProcessService.generateVector(trimmed);
        List<Long> vectorIds = Collections.emptyList();
        if (queryVector != null && queryVector.length > 0) {
            double threshold = appConfig.getFeedSimilarityThreshold() != null
                    ? appConfig.getFeedSimilarityThreshold()
                    : 0.3D;
            vectorIds = articleExtraRepository.searchIdsByVector(toPgVectorLiteral(queryVector), threshold, 50);
        } else {
            log.warn("Vector generation failed, fallback to keyword search only");
        }

        List<Long> merged = mergeHybridIds(fuzzyIds, vectorIds, 20);
        Map<Long, Article> articleMap = toArticleMap(merged);

        return merged.stream()
                .map(articleMap::get)
                .filter(Objects::nonNull)
                .map(article -> ArticleFeedDTO.of(
                        article.getId(),
                        article.getSource() != null ? article.getSource().getId() : null,
                        article.getSourceName(),
                        article.getTitle(),
                        article.getCoverImage(),
                        article.getPubDate()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ArticleFeedDTO> recommendArticles(Long articleId) {
        if (articleId == null || articleId <= 0) {
            throw new IllegalArgumentException("articleId must be positive");
        }

        articleRepository.findById(articleId)
                .orElseThrow(() -> new EntityNotFoundException("文章不存在: " + articleId));

        if (!hasVector(articleId)) {
            return List.of();
        }

        List<Long> similarIds = articleExtraRepository.findSimilarArticleIds(articleId, 2);
        if (similarIds.isEmpty()) {
            return List.of();
        }

        Map<Long, Article> articleMap = toArticleMap(similarIds);
        return similarIds.stream()
            .map(articleMap::get)
            .filter(article -> article != null)
            .map(article -> ArticleFeedDTO.of(
                article.getId(),
                article.getSource() != null ? article.getSource().getId() : null,
                article.getSourceName(),
                article.getTitle(),
                article.getCoverImage(),
                article.getPubDate()
            ))
            .toList();
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

    private boolean hasVector(Long articleId) {
        return articleExtraRepository.existsByArticleIdAndVectorIsNotNull(articleId);
    }

    private Map<Long, Article> toArticleMap(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return articleRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Article::getId, article -> article));
    }

    private List<Long> mergeHybridIds(List<Long> fuzzyIds, List<Long> vectorIds, int limit) {
        List<Long> fuzzyList = fuzzyIds == null ? List.of() : fuzzyIds;
        List<Long> vectorList = vectorIds == null ? List.of() : vectorIds;

        Set<Long> vectorSet = Set.copyOf(vectorList);
        List<Long> intersection = fuzzyList.stream()
                .filter(vectorSet::contains)
                .toList();

        Set<Long> intersectionSet = Set.copyOf(intersection);
        List<Long> fuzzyOnly = fuzzyList.stream()
                .filter(id -> !intersectionSet.contains(id))
                .toList();

        Set<Long> fuzzySet = Set.copyOf(fuzzyList);
        List<Long> vectorOnly = vectorList.stream()
                .filter(id -> !fuzzySet.contains(id))
                .toList();

        List<Long> merged = new ArrayList<>(intersection.size() + fuzzyOnly.size() + vectorOnly.size());
        merged.addAll(intersection);
        merged.addAll(fuzzyOnly);
        merged.addAll(vectorOnly);

        if (merged.size() > limit) {
            return merged.subList(0, limit);
        }
        return merged;
    }

    private String toPgVectorLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        sb.append(']');
        return sb.toString();
    }
}