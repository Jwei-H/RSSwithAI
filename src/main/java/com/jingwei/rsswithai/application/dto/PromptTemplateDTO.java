package com.jingwei.rsswithai.application.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class PromptTemplateDTO {
    private Long id;
    private String name;
    private String description;
    private Integer latestVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private PromptVersionDTO latestVersionDetail;
}
