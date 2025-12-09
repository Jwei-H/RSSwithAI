package com.jingwei.rsswithai.config;

import lombok.Data;
import org.springframework.stereotype.Component;

@Component
@Data
public class AppConfig {

    @SettingKey("collector_fetch_interval")
    private Long collectorFetchInterval = 60000L;

    @SettingKey("collector_fetch_timeout")
    private Integer collectorFetchTimeout = 30;

    @SettingKey("collector_fetch_max_retries")
    private Integer collectorFetchMaxRetries = 3;

    @SettingKey("rsshub_host")
    private String rsshubHost = "http://rsshub.app";
}
