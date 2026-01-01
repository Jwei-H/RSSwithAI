package com.jingwei.rsswithai.interfaces.admin;

import com.jingwei.rsswithai.application.dto.CreateRssSourceRequest;
import com.jingwei.rsswithai.application.dto.RssSourceDTO;
import com.jingwei.rsswithai.application.dto.RssSourceStatsDTO;
import com.jingwei.rsswithai.application.dto.UpdateRssSourceRequest;
import com.jingwei.rsswithai.application.service.RssSchedulerService;
import com.jingwei.rsswithai.application.service.RssSourceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * RSS源管理REST控制器
 * 提供RSS源的CRUD操作和手动触发抓取的API
 */
@RestController
@RequestMapping("/api/admin/v1/rss-sources")
@RequiredArgsConstructor
@Slf4j
public class RssSourceController {

    private final RssSourceService rssSourceService;
    private final RssSchedulerService rssSchedulerService;

    /**
     * 获取所有RSS源
     * GET /api/v1/rss-sources
     */
    @GetMapping
    public ResponseEntity<Page<RssSourceDTO>> getAllSources(@PageableDefault(size = 20) Pageable pageable) {
        log.debug("获取所有RSS源");
        Page<RssSourceDTO> sources = rssSourceService.getAllSources(pageable);
        return ResponseEntity.ok(sources);
    }

    /**
     * 获取RSS源统计信息
     * GET /api/admin/v1/rss-sources/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<RssSourceStatsDTO> getStats() {
        log.debug("获取RSS源统计信息");
        RssSourceStatsDTO stats = rssSourceService.getStats();
        return ResponseEntity.ok(stats);
    }

    /**
     * 获取单个RSS源详情
     * GET /api/v1/rss-sources/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<RssSourceDTO> getSource(@PathVariable Long id) {
        log.debug("获取RSS源详情: id={}", id);
        RssSourceDTO source = rssSourceService.getSource(id);
        return ResponseEntity.ok(source);
    }

    /**
     * 创建RSS源
     * POST /api/v1/rss-sources
     */
    @PostMapping
    public ResponseEntity<RssSourceDTO> createSource(@Valid @RequestBody CreateRssSourceRequest request) {
        log.info("创建RSS源: name={}, url={}", request.name(), request.url());
        RssSourceDTO created = rssSourceService.createSource(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * 更新RSS源
     * PUT /api/v1/rss-sources/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<RssSourceDTO> updateSource(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRssSourceRequest request) {
        log.info("更新RSS源: id={}", id);
        RssSourceDTO updated = rssSourceService.updateSource(id, request);
        return ResponseEntity.ok(updated);
    }

    /**
     * 删除RSS源
     * DELETE /api/v1/rss-sources/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSource(@PathVariable Long id) {
        log.info("删除RSS源: id={}", id);
        rssSourceService.deleteSource(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * 启用RSS源
     * POST /api/v1/rss-sources/{id}/enable
     */
    @PostMapping("/{id}/enable")
    public ResponseEntity<RssSourceDTO> enableSource(@PathVariable Long id) {
        log.info("启用RSS源: id={}", id);
        RssSourceDTO source = rssSourceService.enableSource(id);
        return ResponseEntity.ok(source);
    }

    /**
     * 禁用RSS源
     * POST /api/v1/rss-sources/{id}/disable
     */
    @PostMapping("/{id}/disable")
    public ResponseEntity<RssSourceDTO> disableSource(@PathVariable Long id) {
        log.info("禁用RSS源: id={}", id);
        RssSourceDTO source = rssSourceService.disableSource(id);
        return ResponseEntity.ok(source);
    }

    /**
     * 手动触发抓取指定RSS源
     * POST /api/v1/rss-sources/{id}/fetch
     */
    @PostMapping("/{id}/fetch")
    public ResponseEntity<Void> fetchSource(@PathVariable Long id) {
        log.info("手动触发抓取RSS源: id={}", id);
        rssSchedulerService.fetchSource(id);
        return ResponseEntity.accepted().build();
    }

    /**
     * 手动触发抓取所有启用的RSS源
     * POST /api/v1/rss-sources/fetch-all
     */
    @PostMapping("/fetch-all")
    public ResponseEntity<Void> fetchAllSources() {
        log.info("手动触发抓取所有RSS源");
        rssSchedulerService.fetchAllEnabled();
        return ResponseEntity.accepted().build();
    }
}