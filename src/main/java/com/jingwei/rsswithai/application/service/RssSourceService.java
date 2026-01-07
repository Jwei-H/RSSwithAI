package com.jingwei.rsswithai.application.service;

import com.jingwei.rsswithai.application.dto.CreateRssSourceRequest;
import com.jingwei.rsswithai.application.dto.RssSourceDTO;
import com.jingwei.rsswithai.application.dto.RssSourceStatsDTO;
import com.jingwei.rsswithai.application.dto.UpdateRssSourceRequest;
import com.jingwei.rsswithai.domain.model.FetchStatus;
import com.jingwei.rsswithai.domain.model.RssSource;
import com.jingwei.rsswithai.domain.model.SourceStatus;
import com.jingwei.rsswithai.domain.repository.ArticleRepository;
import com.jingwei.rsswithai.domain.repository.RssSourceRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RSS源管理服务（Source Manager）
 * 负责维护所有注册的RSS源配置，支持动态启停与参数调整
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RssSourceService {

    private final RssSourceRepository rssSourceRepository;
    private final ArticleRepository articleRepository;

    @Value("${collector.fetch.interval:30}")
    private int defaultFetchInterval;

    /**
     * 添加RSS源
     */
    @Transactional
    public RssSourceDTO createSource(CreateRssSourceRequest request) {
        log.info("创建RSS源: {}", request.name());

        RssSource source = RssSource.builder()
                .name(request.name())
                .url(request.url())
                .type(request.type())
                .description(request.description())
                .fetchIntervalMinutes(request.fetchIntervalMinutes() != null
                        ? request.fetchIntervalMinutes()
                        : defaultFetchInterval)
                .status(SourceStatus.ENABLED)
                .build();

        RssSource saved = rssSourceRepository.save(source);
        log.info("RSS源创建成功: id={}, name={}", saved.getId(), saved.getName());
        return RssSourceDTO.from(saved);
    }

    /**
     * 更新RSS源
     */
    @Transactional
    public RssSourceDTO updateSource(Long id, UpdateRssSourceRequest request) {
        log.info("更新RSS源: id={}", id);

        RssSource source = rssSourceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("RSS源不存在: " + id));

        // 如果URL变更，检查新URL是否已存在
        if (request.url() != null && !request.url().equals(source.getUrl())) {
            source.setUrl(request.url());
        }

        if (request.name() != null) {
            source.setName(request.name());
        }
        if (request.type() != null) {
            source.setType(request.type());
        }
        if (request.description() != null) {
            source.setDescription(request.description());
        }
        if (request.fetchIntervalMinutes() != null) {
            source.setFetchIntervalMinutes(request.fetchIntervalMinutes());
        }
        if (request.status() != null) {
            source.setStatus(request.status());
        }

        RssSource saved = rssSourceRepository.save(source);
        log.info("RSS源更新成功: id={}, name={}", saved.getId(), saved.getName());
        return RssSourceDTO.from(saved);
    }

    /**
     * 删除RSS源
     */
    @Transactional
    public void deleteSource(Long id) {
        log.info("删除RSS源: id={}", id);

        if (!rssSourceRepository.existsById(id)) {
            throw new EntityNotFoundException("RSS源不存在: " + id);
        }
        articleRepository.detachSource(id);
        rssSourceRepository.deleteById(id);
        log.info("RSS源删除成功: id={}", id);
    }

    /**
     * 获取RSS源详情
     */
    public RssSourceDTO getSource(Long id) {
        RssSource source = rssSourceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("RSS源不存在: " + id));
        return RssSourceDTO.from(source);
    }

    /**
     * 获取所有RSS源
     */
    public Page<RssSourceDTO> getAllSources(Pageable pageable) {
        return rssSourceRepository.findAll(pageable)
                .map(RssSourceDTO::from);
    }

    /**
     * 启用RSS源
     */
    @Transactional
    public RssSourceDTO enableSource(Long id) {
        log.info("启用RSS源: id={}", id);

        RssSource source = rssSourceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("RSS源不存在: " + id));

        source.setStatus(SourceStatus.ENABLED);
        RssSource saved = rssSourceRepository.save(source);
        log.info("RSS源已启用: id={}", id);
        return RssSourceDTO.from(saved);
    }

    /**
     * 禁用RSS源
     */
    @Transactional
    public RssSourceDTO disableSource(Long id) {
        log.info("禁用RSS源: id={}", id);

        RssSource source = rssSourceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("RSS源不存在: " + id));

        source.setStatus(SourceStatus.DISABLED);
        RssSource saved = rssSourceRepository.save(source);
        log.info("RSS源已禁用: id={}", id);
        return RssSourceDTO.from(saved);
    }

    /**
     * 获取RSS源统计信息
     */
    public RssSourceStatsDTO getStats() {
        long total = rssSourceRepository.count();
        List<Object[]> counts = rssSourceRepository.countByLastFetchStatus();
        Map<String, Long> statusCounts = new HashMap<>();

        // Initialize with 0 for all statuses
        for (FetchStatus status : FetchStatus.values()) {
            statusCounts.put(status.name(), 0L);
        }

        for (Object[] row : counts) {
            statusCounts.put(((FetchStatus) row[0]).name(), (Long) row[1]);
        }
        return new RssSourceStatsDTO(total, statusCounts);
    }
}