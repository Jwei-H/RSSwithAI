package com.jingwei.rsswithai.application.dto;

import lombok.Data;

@Data
public class UpdateModelConfigRequest {
    private String name;
    private String description;
    private String modelId;
    private Double temperature;
    private Double topP;
    private Integer topK;
    private Integer maxTokens;
    private Integer seed;
}