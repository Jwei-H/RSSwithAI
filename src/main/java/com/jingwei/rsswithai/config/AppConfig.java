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

    @SettingKey("embedding_base_url")
    private String embeddingBaseUrl;

    @SettingKey("embedding_api_key")
    private String embeddingApiKey;

    @SettingKey("concurrent_limit")
    private Integer concurrentLimit = 5;

    @SettingKey("feed_similarity_threshold")
    private Double feedSimilarityThreshold = 0.4;

    @SettingKey("subscription_limit")
    private Integer subscriptionLimit = 20;

    @SettingKey("admin_username")
    private String adminUsername;

    @SettingKey("admin_password")
    private String adminPassword;

    @SettingKey("default_avatar")
    private String defaultAvatar;

    @SettingKey("trends_word_cloud_frequency_hours")
    private Integer trendsWordCloudFrequencyHours = 6;

    @SettingKey("trends_hot_events_frequency_hours")
    private Integer trendsHotEventsFrequencyHours = 4;

    @SettingKey("trends_word_cloud_prompt")
    private String trendsWordCloudPrompt = """
            你是一个精通语义分析的助手。以下是一组"标签:频次"的数据，请找出其中含义完全相同或高度互为同义词的组合（例如 "AI":"人工智能", "LLM":"大语言模型"）。
            请返回一个 JSON 对象，Key 为你选定的标准词（通常是出现频率最高或最通用的那个），Value 为该标准词对应的所有变体列表（包含标准词本身）。
            未在 JSON 中出现的词将被视为无同义词，保持原样。**禁止使用"其他"、"杂项"等模糊类别**。
            
            输入数据：
            {tags}
            
            输出格式要求（严禁包含 Markdown 代码块标记，仅返回纯 JSON）：
            \\{
              "人工智能": ["AI", "人工智能", "Artificial Intelligence"],
              "SpringBoot": ["Spring Boot", "SpringBoot"]
            \\}
            """;

    @SettingKey("trends_hot_events_map_prompt")
    private String trendsHotEventsMapPrompt = """
            你是一个专业的新闻分析师。请分析以下RSS源最近的文章概览，提炼出 0-5 个具有明确时效性的关键新闻事件。
            请忽略日常维护日志、版本微小更新、单纯的技术教程或非时效性内容。
            如果没有值得关注的大事件，返回空列表。
            
            文章列表：
            {articles}
            
            请输出 JSON 数组，每个元素需包含：
            - event: 事件的一句话标题（简练、客观）
            - description: 30字以内的简介
            
            输出示例（严禁包含 Markdown 代码块标记）：
            \\[
              \\{"event": "OpenAI发布Sora模型", "description": "OpenAI推出首个文生视频模型，效果震撼行业。"\\},
              \\{"event": "Java 25正式发布", "description": "Oracle发布Java 25，引入全新协程模型。"\\}
            \\]
            """;

    @SettingKey("trends_hot_events_reduce_prompt")
    private String trendsHotEventsReducePrompt = """
            你是一个全球热点聚合引擎。以下是从多个来源收集到的新闻事件片段。很多片段描述的是同一个事件，请进行语义识别、去重和合并，每个event不要超过40字。
            并根据事件被提及的频率（隐含在列表中重复出现的次数）以及事件本身的行业影响力，打一个热度分（1-10，10为最高）。
            
            输入事件列表：
            {events}
            
            请输出 JSON 数组，按热度降序排列，输出最多15个事件即可。
            格式：\\{"event": "一句话完整描述事件（不带来源标识）", "score": 10\\}
            
            输出示例（严禁包含 Markdown 代码块标记）：
            [
              \\{"event": "OpenAI发布文生视频模型Sora，引发广泛关注", "score": 10\\},
              \\{"event": "Google推出Gemini 1.5 Pro模型", "score": 9\\}
            ]
            """;
}