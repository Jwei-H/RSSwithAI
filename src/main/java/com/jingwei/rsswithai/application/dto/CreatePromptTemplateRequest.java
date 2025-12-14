package com.jingwei.rsswithai.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreatePromptTemplateRequest {
    @NotBlank
    private String name;
    private String description;
}
