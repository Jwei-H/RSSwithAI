package com.jingwei.rsswithai.interfaces.front;

import com.jingwei.rsswithai.application.dto.ArticleDetailDTO;
import com.jingwei.rsswithai.application.dto.ArticleExtraDTO;
import com.jingwei.rsswithai.application.dto.ArticleFeedDTO;
import com.jingwei.rsswithai.application.service.ArticleService;
import com.jingwei.rsswithai.domain.model.AnalysisStatus;
import com.jingwei.rsswithai.interfaces.context.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/front/v1/articles")
@RequiredArgsConstructor
public class FrontArticleController {

    private final ArticleService articleService;

    @GetMapping("/{id}")
    public ResponseEntity<ArticleDetailDTO> getArticle(@PathVariable Long id) {
        ArticleDetailDTO article = articleService.getArticle(id, UserContext.currentUserIdOrNull());
        return ResponseEntity.ok(article);
    }

    @GetMapping("/{id}/extra")
    public ResponseEntity<ArticleExtraDTO> getArticleExtra(@PathVariable Long id) {
        ArticleExtraDTO extra = articleService.getArticleExtra(id);
        if (extra == null || AnalysisStatus.FAILED == extra.status()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(extra);
    }

    @GetMapping("/search")
    public ResponseEntity<List<ArticleFeedDTO>> searchArticles(
            @RequestParam("query") String query,
            @RequestParam(value = "searchScope", defaultValue = "ALL") SearchScope searchScope,
            @RequestParam(value = "sourceId", required = false) Long sourceId) {
        List<ArticleFeedDTO> results = articleService.searchArticles(query, searchScope, sourceId, UserContext.currentUserIdOrNull());
        return ResponseEntity.ok(results);
    }

    @PostMapping("/{id}/favorite")
    public ResponseEntity<Void> favoriteArticle(@PathVariable Long id) {
        articleService.favoriteArticle(UserContext.currentUserId(), id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/favorite")
    public ResponseEntity<Void> unfavoriteArticle(@PathVariable Long id) {
        articleService.unfavoriteArticle(UserContext.currentUserId(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/favorites")
    public ResponseEntity<Page<ArticleFeedDTO>> listFavoriteArticles(
            @PageableDefault(size = 10, sort = "pubDate", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<ArticleFeedDTO> page = articleService.listFavoriteArticles(UserContext.currentUserId(), pageable);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/{id}/recommendations")
    public ResponseEntity<List<ArticleFeedDTO>> recommendArticles(@PathVariable Long id) {
        List<ArticleFeedDTO> results = articleService.recommendArticles(id);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/source/{sourceId}")
    public ResponseEntity<Page<ArticleFeedDTO>> getArticlesBySource(
            @PathVariable Long sourceId,
            @PageableDefault(size = 10, sort = "pubDate", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<ArticleFeedDTO> page = articleService.getArticleFeedsBySource(sourceId, pageable);
        return ResponseEntity.ok(page);
    }

    public enum SearchScope {
        ALL,
        SUBSCRIBED,
        FAVORITE
    }
}