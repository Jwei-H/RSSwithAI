package com.jingwei.rsswithai.config;

import lombok.Data;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.List;

@Component
@Data
public class AppConfig {

    @SettingKey("collector_fetch_interval")
    private Integer collectorFetchInterval = 60;

    @SettingKey("collector_fetch_timeout")
    private Integer collectorFetchTimeout = 30;

    @SettingKey("collector_fetch_max_retries")
    private Integer collectorFetchMaxRetries = 3;

    @SettingKey("collector_title_filter_words")
    private List<String> collectorTitleFilterWords = List.of();

    @SettingKey("rsshub_host")
    private String rsshubHost = "http://rsshub.app";

    @SettingKey("llm_base_url")
    private String llmBaseUrl;

    @SettingKey("llm_api_key")
    private String llmApiKey;

    @SettingKey("language_model_id")
    private String languageModel;

    @SettingKey("llm_gen_prompt")
    private String llmGenPrompt = """
            # Role
            你是一个专业的新闻情报分析员和内容架构师。你的任务是为 RSS 文章生成高价值的元数据增强信息。
            
            # Constraints
            1. 语言：无论原文何种语言，输出必须为简体中文。（除了补充目录部分）
            2. 格式：严格输出 JSON，严禁任何开场白或解释性文字。
            3. 概览：控制在 100 字内，客观中立，对关键术语进行 **加粗** 处理。
            4. 关键信息：1-3 条，每条 < 40 字，侧重核心结论或事实，注意不要复读概览已有的内容。严禁**加粗。
            5. 标签：3-10 个。要求：[领域/分类] 或 [核心实体/技术名词]。避开 "深度好文"、"干货" 等无意义词汇。
            6. 补充目录 (toc)：
               - 如果文章较短或已有丰富的标题，此字段返回空数组 []。
               - 生成带有 Markdown 前缀的补充标题（如 "## 背景介绍"），与原文风格保持一致，禁止复读已有标题。请你自行判断level(2级到4级)。
               - 【极其重要】：为每个生成的标题提取其正下方第一个段落开头的 10-15 个字符作为“锚点 (anchor)”。锚点必须 100% 照抄原文，包含原文中的所有 Markdown 符号（如 **、*、[] 等），绝不能转换为纯文本。
            
            # Workflow
            1. 深度分析文章标题与内容，识别其核心事件、技术背景或核心观点。
            2. 提取文章中的实体（人名、公司名、技术协议、专有名词）。
            3. 总结全文，生成摘要与关键信息。
            4. 评估文章是否缺乏多级标题（## 或 ###）。
            
            # Output Format (JSON)
            \\{
              "overview": "字符串，含**加粗内容**",
              "key_info": ["信息点1", "信息点2"],
              "tags": ["领域标签", "实体标签1", "实体标签2"],
              "toc": [
                \\{
                  "title": "### 补充的标题",
                  "anchor": "照抄原文段落开头的字符片段"
                \\}
              ]
            \\}
            
            # Input Data
            ```
            标题：{title}
            来源：{source}
            正文：
            {content}
            ```
            """;

    @SettingKey("llm_gen_model_config")
    private JsonNode llmGenModelConfig;

    @SettingKey("embedding_model_id")
    private String embeddingModel;

    @SettingKey("embedding_base_url")
    private String embeddingBaseUrl;

    @SettingKey("embedding_api_key")
    private String embeddingApiKey;

    @SettingKey("concurrent_limit")
    private Integer concurrentLimit = 1;

    @SettingKey("admin_username")
    private String adminUsername = "admin";

    @SettingKey("admin_password")
    private String adminPassword = "admin";

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
             你是一个专业的新闻分析师。请基于以下RSS源最近文章的“标题+概览”，提炼 0-10 个具有**明确时效性**的客观事件。
             请忽略单纯教程、版本更新或任何非时效性内容。
             如果多篇文章指向同一事件，请合并为一个事件。
             输出结果必须按重要程度降序排列（你可参考事件影响范围、涉及主体和出现频次），最多取前10个。
             宁缺毋滥，事件要求少而精，如果没有值得关注的事件，请返回空数组 []。
            
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
            每个源内的 events 已按重要程度降序排列。
            任务：
            1) 对跨源同义事件进行语义归并与去重
            2) 结合事件的出现频率、事件价值与事件的影响力，给出评分（1-10）
            3) 每个event控制在45字内，保持客观、完整、无来源标记
            
            输入（按源分组）：
            {events}
            
            请输出 JSON 数组，按评分降序排列，输出最多40个事件。
            格式：\\{"event": "一句话完整描述事件（不带来源标识）", "score": 10\\}
            
            输出示例（严禁包含 Markdown 代码块标记）：
            [
            \\{"event": "OpenAI发布文生视频模型Sora，引发广泛关注...", "score": 10\\},
            \\{"event": "Google推出Gemini 2.5 Pro模型...", "score": 9\\}
            ]
            """;
}