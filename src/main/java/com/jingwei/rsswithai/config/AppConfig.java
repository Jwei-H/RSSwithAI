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
    private String llmBaseUrl = "https://api.openai.com";

    @SettingKey("llm_api_key")
    private String llmApiKey;

    @SettingKey("language_model_id")
    private String languageModel;

    @SettingKey("llm_gen_prompt")
    private String llmGenPrompt = """
            请根据下面的文章内容，给出三部分信息：
            1. 文章的简要概览，控制在100字以内；
            2. 文章的关键信息，选取1~3条最重要的事实(每条40字以内)，视文章长度调整，不要与概览内容重复
            3. 文章的关键词或标签，10个以内
            请将回答内容以JSON格式返回，包含以下字段：
            {
              "overview": "文章概览内容",
              "key_info": ["重要信息1", "..."],
              "tags": ["标签1", "标签2", "..."]
            }
            其中overview字段支持markdown格式，其它字段为纯文本内容
            文章内容如下：
            ```markdown
            标题：{title}
            来源：{source}
            ---
            {content}
            ```
            """;

    @SettingKey("llm_gen_model_config")
    private JsonNode llmGenModelConfig;

    @SettingKey("embedding_model_id")
    private String embeddingModel;

    @SettingKey("concurrent_limit")
    private Integer concurrentLimit = 5;
}