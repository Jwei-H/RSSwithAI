package com.jingwei.rsswithai.application.service;

import com.jingwei.rsswithai.application.dto.ArticleDTO;
import com.jingwei.rsswithai.domain.model.Article;
import com.jingwei.rsswithai.domain.repository.ArticleRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    /**
     * 获取文章详情
     */
    @Transactional(readOnly = true)
    public ArticleDTO getArticle(Long id) {
        Article article = articleRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("文章不存在: " + id));
        return ArticleDTO.from(article);
    }

    /**
     * 根据RSS源ID分页获取文章
     */
    @Transactional(readOnly = true)
    public Page<ArticleDTO> getArticlesBySource(Long sourceId, Pageable pageable) {
        return articleRepository.findBySourceIdOrderByPubDateDesc(sourceId, pageable)
            .map(ArticleDTO::from);
    }

    /**
     * 分页获取所有文章
     */
    @Transactional(readOnly = true)
    public Page<ArticleDTO> getAllArticles(Pageable pageable) {
        return articleRepository.findAll(pageable)
            .map(ArticleDTO::from);
    }
}
