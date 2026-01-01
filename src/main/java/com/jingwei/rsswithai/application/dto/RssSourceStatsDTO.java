package com.jingwei.rsswithai.application.dto;

import java.util.Map;

public record RssSourceStatsDTO(
    long total,
    Map<String, Long> statusCounts
) {}