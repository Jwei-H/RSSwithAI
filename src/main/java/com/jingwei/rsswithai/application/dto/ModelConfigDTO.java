package com.jingwei.rsswithai.application.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ModelConfigDTO {
    private Long id;
    private String name;
    private String description;
    private String modelId;
    private Double temperature;
    private Double topP;
    private Integer topK;
    private Integer maxTokens;
    private Long seed;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
