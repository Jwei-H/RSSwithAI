package com.jingwei.rsswithai.interfaces.front;

import com.jingwei.rsswithai.application.dto.ArticleDetailDTO;
import com.jingwei.rsswithai.application.dto.ArticleExtraDTO;
import com.jingwei.rsswithai.application.service.ArticleService;
import com.jingwei.rsswithai.domain.model.AnalysisStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/front/v1/articles")
@RequiredArgsConstructor
public class FrontArticleController {

    private final ArticleService articleService;

    @GetMapping("/{id}")
    public ResponseEntity<ArticleDetailDTO> getArticle(@PathVariable Long id) {
        ArticleDetailDTO article = articleService.getArticle(id);
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
}