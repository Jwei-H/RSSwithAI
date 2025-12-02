package com.jingwei.rsswithai.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;

/**
 * 创建RSS源请求
 */
public record CreateRssSourceRequest(
    @NotBlank(message = "名称不能为空")
    String name,
    
    @NotBlank(message = "URL不能为空")
    @URL(message = "URL格式不正确")
    String url,
    
    String description,
    
    @Min(value = 1, message = "抓取间隔至少为1分钟")
    Integer fetchIntervalMinutes
) {
}
