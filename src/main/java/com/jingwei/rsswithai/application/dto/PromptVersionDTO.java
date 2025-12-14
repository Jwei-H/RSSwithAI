package com.jingwei.rsswithai.application.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PromptVersionDTO {
    private Long id;
    private Long templateId;
    private Integer version;
    private String content;
    private Boolean immutable;
    private LocalDateTime createdAt;
}
