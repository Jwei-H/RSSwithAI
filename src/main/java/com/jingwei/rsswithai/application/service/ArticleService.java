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
import com.qianxinyao.analysis.jieba.keyword.Keyword;
import com.qianxinyao.analysis.jieba.keyword.TFIDFAnalyzer;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.Supplier;
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

    private static final double VECTOR_SIMILARITY_THRESHOLD = 0.4D;
    private static final int FUZZY_RECALL_LIMIT = 20;
    private static final int VECTOR_RECALL_LIMIT = 50;
    private static final int TFIDF_TOP_N = 1;
    private static final ExecutorService SEARCH_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

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
    public List<ArticleFeedDTO> searchArticles(String query, FrontArticleController.SearchScope scope, Long sourceId, Long userId) {
        String normalizedQuery = normalizeSearchQuery(query);

        if (isSourceScopedSearch(sourceId)) {
            return searchInSource(normalizedQuery, sourceId);
        }

        if (requiresAuthenticatedUser(scope) && userId == null) {
            return List.of();
        }

        return searchByScope(normalizedQuery, scope, userId);
    }

    private List<ArticleFeedDTO> searchByScope(String query, FrontArticleController.SearchScope scope, Long userId) {
        return switch (scope) {
            case SUBSCRIBED -> searchInSubscriptions(query, userId);
            case FAVORITE -> searchInFavorites(query, userId);
            case ALL -> searchAll(query);
        };
    }

    private String normalizeSearchQuery(String query) {
        String normalized = query == null ? "" : query.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Search query cannot be blank");
        }
        return normalized;
    }

    private boolean isSourceScopedSearch(Long sourceId) {
        return sourceId != null && sourceId > 0;
    }

    private boolean requiresAuthenticatedUser(FrontArticleController.SearchScope scope) {
        return scope != FrontArticleController.SearchScope.ALL;
    }

    private List<ArticleFeedDTO> searchAll(String query) {
        return executeSearchParallel(() -> searchIdsByFuzzyAll(query), () -> searchIdsByVectorAll(query));
    }

    private List<ArticleFeedDTO> searchInSource(String query, Long sourceId) {
        List<Long> sourceIds = List.of(sourceId);
        return executeSearchParallel(
                () -> searchIdsByFuzzyInSources(query, sourceIds),
                () -> searchIdsByVectorInSources(query, sourceIds));
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

        return executeSearchParallel(
                () -> searchIdsByFuzzyInSources(query, sourceIds),
                () -> searchIdsByVectorInSources(query, sourceIds));
    }

    private List<ArticleFeedDTO> searchInFavorites(String query, Long userId) {
        return executeSearchParallel(
                () -> searchIdsByFuzzyInFavorites(query, userId),
                () -> searchIdsByVectorInFavorites(query, userId));
    }

    private List<ArticleFeedDTO> executeSearchParallel(
            Supplier<List<Long>> fuzzySupplier,
            Supplier<List<ArticleExtraRepository.IdWithDistance>> vectorSupplier) {
        var fuzzyFuture = CompletableFuture.supplyAsync(fuzzySupplier, SEARCH_EXECUTOR)
                .exceptionally(ex -> {
                    log.warn("Fuzzy recall failed, fallback to vector only", ex);
                    return List.of();
                });

        var vectorFuture = CompletableFuture.supplyAsync(vectorSupplier, SEARCH_EXECUTOR)
                .exceptionally(ex -> {
                    log.warn("Vector recall failed, fallback to fuzzy only", ex);
                    return Collections.emptyList();
                });

        return executeSearch(fuzzyFuture.join(), vectorFuture.join());
    }

    private List<ArticleFeedDTO> executeSearch(List<Long> fuzzyIds,
            List<ArticleExtraRepository.IdWithDistance> vectorResults) {
        Set<Long> fuzzyIdSet = fuzzyIds == null ? Set.of() : new HashSet<>(fuzzyIds);
        Set<Long> allIds = new HashSet<>();
        allIds.addAll(fuzzyIdSet);

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
                .map(feed -> Map.entry(feed, calculateFinalScore(feed, fuzzyIdSet, vectorDistanceMap)))
                .sorted(Map.Entry.<ArticleRepository.ArticleFeedView, Double>comparingByValue().reversed())
                .map(entry -> toFeedDto(entry.getKey()))
                .toList();
    }

    private double calculateFinalScore(
            ArticleRepository.ArticleFeedView feed,
            Set<Long> fuzzyIdSet,
            Map<Long, Double> vectorDistanceMap) {
        double score = 0.0;

        Double distance = vectorDistanceMap.get(feed.getId());
        if (distance != null) {
            score += (1.0 - distance) * 1.5;
        }

        if (fuzzyIdSet.contains(feed.getId())) {
            score += 1.0;
        }

        long daysDiff = getDaysDiff(feed.getPubDate());
        double decay = 1.0 / (1.0 + daysDiff * 0.1);
        return score * decay;
    }

    private long getDaysDiff(LocalDateTime pubDate) {
        long daysDiff = pubDate == null ? 0
                : java.time.temporal.ChronoUnit.DAYS.between(pubDate, LocalDateTime.now());
        return Math.max(daysDiff, 0);
    }

    private List<Long> searchIdsByFuzzyAll(String query) {
        return searchIdsByFuzzyWithKeyword(
                () -> articleRepository.searchIdsByFuzzy(query, FUZZY_RECALL_LIMIT),
                keyword -> articleRepository.searchIdsByFuzzy(keyword, FUZZY_RECALL_LIMIT),
                query,
                FUZZY_RECALL_LIMIT);
    }

    private List<Long> searchIdsByFuzzyInSources(String query, List<Long> sourceIds) {
        return searchIdsByFuzzyWithKeyword(
                () -> articleRepository.searchIdsByFuzzyInSources(query, sourceIds, FUZZY_RECALL_LIMIT),
                keyword -> articleRepository.searchIdsByFuzzyInSources(keyword, sourceIds, FUZZY_RECALL_LIMIT),
                query,
                FUZZY_RECALL_LIMIT);
    }

    private List<Long> searchIdsByFuzzyInFavorites(String query, Long userId) {
        return searchIdsByFuzzyWithKeyword(
                () -> articleRepository.searchIdsByFuzzyInFavorites(query, userId, FUZZY_RECALL_LIMIT),
                keyword -> articleRepository.searchIdsByFuzzyInFavorites(keyword, userId, FUZZY_RECALL_LIMIT),
                query,
                FUZZY_RECALL_LIMIT);
    }

    private List<Long> searchIdsByFuzzyWithKeyword(
            Supplier<List<Long>> querySearch,
            Function<String, List<Long>> keywordSearch,
            String query,
            int limit) {
        List<Long> queryIds = Optional.ofNullable(querySearch.get()).orElseGet(List::of);
        String topKeyword = extractTopKeyword(query);
        if (topKeyword == null || topKeyword.equalsIgnoreCase(query)) {
            return queryIds;
        }

        List<Long> keywordIds = Optional.ofNullable(keywordSearch.apply(topKeyword)).orElseGet(List::of);
        if (keywordIds.isEmpty()) {
            return queryIds;
        }

        return mergeWithLimit(queryIds, keywordIds, limit);
    }

    private List<Long> mergeWithLimit(List<Long> first, List<Long> second, int limit) {
        LinkedHashSet<Long> mergedIds = new LinkedHashSet<>();
        mergedIds.addAll(first);
        mergedIds.addAll(second);
        return mergedIds.stream().limit(limit).toList();
    }

    private String extractTopKeyword(String query) {
        try {
            TFIDFAnalyzer tfidfAnalyzer = new TFIDFAnalyzer();
            List<Keyword> keywords = tfidfAnalyzer.analyze(query, TFIDF_TOP_N);
            if (keywords == null || keywords.isEmpty()) {
                return null;
            }
            String keyword = keywords.getFirst().getName();
            if (keyword == null) {
                return null;
            }
            String trimmed = keyword.trim();
            return trimmed.isEmpty() ? null : trimmed;
        } catch (Exception ex) {
            log.debug("TFIDF keyword extraction failed, fallback to query only", ex);
            return null;
        }
    }

    private List<ArticleExtraRepository.IdWithDistance> searchIdsByVectorAll(String query) {
        float[] vector = llmProcessService.generateVector(query);
        if (vector == null || vector.length == 0) {
            log.warn("Vector generation failed, fallback to keyword search only");
            return Collections.emptyList();
        }
        return articleExtraRepository.searchIdsByVector(toPgVectorLiteral(vector), VECTOR_SIMILARITY_THRESHOLD, VECTOR_RECALL_LIMIT);
    }

    private List<ArticleExtraRepository.IdWithDistance> searchIdsByVectorInSources(String query, List<Long> sourceIds) {
        float[] vector = llmProcessService.generateVector(query);
        if (vector == null || vector.length == 0) {
            log.warn("Vector generation failed, fallback to keyword search only");
            return Collections.emptyList();
        }
        return articleExtraRepository.searchIdsByVectorInSources(toPgVectorLiteral(vector), sourceIds, VECTOR_SIMILARITY_THRESHOLD, VECTOR_RECALL_LIMIT);
    }

    private List<ArticleExtraRepository.IdWithDistance> searchIdsByVectorInFavorites(String query, Long userId) {
        float[] vector = llmProcessService.generateVector(query);
        if (vector == null || vector.length == 0) {
            log.warn("Vector generation failed, fallback to keyword search only");
            return Collections.emptyList();
        }
        return articleExtraRepository.searchIdsByVectorInFavorites(toPgVectorLiteral(vector), userId, VECTOR_SIMILARITY_THRESHOLD, VECTOR_RECALL_LIMIT);
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