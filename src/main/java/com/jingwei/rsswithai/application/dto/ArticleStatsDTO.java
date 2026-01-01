package com.jingwei.rsswithai.application.dto;

import java.time.LocalDate;
import java.util.List;

public record ArticleStatsDTO(
        long total,
        List<DailyCount> dailyCounts
) {
    public record DailyCount(
            LocalDate date,
            long count
    ) {}
}