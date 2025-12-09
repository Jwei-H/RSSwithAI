package com.jingwei.rsswithai.application.service;

import com.jingwei.rsswithai.application.dto.CreateRssSourceRequest;
import com.jingwei.rsswithai.application.dto.RssSourceDTO;
import com.jingwei.rsswithai.application.dto.UpdateRssSourceRequest;
import com.jingwei.rsswithai.domain.model.RssSource;
import com.jingwei.rsswithai.domain.model.SourceStatus;
import com.jingwei.rsswithai.domain.repository.RssSourceRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * RSS源管理服务（Source Manager）
 * 负责维护所有注册的RSS源配置，支持动态启停与参数调整
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RssSourceService {

    private final RssSourceRepository rssSourceRepository;

    @Value("${collector.fetch.interval:30}")
    private int defaultFetchInterval;

    /**
     * 添加RSS源
     */
    @Transactional
    public RssSourceDTO createSource(CreateRssSourceRequest request) {
        log.info("创建RSS源: {}", request.name());
        
        // 检查URL是否已存在
        if (rssSourceRepository.existsByUrl(request.url())) {
            throw new IllegalArgumentException("该URL已存在: " + request.url());
        }

        RssSource source = RssSource.builder()
            .name(request.name())
            .url(request.url())
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
            if (rssSourceRepository.existsByUrl(request.url())) {
                throw new IllegalArgumentException("该URL已存在: " + request.url());
            }
            source.setUrl(request.url());
        }

        if (request.name() != null) {
            source.setName(request.name());
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
        
        rssSourceRepository.deleteById(id);
        log.info("RSS源删除成功: id={}", id);
    }

    /**
     * 获取RSS源详情
     */
    @Transactional(readOnly = true)
    public RssSourceDTO getSource(Long id) {
        RssSource source = rssSourceRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("RSS源不存在: " + id));
        return RssSourceDTO.from(source);
    }

    /**
     * 获取所有RSS源
     */
    @Transactional(readOnly = true)
    public List<RssSourceDTO> getAllSources() {
        return rssSourceRepository.findAll().stream()
            .map(RssSourceDTO::from)
            .toList();
    }

    /**
     * 获取所有启用的RSS源
     */
    @Transactional(readOnly = true)
    public List<RssSourceDTO> getEnabledSources() {
        return rssSourceRepository.findAllEnabled().stream()
            .map(RssSourceDTO::from)
            .toList();
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
}
