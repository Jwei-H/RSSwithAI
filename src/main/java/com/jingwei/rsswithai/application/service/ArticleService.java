package com.jingwei.rsswithai.application.service;

import com.jingwei.rsswithai.application.dto.ArticleDTO;
import com.jingwei.rsswithai.application.dto.ArticleExtraDTO;
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
    public ArticleDTO getArticle(Long id) {
        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("文章不存在: " + id));
        return ArticleDTO.from(article);
    }

    /**
     * 根据RSS源ID分页获取文章
     */
    public Page<ArticleDTO> getArticlesBySource(Long sourceId, Pageable pageable) {
        return articleRepository.findBySourceIdOrderByPubDateDesc(sourceId, pageable)
                .map(ArticleDTO::fromBasic);
    }

    /**
     * 分页获取所有文章
     */
    public Page<ArticleDTO> getAllArticles(Pageable pageable) {
        return articleRepository.findAllByOrderByPubDateDesc(pageable)
                .map(ArticleDTO::fromBasic);
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