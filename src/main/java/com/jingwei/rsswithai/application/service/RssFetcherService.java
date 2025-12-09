package com.jingwei.rsswithai.application.service;

import com.jingwei.rsswithai.config.AppConfig;
import com.jingwei.rsswithai.domain.model.Article;
import com.jingwei.rsswithai.domain.model.RssSource;
import com.jingwei.rsswithai.domain.model.SourceType;
import com.jingwei.rsswithai.domain.repository.ArticleRepository;
import com.jingwei.rsswithai.domain.repository.RssSourceRepository;
import com.jingwei.rsswithai.utils.RssUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * RSS抓取执行器服务（Fetcher）
 * 负责执行实际的HTTP请求，并调用RssUtils进行统一解析
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RssFetcherService {

    private final RssSourceRepository rssSourceRepository;
    private final ArticleRepository articleRepository;
    private final AppConfig appConfig;

    // 使用虚拟线程的HttpClient
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * 抓取单个RSS源
     *
     * @return 新抓取的文章数量
     */
    @Transactional
    public int fetchSource(Long sourceId) {
        RssSource source = rssSourceRepository.findById(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("RSS源不存在: " + sourceId));
        return fetchSource(source);
    }

    /**
     * 抓取单个RSS源
     *
     * @return 新抓取的文章数量
     */
    @Transactional
    public int fetchSource(RssSource source) {
        log.info("开始抓取RSS源: id={}, name={}, url={}", source.getId(), source.getName(), source.getUrl());

        // 标记为正在抓取
        source.markAsFetching();
        rssSourceRepository.save(source);

        int retryCount = 0;
        Exception lastException = null;
        int maxRetries = appConfig.getCollectorFetchMaxRetries();

        while (retryCount < maxRetries) {
            try {
                // 1. 执行HTTP请求获取RSS内容
                String fetchUrl = getFetchUrl(source);
                String content = fetchRssContent(fetchUrl);

                // 2. 使用RssUtils统一解析（自动检测RSS/Atom格式）
                List<Article> articles = RssUtils.parseContent(content, source);

                // 3. 去重并保存文章
                int savedCount = saveArticlesWithDeduplication(articles);

                // 4. 记录抓取成功
                source.recordFetchSuccess();
                rssSourceRepository.save(source);

                log.info("RSS源抓取成功: id={}, name={}, 解析文章数={}, 新增文章数={}",
                        source.getId(), source.getName(), articles.size(), savedCount);
                return savedCount;

            } catch (Exception e) {
                lastException = e;
                retryCount++;
                log.warn("RSS源抓取失败，第{}次重试: id={}, name={}, error={}",
                        retryCount, source.getId(), source.getName(), e.getMessage());

                if (retryCount < maxRetries) {
                    try {
                        // 指数退避重试
                        Thread.sleep(2000L * retryCount);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        // 所有重试都失败
        String errorMsg = lastException != null ? lastException.getMessage() : "未知错误";
        source.recordFetchFailure(errorMsg);
        rssSourceRepository.save(source);
        log.error("RSS源抓取最终失败: id={}, name={}, error={}",
                source.getId(), source.getName(), errorMsg);
        return 0;
    }

    private String getFetchUrl(RssSource source) {
        if (source.getType() == SourceType.RSSHUB) {
            String route = source.getUrl();
            if (!route.startsWith("/")) {
                route = "/" + route;
            }
            return appConfig.getRsshubHost() + route;
        }
        return source.getUrl();
    }

    /**
     * 执行HTTP请求获取RSS内容
     */
    private String fetchRssContent(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(appConfig.getCollectorFetchTimeout()))
                .header("User-Agent", "RSSwithAI/1.0")
                .header("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml, */*")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("HTTP请求失败，状态码: " + response.statusCode());
        }

        return response.body();
    }

    /**
     * 去重并保存文章
     */
    private int saveArticlesWithDeduplication(List<Article> articles) {
        int savedCount = 0;

        for (Article article : articles) {
            String guid = article.getGuid();
            String link = article.getLink();

            // 检查是否已存在（基于guid或link去重）
            boolean exists = false;
            if (guid != null && !guid.isBlank()) {
                exists = articleRepository.existsByGuid(guid);
            }
            if (!exists && link != null && !link.isBlank()) {
                exists = articleRepository.existsByLink(link);
            }

            if (!exists) {
                articleRepository.save(article);
                savedCount++;
                log.debug("保存新文章: title={}, guid={}", article.getTitle(), guid);
            } else {
                log.debug("文章已存在，跳过: guid={}, link={}", guid, link);
            }
        }

        return savedCount;
    }
}