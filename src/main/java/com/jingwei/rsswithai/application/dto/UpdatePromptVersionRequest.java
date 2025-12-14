package com.jingwei.rsswithai.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdatePromptVersionRequest {
    @NotBlank
    private String content;
}
