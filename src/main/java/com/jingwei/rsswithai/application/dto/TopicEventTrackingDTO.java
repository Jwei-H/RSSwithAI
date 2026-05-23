package com.jingwei.rsswithai.application.dto;

import java.time.LocalDateTime;
import java.util.List;

public record TopicEventTrackingDTO(
        Status status,
        Result result,
        String message
) {
    public enum Status {
        EMPTY,
        SUCCESS,
        INVALID
    }

    public record Result(
            Integer version,
            Long topicId,
            String topic,
            LocalDateTime generatedAt,
            List<Long> sourceArticleIds,
            List<Node> nodes
    ) {
    }

    public record Node(
            String date,
            String progress,
            String coverImage,
            List<Article> articles
    ) {
    }

    public record Article(
            Long id,
            String title
    ) {
    }

    public static TopicEventTrackingDTO empty(String message) {
        return new TopicEventTrackingDTO(Status.EMPTY, null, message);
    }

    public static TopicEventTrackingDTO success(Result result) {
        return new TopicEventTrackingDTO(Status.SUCCESS, result, null);
    }

    public static TopicEventTrackingDTO invalid(String message) {
        return new TopicEventTrackingDTO(Status.INVALID, null, message);
    }
}
