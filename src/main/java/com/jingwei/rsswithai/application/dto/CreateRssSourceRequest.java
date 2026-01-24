package com.jingwei.rsswithai.application.dto;

import com.jingwei.rsswithai.domain.model.SourceCategory;
import com.jingwei.rsswithai.domain.model.SourceType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 创建RSS源请求
 */
public record CreateRssSourceRequest(
    String name,  // 可选，如果为空则从RSS源中提取

    @NotBlank(message = "URL不能为空")
    String url,

    @NotNull(message = "类型不能为空")
    SourceType type,

    String description,

    String link,

    @Min(value = 1, message = "抓取间隔至少为1分钟")
    Integer fetchIntervalMinutes,

    SourceCategory category
) {
}