package com.jingwei.rsswithai.interfaces.admin;

import com.jingwei.rsswithai.application.dto.ArticleDTO;
import com.jingwei.rsswithai.application.dto.ArticleExtraDTO;
import com.jingwei.rsswithai.application.dto.ArticleStatsDTO;
import com.jingwei.rsswithai.application.service.ArticleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文章REST控制器
 * 提供文章的查询API
 */
@RestController
@RequestMapping("/api/admin/v1/articles")
@RequiredArgsConstructor
@Slf4j
public class ArticleController {

    private final ArticleService articleService;

    /**
     * 获取所有文章（分页）
     * GET /api/v1/articles?page=0&size=20&searchWord=keyword
     */
    @GetMapping
    public ResponseEntity<Page<ArticleDTO>> getArticles(
            @org.springframework.web.bind.annotation.RequestParam(required = false) String searchWord,
            @PageableDefault(size = 20) Pageable pageable) {
        log.debug("获取所有文章, page={}, size={}, searchWord={}", pageable.getPageNumber(), pageable.getPageSize(), searchWord);
        Page<ArticleDTO> articles = articleService.getArticles(searchWord, pageable);
        return ResponseEntity.ok(articles);
    }

    /**
     * 获取文章统计信息
     * GET /api/admin/v1/articles/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<ArticleStatsDTO> getStats() {
        log.debug("获取文章统计信息");
        ArticleStatsDTO stats = articleService.getStats();
        return ResponseEntity.ok(stats);
    }

    /**
     * 获取单个文章详情
     * GET /api/v1/articles/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ArticleDTO> getArticle(@PathVariable Long id) {
        log.debug("获取文章详情: id={}", id);
        ArticleDTO article = articleService.getArticle(id);
        return ResponseEntity.ok(article);
    }

    /**
     * 获取指定RSS源的文章（分页）
     * GET /api/v1/articles/source/{sourceId}?page=0&size=20&searchWord=keyword
     */
    @GetMapping("/source/{sourceId}")
    public ResponseEntity<Page<ArticleDTO>> getArticlesBySource(
            @PathVariable Long sourceId,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String searchWord,
            @PageableDefault(size = 20) Pageable pageable) {
        log.debug("获取RSS源文章: sourceId={}, page={}, size={}, searchWord={}",
                sourceId, pageable.getPageNumber(), pageable.getPageSize(), searchWord);
        Page<ArticleDTO> articles = articleService.getArticlesBySource(sourceId, searchWord, pageable);
        return ResponseEntity.ok(articles);
    }

    /**
     * 获取文章增强信息
     * GET /api/v1/articles/{id}/extra
     */
    @GetMapping("/{id}/extra")
    public ResponseEntity<ArticleExtraDTO> getArticleExtra(@PathVariable Long id) {
        log.debug("获取文章增强信息: articleId={}", id);
        ArticleExtraDTO extra = articleService.getArticleExtra(id);
        if (extra == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(extra);
    }
}