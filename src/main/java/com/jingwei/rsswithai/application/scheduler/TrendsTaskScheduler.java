package com.jingwei.rsswithai.application.scheduler;

import com.jingwei.rsswithai.config.AppConfig;
import com.jingwei.rsswithai.domain.repository.RssSourceRepository;
import com.jingwei.rsswithai.application.service.TrendsAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Component
@Slf4j
@RequiredArgsConstructor
public class TrendsTaskScheduler {

    private final TrendsAnalysisService trendsAnalysisService;
    private final RssSourceRepository rssSourceRepository;
    private final AppConfig appConfig;

    private final AtomicReference<LocalDateTime> lastWordCloudRun = new AtomicReference<>(LocalDateTime.MIN);
    private final AtomicReference<LocalDateTime> lastHotEventsRun = new AtomicReference<>(LocalDateTime.MIN);

    /**
     * Check every 10 minutes if tasks need to run
     */
//    @Scheduled(fixedDelay = 600000) // 10 minutes
    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.DAYS)
    public void scheduleTasks() {
        checkAndRunWordCloud();
        checkAndRunHotEvents();
    }

    private void checkAndRunWordCloud() {
        int frequencyHours = appConfig.getTrendsWordCloudFrequencyHours() != null
                ? appConfig.getTrendsWordCloudFrequencyHours()
                : 6;

        if (shouldRun(lastWordCloudRun.get(), frequencyHours)) {
            log.info("Triggering Word Cloud Task");
            List<Long> sourceIds = rssSourceRepository.findAllIds();
            for (Long sourceId : sourceIds) {
                try {
                    trendsAnalysisService.generateWordCloudForSource(sourceId);
                } catch (Exception e) {
                    log.error("Failed to generate word cloud for source {}", sourceId, e);
                }
            }
            lastWordCloudRun.set(LocalDateTime.now());
        }
    }

    private void checkAndRunHotEvents() {
        int frequencyHours = appConfig.getTrendsHotEventsFrequencyHours() != null
                ? appConfig.getTrendsHotEventsFrequencyHours()
                : 4;

        if (shouldRun(lastHotEventsRun.get(), frequencyHours)) {
            log.info("Triggering Hot Events Task");
            try {
                trendsAnalysisService.generateHotEvents();
                lastHotEventsRun.set(LocalDateTime.now());
            } catch (Exception e) {
                log.error("Failed to generate hot events", e);
            }
        }
    }

    private boolean shouldRun(LocalDateTime lastRun, int frequencyHours) {
        if (frequencyHours <= 0)
            return false; // Disable if 0
        long hoursSinceResult = ChronoUnit.HOURS.between(lastRun, LocalDateTime.now());
        return hoursSinceResult >= frequencyHours;
    }
}