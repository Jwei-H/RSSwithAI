package com.jingwei.rsswithai.application.dto;

import com.jingwei.rsswithai.domain.model.RssSource;
import com.jingwei.rsswithai.domain.model.SourceCategory;
import com.jingwei.rsswithai.domain.model.Subscription;
import com.jingwei.rsswithai.domain.model.SubscriptionType;
import com.jingwei.rsswithai.domain.model.Topic;

import java.time.LocalDateTime;

public record SubscriptionDTO(
        Long id,
        SubscriptionType type,
        Long targetId,
        String name,
        String icon,
        SourceCategory category,
        String content,
        LocalDateTime createdAt
) {
    public static SubscriptionDTO from(Subscription subscription) {
        if (subscription.getType() == SubscriptionType.RSS) {
            RssSource source = subscription.getSource();
            return new SubscriptionDTO(
                    subscription.getId(),
                    SubscriptionType.RSS,
                    source != null ? source.getId() : null,
                    source != null ? source.getName() : null,
                    source != null ? source.getIcon() : null,
                    source != null ? source.getCategory() : null,
                    null,
                    subscription.getCreatedAt()
            );
        }
        Topic topic = subscription.getTopic();
        return new SubscriptionDTO(
                subscription.getId(),
                SubscriptionType.TOPIC,
                topic != null ? topic.getId() : null,
                null,
                null,
                null,
                topic != null ? topic.getContent() : null,
                subscription.getCreatedAt()
        );
    }
}
