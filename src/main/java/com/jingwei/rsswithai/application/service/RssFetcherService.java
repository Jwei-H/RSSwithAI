package com.jingwei.rsswithai.application.service;

import com.jingwei.rsswithai.domain.model.Article;
import com.jingwei.rsswithai.domain.model.RssSource;
import com.jingwei.rsswithai.domain.repository.ArticleRepository;
import com.jingwei.rsswithai.domain.repository.RssSourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.jingwei.rsswithai.utils.RssUtils;

/**
 * RSS抓取执行器服务（Fetcher）
 * 负责执行实际的HTTP请求、XML解析、内容提取，并将有效条目封装为标准化格式
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RssFetcherService {

    
    private final RssSourceRepository rssSourceRepository;
    private final ArticleRepository articleRepository;
    // 使用虚拟线程的HttpClient
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    @Value("${collector.fetch.timeout:30}")
    private int fetchTimeout;
    @Value("${collector.fetch.max-retries:3}")
    private int maxRetries;

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

        while (retryCount < maxRetries) {
            try {
                // 执行HTTP请求获取RSS内容
                String rssContent = fetchRssContent(source.getUrl());

                // 解析RSS内容
                List<Article> articles = parseRssContent(rssContent, source);

                // 去重并保存
                int savedCount = saveArticlesWithDeduplication(articles);

                // 记录成功
                source.recordFetchSuccess();
                rssSourceRepository.save(source);

                log.info("RSS源抓取成功: id={}, name={}, 新文章数={}",
                        source.getId(), source.getName(), savedCount);
                return savedCount;

            } catch (Exception e) {
                lastException = e;
                retryCount++;
                log.warn("RSS源抓取失败，重试 {}/{}: id={}, error={}",
                        retryCount, maxRetries, source.getId(), e.getMessage());

                if (retryCount < maxRetries) {
                    try {
                        Thread.sleep(1000L * retryCount); // 指数退避
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

    /**
     * 执行HTTP请求获取RSS内容
     */
    private String fetchRssContent(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(fetchTimeout))
                .header("User-Agent", "RSSwithAI/1.0")
                .header("Accept", "application/rss+xml, application/xml, text/xml, */*")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("HTTP请求失败，状态码: " + response.statusCode());
        }

        return response.body();
    }

    /**
     * 解析RSS内容
     */
    private List<Article> parseRssContent(String content, RssSource source) throws Exception {
        List<Article> articles = new ArrayList<>();

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // 安全设置，防止XXE攻击
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new InputSource(new StringReader(content)));
        doc.getDocumentElement().normalize();

        // 尝试解析RSS 2.0格式
        NodeList itemList = doc.getElementsByTagName("item");
        if (itemList.getLength() == 0) {
            // 尝试解析Atom格式
            itemList = doc.getElementsByTagName("entry");
        }

        for (int i = 0; i < itemList.getLength(); i++) {
            Element item = (Element) itemList.item(i);
            Article article = RssUtils.buildArticleFromElement(item, source);
            if (article != null) {
                articles.add(article);
            }
        }

        log.debug("解析RSS内容完成: source={}, 文章数={}", source.getName(), articles.size());
        return articles;
    }

    /**
     * 解析单个条目
     */
    

    /**
     * 去重并保存文章
     */
    private int saveArticlesWithDeduplication(List<Article> articles) {
        int savedCount = 0;

        for (Article article : articles) {
            String guid = article.getGuid();
            String link = article.getLink();

            boolean exists = false;
            if (guid != null && !guid.isBlank()) {
                exists = articleRepository.existsByGuid(guid);
            }
            if (!exists) {
                exists = articleRepository.existsByLink(link);
            }

            if (!exists) {
                articleRepository.save(article);
                savedCount++;
            }
        }

        return savedCount;
    }
}
