package com.jingwei.rsswithai.application.dto;

import com.jingwei.rsswithai.domain.model.SourceStatus;
import com.jingwei.rsswithai.domain.model.SourceType;
import jakarta.validation.constraints.Min;

/**
 * 更新RSS源请求
 */
public record UpdateRssSourceRequest(
    String name,
    
    String url,

    SourceType type,

    String description,
    
    @Min(value = 1, message = "抓取间隔至少为1分钟")
    Integer fetchIntervalMinutes,
    
    SourceStatus status
) {
}
