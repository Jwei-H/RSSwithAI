package com.jingwei.rsswithai.application.service;

import com.jingwei.rsswithai.application.Event.ArticleProcessEvent;
import com.jingwei.rsswithai.config.AppConfig;
import com.jingwei.rsswithai.domain.model.Article;
import com.jingwei.rsswithai.domain.model.FetchStatus;
import com.jingwei.rsswithai.domain.model.RssSource;
import com.jingwei.rsswithai.domain.model.SourceType;
import com.jingwei.rsswithai.domain.repository.RssSourceRepository;
import com.jingwei.rsswithai.utils.RssUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
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
    private final ArticleService articleService;
    private final AppConfig appConfig;
    private final ApplicationEventPublisher eventPublisher;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * 抓取单个RSS源
     *
     * @return 新抓取的文章数量
     */
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
    public int fetchSource(RssSource source) {
        boolean isFirstFetch = source.getLastFetchStatus() == FetchStatus.NEVER;
        // 如果返回 0，说明该源已经在 NEW/FETCHING 状态（被其他线程或节点处理中），直接跳过
        int updatedRows = rssSourceRepository.compareAndSetFetching(source.getId());
        if (updatedRows == 0) {
            log.info("RSS源正在抓取中，跳过本次执行: id={}, name={}", source.getId(), source.getName());
            return 0;
        }

        source.markAsFetching();

        log.info("开始抓取RSS源: id={}, name={}, url={}", source.getId(), source.getName(), source.getUrl());

        int retryCount = 0;
        Exception lastException = null;
        int maxRetries = appConfig.getCollectorFetchMaxRetries();
        boolean success = false; // 标记最终是否成功

        try {
            while (retryCount < maxRetries) {
                try {
                    String fetchUrl = getFetchUrl(source);
                    String content = fetchRssContent(fetchUrl);

                    // 如果是首次抓取且源名称为空，则尝试从RSS中提取元信息
                    if (isFirstFetch) {
                        RssUtils.ChannelInfo channelInfo = RssUtils.parseChannelInfo(content);
                        if (channelInfo != null) {
                            if (channelInfo.title() != null && !channelInfo.title().isBlank() && (source.getName() == null || source.getName().isBlank())) {
                                source.setName(channelInfo.title());
                                log.info("从RSS中提取到源名称: {}", channelInfo.title());
                            }
                            if (channelInfo.description() != null && !channelInfo.description().isBlank()) {
                                source.setDescription(channelInfo.description());
                                log.info("从RSS中提取到源描述: {}", channelInfo.description());
                            }
                            if (channelInfo.link() != null && !channelInfo.link().isBlank()) {
                                source.setLink(channelInfo.link());
                                log.info("从RSS中提取到源链接: {}", channelInfo.link());
                            }
                        }
                    }

                    List<RssUtils.ParsedItem> items = RssUtils.parseItems(content, source);

                    int savedCount = 0;
                    LocalDateTime latestArticlePubDate = source.getLatestArticlePubDate();
                    for (RssUtils.ParsedItem item : items) {
                        Article article = RssUtils.buildArticle(item, source);
                        if (article == null) {
                            continue;
                        }

                        LocalDateTime pubDate = article.getPubDate();
                        if (pubDate != null && (latestArticlePubDate == null || pubDate.isAfter(latestArticlePubDate))) {
                            latestArticlePubDate = pubDate;
                        }

                        if (item.hasIdentity() && articleService.existsBySourceAndGuidOrLink(
                                source.getId(), item.guid(), item.link())) {
                            continue;
                        }

                        Article savedArticle = articleService.saveArticleIfNotExists(article);
                        if (savedArticle != null) {
                            log.debug("保存新文章: title={}, guid={}", article.getTitle(), article.getGuid());
                            eventPublisher.publishEvent(new ArticleProcessEvent(this, savedArticle.getId()));
                            savedCount++;
                        }
                    }

                    source.setLatestArticlePubDate(latestArticlePubDate);
                    source.recordFetchSuccess();
                    rssSourceRepository.save(source);

                    log.info("RSS源抓取成功: id={}, name={}, 新增文章数={}",
                            source.getId(), source.getName(), savedCount);

                    success = true;
                    return savedCount;

                } catch (Exception e) {
                    lastException = e;
                    retryCount++;
                    log.warn("RSS源抓取失败，第{}次重试: id={}, name={}, error={}",
                            retryCount, source.getId(), source.getName(), e.getMessage());

                    if (retryCount < maxRetries) {
                        try {
                            Thread.sleep(2000L * retryCount);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
        } finally {
            // 兜底处理：如果未成功（重试耗尽 或 发生未捕获的RuntimeException），强制标记为失败
            if (!success) {
                String errorMsg = lastException != null ? lastException.getMessage() : "未知错误或被中断";
                source.recordFetchFailure(errorMsg);
                rssSourceRepository.save(source);
                log.error("RSS源抓取最终失败: id={}, name={}, error={}",
                        source.getId(), source.getName(), errorMsg);
            }
        }

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

}