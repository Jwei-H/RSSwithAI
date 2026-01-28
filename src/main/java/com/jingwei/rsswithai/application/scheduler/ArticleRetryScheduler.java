package com.jingwei.rsswithai.application.scheduler;

import com.jingwei.rsswithai.application.Event.ArticleProcessEvent;
import com.jingwei.rsswithai.application.service.LlmProcessService;
import com.jingwei.rsswithai.domain.model.AnalysisStatus;
import com.jingwei.rsswithai.domain.repository.ArticleExtraRepository;
import com.jingwei.rsswithai.domain.repository.ArticleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
@RequiredArgsConstructor
public class ArticleRetryScheduler {

    private final ArticleRepository articleRepository;
    private final ArticleExtraRepository articleExtraRepository;
    private final LlmProcessService llmProcessService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 每小时检查一次过去7天内没有生成extra数据的文章，并重新触发处理事件
     */
    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.HOURS)
    public void retryMissingArticleExtras() {
        log.info("Starting scheduled check for articles missing extra data...");
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        List<Long> articleIds = articleRepository.findArticleIdsWithoutExtraSince(sevenDaysAgo);
        List<Long> failedArticleIds = articleExtraRepository.findArticleIdsByStatusSince(
                AnalysisStatus.FAILED, sevenDaysAgo);

        if (articleIds.isEmpty() && failedArticleIds.isEmpty()) {
            log.info("No articles found missing extra data in the last 7 days.");
            return;
        }

        for (Long articleId : articleIds) {
            try {
                eventPublisher.publishEvent(new ArticleProcessEvent(this, articleId));
                log.debug("Republished ArticleProcessEvent for articleId: {}", articleId);
            } catch (Exception e) {
                log.error("Failed to republish event for articleId: {}", articleId, e);
            }
        }


        for (Long articleId : failedArticleIds) {
            try {
                llmProcessService.regenerateArticleExtra(articleId);
                log.debug("Regenerated ArticleExtra for articleId: {}", articleId);
            } catch (Exception e) {
                log.error("Failed to regenerate ArticleExtra for articleId: {}", articleId, e);
            }
        }
    }
}