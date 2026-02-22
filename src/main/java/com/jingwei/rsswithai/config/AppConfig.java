package com.jingwei.rsswithai.config;

import lombok.Data;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
@Data
public class AppConfig {

    @SettingKey("collector_fetch_interval")
    private Integer collectorFetchInterval = 60;

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

    @SettingKey("admin_username")
    private String adminUsername;

    @SettingKey("admin_password")
    private String adminPassword;

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

    @SettingKey("trends_word_cloud_frequency_hours")
    private Integer trendsWordCloudFrequencyHours = 24;

    @SettingKey("subscription_topic_threshold")
    private Double topicThreshold = 0.4;

    @SettingKey("trends_hot_events_map_prompt")
    private String trendsHotEventsMapPrompt = """
              你是一个专业的新闻分析师。请基于以下RSS源最近文章的“标题+概览”，提炼 0-10 个具有明确时效性的关键事件。
              请忽略日常维护日志、版本微小更新、单纯教程或非时效性内容。
              如果多篇文章指向同一事件，请合并为一个事件。
              输出结果必须按重要程度降序排列（你可参考事件影响范围、涉及主体和出现频次），最多取前10个。
              宁缺毋滥：如果没有值得关注的事件，请返回空数组 []。
            
              RSS源名称：
              {sourcename}
            
              文章列表（每条包含标题与概览）：
              {articles}
            
              输出要求：
              1) 仅输出 JSON 数组（严禁 Markdown 代码块）
              2) 数组元素是字符串，每个字符串就是一个事件的简短描述
              3) 每个事件不超过50字
            
              输出示例：
              \\[
                "OpenAI推出gpt codex模型，在多项benchmark取得领先成绩...",
                "Java 25正式发布并带来多项平台能力增强..."
              \\]
            """;

    @SettingKey("trends_hot_events_frequency_hours")
    private Integer trendsHotEventsFrequencyHours = 12;

    @SettingKey("trends_hot_events_reduce_prompt")
    private String trendsHotEventsReducePrompt = """
              你是一个全球热点聚合引擎。以下输入是按RSS源分组的事件列表。
              每个源内的 events 已按重要程度降序排列，请将其作为重要参考。
              任务：
              1) 对跨源同义事件进行语义归并与去重
              2) 结合事件在不同源中的出现情况（频率）与事件影响力，给出热度分（1-10）
              3) 每个event控制在40字内，保持客观、完整、无来源标记

              输入（按源分组）：
              {events}
            
              请输出 JSON 数组，按热度降序排列，输出最多20个事件即可。
              格式：\\{"event": "一句话完整描述事件（不带来源标识）", "score": 10\\}
            
              输出示例（严禁包含 Markdown 代码块标记）：
              [
                \\{"event": "OpenAI发布文生视频模型Sora，引发广泛关注", "score": 10\\},
                \\{"event": "Google推出Gemini 1.5 Pro模型", "score": 9\\}
              ]
            """;
}