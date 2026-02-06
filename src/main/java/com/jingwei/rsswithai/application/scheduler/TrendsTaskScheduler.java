package com.jingwei.rsswithai.application.scheduler;

import com.jingwei.rsswithai.application.service.TrendsAnalysisService;
import com.jingwei.rsswithai.domain.model.RssSource;
import com.jingwei.rsswithai.domain.repository.RssSourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@Slf4j
@RequiredArgsConstructor
public class TrendsTaskScheduler {

    private final TrendsAnalysisService trendsAnalysisService;
    private final RssSourceRepository rssSourceRepository;

    @Scheduled(
            initialDelay = 12,
            timeUnit = TimeUnit.HOURS,
            fixedDelayString = "#{@appConfig.trendsWordCloudFrequencyHours * 60 * 60 * 1000}"
    )
    private void runWordCloudTask() {
        log.info("Triggering Word Cloud Task");
         rssSourceRepository.findAllEnabled();
        for (RssSource source : rssSourceRepository.findAllEnabled()) {
            try {
                trendsAnalysisService.generateWordCloudForSource(source.getId());
            } catch (Exception e) {
                log.error("Failed to generate word cloud for source {}", source.getId(), e);
            }
        }
    }

    @Scheduled(
            initialDelay = 6,
            timeUnit = TimeUnit.HOURS,
            fixedDelayString = "#{@appConfig.trendsHotEventsFrequencyHours * 60 * 60 * 1000}"
    )
    private void runHotEventsTask() {
        log.info("Triggering Hot Events Task");
        try {
            trendsAnalysisService.generateHotEvents();
        } catch (Exception e) {
            log.error("Failed to generate hot events", e);
        }
    }
}