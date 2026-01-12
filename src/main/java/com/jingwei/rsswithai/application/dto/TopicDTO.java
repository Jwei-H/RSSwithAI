package com.jingwei.rsswithai.application.dto;

import com.jingwei.rsswithai.domain.model.Topic;

import java.time.LocalDateTime;

public record TopicDTO(
        Long id,
        String content,
        LocalDateTime createdAt
) {
    public static TopicDTO from(Topic topic) {
        return new TopicDTO(
                topic.getId(),
                topic.getContent(),
                topic.getCreatedAt()
        );
    }
}
