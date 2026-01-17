package com.jingwei.rsswithai.application.scheduler;

import com.jingwei.rsswithai.application.Event.ArticleProcessEvent;
import com.jingwei.rsswithai.domain.repository.ArticleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class ArticleRetryScheduler {

    private final ArticleRepository articleRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 每小时检查一次过去7天内没有生成extra数据的文章，并重新触发处理事件
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void retryMissingArticleExtras() {
        log.info("Starting scheduled check for articles missing extra data...");
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        List<Long> articleIds = articleRepository.findArticleIdsWithoutExtraSince(sevenDaysAgo);

        if (articleIds.isEmpty()) {
            log.info("No articles found missing extra data in the last 7 days.");
            return;
        }

        log.info("Found {} articles without extra data in the last 7 days. Republishing events...", articleIds.size());

        for (Long articleId : articleIds) {
            try {
                eventPublisher.publishEvent(new ArticleProcessEvent(this, articleId));
                log.debug("Republished ArticleProcessEvent for articleId: {}", articleId);
            } catch (Exception e) {
                log.error("Failed to republish event for articleId: {}", articleId, e);
            }
        }
    }
}
