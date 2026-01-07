package com.jingwei.rsswithai.application.dto;

import java.util.List;

public record CreateExperimentRequest(
    String name,
    String description,
    List<Long> articleIds,
    Long modelConfigId,
    Long promptTemplateId,
    Integer promptVersionNum
) {}