package com.jingwei.rsswithai.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateModelConfigRequest {
    @NotBlank
    private String name;
    private String description;
    @NotBlank
    private String modelId;
    private Double temperature;
    private Double topP;
    private Integer topK;
    private Integer maxTokens;
    private Integer seed;
}