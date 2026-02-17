package com.jingwei.rsswithai.application.scheduler;

import com.jingwei.rsswithai.application.service.TrendsAnalysisService;
import com.jingwei.rsswithai.config.AppConfig;
import com.jingwei.rsswithai.domain.model.RssSource;
import com.jingwei.rsswithai.domain.repository.RssSourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
@Slf4j
@RequiredArgsConstructor
public class TrendsTaskScheduler implements SchedulingConfigurer {

    private final TrendsAnalysisService trendsAnalysisService;
    private final RssSourceRepository rssSourceRepository;
    private final AppConfig appConfig;

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.addTriggerTask(
                this::runWordCloudTask,
                triggerContext -> {
                    Integer frequencyHours = appConfig.getTrendsWordCloudFrequencyHours();
                    long delayHours = (frequencyHours == null || frequencyHours <= 0) ? 24L : frequencyHours;
                    Instant lastCompletion = triggerContext.lastCompletion();
                    if (lastCompletion == null) {
                        return Instant.now().plus(Duration.ofHours(12));
                    }
                    return lastCompletion.plus(Duration.ofHours(delayHours));
                }
        );

        taskRegistrar.addTriggerTask(
                this::runHotEventsTask,
                triggerContext -> {
                    Integer frequencyHours = appConfig.getTrendsHotEventsFrequencyHours();
                    long delayHours = (frequencyHours == null || frequencyHours <= 0) ? 12L : frequencyHours;
                    Instant lastCompletion = triggerContext.lastCompletion();
                    if (lastCompletion == null) {
                        return Instant.now().plus(Duration.ofHours(6));
                    }
                    return lastCompletion.plus(Duration.ofHours(delayHours));
                }
        );
    }

    private void runWordCloudTask() {
        log.info("Triggering Word Cloud Task");
        for (RssSource source : rssSourceRepository.findAllEnabled()) {
            try {
                trendsAnalysisService.generateWordCloudForSource(source.getId());
            } catch (Exception e) {
                log.error("Failed to generate word cloud for source {}", source.getId(), e);
            }
        }
    }

    private void runHotEventsTask() {
        log.info("Triggering Hot Events Task");
        try {
            trendsAnalysisService.generateHotEvents();
        } catch (Exception e) {
            log.error("Failed to generate hot events", e);
        }
    }
}