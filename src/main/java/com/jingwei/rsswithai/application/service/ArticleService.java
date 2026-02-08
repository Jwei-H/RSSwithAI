package com.jingwei.rsswithai.application.service;

import com.jingwei.rsswithai.application.dto.*;
import com.jingwei.rsswithai.domain.model.Article;
import com.jingwei.rsswithai.domain.model.ArticleFavorite;
import com.jingwei.rsswithai.domain.model.RssSource;
import com.jingwei.rsswithai.domain.model.SubscriptionType;
import com.jingwei.rsswithai.domain.repository.ArticleExtraRepository;
import com.jingwei.rsswithai.domain.repository.ArticleFavoriteRepository;
import com.jingwei.rsswithai.domain.repository.ArticleRepository;
import com.jingwei.rsswithai.domain.repository.SubscriptionRepository;
import com.jingwei.rsswithai.interfaces.front.FrontArticleController;
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
    private final ArticleFavoriteRepository articleFavoriteRepository;
    private final LlmProcessService llmProcessService;
    private final SubscriptionRepository subscriptionRepository;

    /**
     * 获取文章详情
     */
    public ArticleDetailDTO getArticle(Long id) {
        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("文章不存在: " + id));
        return ArticleDetailDTO.from(article);
    }

    public ArticleDetailDTO getArticle(Long id, Long userId) {
        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("文章不存在: " + id));
        if (userId == null) {
            return ArticleDetailDTO.from(article, false);
        }
        boolean isFavorite = articleFavoriteRepository.existsByUserIdAndArticle_Id(userId, id);
        return ArticleDetailDTO.from(article, isFavorite);
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

    public boolean existsBySourceAndGuidOrLink(Long sourceId, String guid, String link) {
        if (sourceId == null) {
            return false;
        }
        return articleRepository.existsBySourceIdAndGuidOrLink(sourceId, guid, link);
    }

    /**
     * 获取文章增强信息
     */
    public ArticleExtraDTO getArticleExtra(Long articleId) {
        return articleExtraRepository.findByArticleId(articleId)
                .map(ArticleExtraDTO::from)
                .orElse(null);
    }

    @Transactional
    public void favoriteArticle(Long userId, Long articleId) {
        if (articleId == null || articleId <= 0) {
            throw new IllegalArgumentException("articleId must be positive");
        }
        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new EntityNotFoundException("文章不存在: " + articleId));

        if (articleFavoriteRepository.existsByUserIdAndArticle_Id(userId, articleId)) {
            return;
        }

        articleFavoriteRepository.save(ArticleFavorite.builder()
                .userId(userId)
                .article(article)
                .build());
    }

    @Transactional
    public void unfavoriteArticle(Long userId, Long articleId) {
        if (articleId == null || articleId <= 0) {
            throw new IllegalArgumentException("articleId must be positive");
        }
        articleFavoriteRepository.findByUserIdAndArticle_Id(userId, articleId)
                .ifPresent(articleFavoriteRepository::delete);
    }

    @Transactional(readOnly = true)
    public Page<ArticleFeedDTO> listFavoriteArticles(Long userId, Pageable pageable) {
        return articleRepository.findFavoriteFeedByUserId(userId, pageable)
            .map(this::toFeedDto);
    }

    @Transactional(readOnly = true)
    public List<ArticleFeedDTO> searchArticles(String query, FrontArticleController.SearchScope scope, Long userId) {
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Search query cannot be blank");
        }

        if (userId == null && scope != FrontArticleController.SearchScope.ALL) {
            return List.of();
        }

        return switch (scope) {
            case SUBSCRIBED -> searchInSubscriptions(trimmed, userId);
            case FAVORITE -> searchInFavorites(trimmed, userId);
            case ALL -> searchAll(trimmed);
        };
    }

    private List<ArticleFeedDTO> searchAll(String query) {
        return executeSearch(searchIdsByFuzzyAll(query), searchIdsByVectorAll(query));
    }

    private List<ArticleFeedDTO> searchInSubscriptions(String query, Long userId) {
        List<Long> sourceIds = subscriptionRepository.findByUserIdAndTypeWithSource(userId, SubscriptionType.RSS)
                .stream()
                .filter(sub -> sub.getSource() != null)
                .map(sub -> sub.getSource().getId())
                .toList();

        if (sourceIds.isEmpty()) {
            return List.of();
        }

        return executeSearch(
                searchIdsByFuzzyInSources(query, sourceIds),
                searchIdsByVectorInSources(query, sourceIds));
    }

    private List<ArticleFeedDTO> searchInFavorites(String query, Long userId) {
        return executeSearch(
                searchIdsByFuzzyInFavorites(query, userId),
                searchIdsByVectorInFavorites(query, userId));
    }

    private List<ArticleFeedDTO> executeSearch(List<Long> fuzzyIds,
            List<ArticleExtraRepository.IdWithDistance> vectorResults) {
        Set<Long> allIds = new HashSet<>();
        if (fuzzyIds != null)
            allIds.addAll(fuzzyIds);

        Map<Long, Double> vectorDistanceMap = new HashMap<>();
        if (vectorResults != null) {
            for (ArticleExtraRepository.IdWithDistance result : vectorResults) {
                allIds.add(result.getArticleId());
                vectorDistanceMap.put(result.getArticleId(), result.getDistance());
            }
        }

        if (allIds.isEmpty()) {
            return List.of();
        }

        Map<Long, ArticleRepository.ArticleFeedView> feedMap = toFeedViewMap(allIds);

        return allIds.stream()
            .map(feedMap::get)
                .filter(Objects::nonNull)
            .map(feed -> {
                    double score = 0.0;

                    // 1. Vector Score (Relevance): 1 - distance
                    // OpenAI distance is 0..2 (Cosine Distance). We map it to score.
                    // Lower distance = Higher score.
                    Double distance = vectorDistanceMap.get(feed.getId());
                    if (distance != null) {
                        // Weight=1.5 implies semantic match is very important
                        score += (1.0 - distance) * 1.5;
                    }

                    // 2. Keyword Score (Exactness): Fixed bonus
                    if (fuzzyIds != null && fuzzyIds.contains(feed.getId())) {
                        score += 1.0;
                    }

                    // 3. Time Decay (Freshness): Gaussian or Linear decay?
                    // Let's use Simple Logic: Score = Score / (1 + age_in_days * 0.1)
                    // 10 days old => score / 2.
                        LocalDateTime pubDate = feed.getPubDate();
                        long daysDiff = pubDate == null ? 0
                            : java.time.temporal.ChronoUnit.DAYS.between(pubDate, LocalDateTime.now());
                    if (daysDiff < 0)
                        daysDiff = 0; // Future articles treated as now

                    double decay = 1.0 / (1.0 + daysDiff * 0.1);
                    double finalScore = score * decay;

                    return Map.entry(feed, finalScore);
                })
                .sorted(Map.Entry.<ArticleRepository.ArticleFeedView, Double>comparingByValue().reversed())
                .map(entry -> toFeedDto(entry.getKey()))
                .toList();
    }

    private List<Long> searchIdsByFuzzyAll(String query) {
        return articleRepository.searchIdsByFuzzy(query, 20);
    }

    private List<Long> searchIdsByFuzzyInSources(String query, List<Long> sourceIds) {
        return articleRepository.searchIdsByFuzzyInSources(query, sourceIds, 20);
    }

    private List<Long> searchIdsByFuzzyInFavorites(String query, Long userId) {
        return articleRepository.searchIdsByFuzzyInFavorites(query, userId, 20);
    }

    private List<ArticleExtraRepository.IdWithDistance> searchIdsByVectorAll(String query) {
        float[] vector = llmProcessService.generateVector(query);
        if (vector == null || vector.length == 0) {
            log.warn("Vector generation failed, fallback to keyword search only");
            return Collections.emptyList();
        }
        double threshold = 0.45D;

        return articleExtraRepository.searchIdsByVector(toPgVectorLiteral(vector), threshold, 50);
    }

    private List<ArticleExtraRepository.IdWithDistance> searchIdsByVectorInSources(String query, List<Long> sourceIds) {
        float[] vector = llmProcessService.generateVector(query);
        if (vector == null || vector.length == 0) {
            log.warn("Vector generation failed, fallback to keyword search only");
            return Collections.emptyList();
        }
        double threshold = 0.45D;
        return articleExtraRepository.searchIdsByVectorInSources(toPgVectorLiteral(vector), sourceIds, threshold, 50);
    }

    private List<ArticleExtraRepository.IdWithDistance> searchIdsByVectorInFavorites(String query, Long userId) {
        float[] vector = llmProcessService.generateVector(query);
        if (vector == null || vector.length == 0) {
            log.warn("Vector generation failed, fallback to keyword search only");
            return Collections.emptyList();
        }
        double threshold = 0.45D;
        return articleExtraRepository.searchIdsByVectorInFavorites(toPgVectorLiteral(vector), userId, threshold, 50);
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

        Map<Long, ArticleRepository.ArticleFeedView> feedMap = toFeedViewMap(similarIds);
        return similarIds.stream()
            .map(feedMap::get)
                .filter(Objects::nonNull)
            .map(this::toFeedDto)
                .toList();
    }

    /**
     * 获取文章统计信息
     */
    public ArticleStatsDTO getStats() {
        long total = articleRepository.count();
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(6).withHour(0).withMinute(0).withSecond(0)
                .withNano(0);
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
        boolean exists = Optional.ofNullable(article.getSource())
                .map(RssSource::getId)
                .map(sourceId -> articleRepository.existsBySourceIdAndGuidOrLink(sourceId, article.getGuid(), article.getLink()))
                .orElse(false);
        if (exists) {
            return null;
        }
        try {
            return articleRepository.save(article);
        } catch (DataIntegrityViolationException e) {
            log.debug("文章保存跳过（并发重复）: guid={}", article.getGuid());
            return null;
        }
    }

    /**
     * 根据RSS源ID分页获取文章（FeedDTO）
     */
    public Page<ArticleFeedDTO> getArticleFeedsBySource(Long sourceId, Pageable pageable) {
        return articleRepository.findFeedBySourceId(sourceId, pageable)
            .map(this::toFeedDto);
    }

    private ArticleFeedDTO toFeedDto(ArticleRepository.ArticleFeedView view) {
        return ArticleFeedDTO.of(
            view.getId(),
            view.getSourceId(),
            view.getSourceName(),
            view.getTitle(),
            view.getCoverImage(),
            view.getPubDate(),
            view.getWordCount());
    }

    private boolean hasVector(Long articleId) {
        return articleExtraRepository.existsByArticleIdAndVectorIsNotNull(articleId);
    }

    private Map<Long, ArticleRepository.ArticleFeedView> toFeedViewMap(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        List<ArticleRepository.ArticleFeedView> feeds = articleRepository.findFeedByIds(new ArrayList<>(ids));
        return feeds.stream()
                .collect(Collectors.toMap(ArticleRepository.ArticleFeedView::getId, feed -> feed));
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