package com.jingwei.rsswithai.application.dto;

import lombok.Data;

import java.util.List;

@Data
public class CreateExperimentRequest {
    private String name;
    private String description;
    private List<Long> articleIds;
    private Long modelConfigId;
    private Long promptTemplateId;
    private Integer promptVersion;
}
