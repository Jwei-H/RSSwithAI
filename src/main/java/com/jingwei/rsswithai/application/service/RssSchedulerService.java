package com.jingwei.rsswithai.application.service;

import com.jingwei.rsswithai.domain.model.RssSource;
import com.jingwei.rsswithai.domain.repository.RssSourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * RSS调度器服务（Scheduler）
 * 基于配置的抓取频率触发定时任务，驱动抓取执行器工作
 * 支持多源并发抓取（使用虚拟线程）
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RssSchedulerService {

    private final RssSourceRepository rssSourceRepository;
    private final RssFetcherService rssFetcherService;

    // 使用虚拟线程执行器实现并发抓取
    private final ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
    
    // 防止任务重叠执行
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    /**
     * 定时任务：每分钟检查需要抓取的源
     * 实际抓取由各源的fetchIntervalMinutes控制
     */
    @Scheduled(fixedRate = 60000) // 每分钟执行一次
    public void scheduledFetch() {
        if (!isRunning.compareAndSet(false, true)) {
            log.debug("上一轮抓取任务尚未完成，跳过本次调度");
            return;
        }

        try {
            log.debug("开始检查需要抓取的RSS源...");
            
            // 获取所有启用的源
            List<RssSource> enabledSources = rssSourceRepository.findAllEnabled();
            
            // 筛选出需要抓取的源
            List<RssSource> sourcesToFetch = enabledSources.stream()
                .filter(RssSource::shouldFetch)
                .toList();

            if (sourcesToFetch.isEmpty()) {
                log.debug("当前没有需要抓取的RSS源");
                return;
            }

            log.info("本次需要抓取的RSS源数量: {}", sourcesToFetch.size());

            // 使用虚拟线程并发抓取
            sourcesToFetch.forEach(source -> 
                virtualThreadExecutor.submit(() -> {
                    try {
                        rssFetcherService.fetchSource(source);
                    } catch (Exception e) {
                        log.error("抓取RSS源异常: id={}, name={}, error={}", 
                            source.getId(), source.getName(), e.getMessage(), e);
                    }
                })
            );

        } finally {
            isRunning.set(false);
        }
    }

    /**
     * 手动触发抓取所有启用的源
     */
    public void fetchAllEnabled() {
        log.info("手动触发抓取所有启用的RSS源");
        
        List<RssSource> enabledSources = rssSourceRepository.findAllEnabled();
        
        enabledSources.forEach(source -> 
            virtualThreadExecutor.submit(() -> {
                try {
                    rssFetcherService.fetchSource(source);
                } catch (Exception e) {
                    log.error("抓取RSS源异常: id={}, name={}, error={}", 
                        source.getId(), source.getName(), e.getMessage(), e);
                }
            })
        );
    }

    /**
     * 手动触发抓取指定的源
     */
    public void fetchSource(Long sourceId) {
        log.info("手动触发抓取RSS源: id={}", sourceId);
        
        virtualThreadExecutor.submit(() -> {
            try {
                rssFetcherService.fetchSource(sourceId);
            } catch (Exception e) {
                log.error("抓取RSS源异常: id={}, error={}", sourceId, e.getMessage(), e);
            }
        });
    }
}
