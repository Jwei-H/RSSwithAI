package com.jingwei.rsswithai.config;

import lombok.Data;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

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

    @SettingKey("llm_base_url")
    private String llmBaseUrl;

    @SettingKey("llm_api_key")
    private String llmApiKey;

    @SettingKey("language_model_id")
    private String languageModel;

    @SettingKey("llm_gen_prompt")
    private String llmGenPrompt;

    @SettingKey("llm_gen_model_config")
    private JsonNode llmGenModelConfig;

    @SettingKey("embedding_model_id")
    private String embeddingModel;

    @SettingKey("concurrent_limit")
    private Integer concurrentLimit = 5;

    @SettingKey("admin_username")
    private String adminUsername;

    @SettingKey("admin_password")
    private String adminPassword;

    @SettingKey("default_avatar")
    private String defaultAvatar;
}