package com.jingwei.rsswithai.application.Event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class ArticleProcessEvent extends ApplicationEvent {
    private final Long articleId;

    public ArticleProcessEvent(Object source, Long articleId) {
        super(source);
        this.articleId = articleId;
    }

}