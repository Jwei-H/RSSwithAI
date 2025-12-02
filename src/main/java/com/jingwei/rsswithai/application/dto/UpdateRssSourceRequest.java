package com.jingwei.rsswithai.application.dto;

import com.jingwei.rsswithai.domain.model.SourceStatus;
import jakarta.validation.constraints.Min;
import org.hibernate.validator.constraints.URL;

/**
 * 更新RSS源请求
 */
public record UpdateRssSourceRequest(
    String name,
    
    @URL(message = "URL格式不正确")
    String url,
    
    String description,
    
    @Min(value = 1, message = "抓取间隔至少为1分钟")
    Integer fetchIntervalMinutes,
    
    SourceStatus status
) {
}
