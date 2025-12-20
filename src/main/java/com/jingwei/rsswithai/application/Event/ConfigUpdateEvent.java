package com.jingwei.rsswithai.application.Event;

import org.springframework.context.ApplicationEvent;

public class ConfigUpdateEvent extends ApplicationEvent {
    public ConfigUpdateEvent(Object source) {
        super(source);
    }
}