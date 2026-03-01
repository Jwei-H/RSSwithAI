# 大语言模型处理模块

## 1. 模块概述

大语言模型处理模块负责对原始文章进行AI驱动的深度处理与增强，包括生成文章概览、提取关键信息、生成标签、补充目录（toc），并生成向量表示。

### 1.1 核心功能

- 监听文章处理事件，自动触发AI增强处理
- 生成文章概览（80字以内，支持Markdown）
- 提取关键信息（1-3条，每条40字以内）
- 生成标签（5个左右）
- 生成补充目录（toc，含标题与锚点）
- 生成文章向量表示（1024维）
- 支持配置动态更新和客户端重建
- 支持并发控制（默认5）

### 1.2 用户交互

该模块为后台处理模块，主要通过事件驱动工作：

1. **自动处理**：RSS抓取器抓取到新文章后，发布ArticleProcessEvent事件，自动触发处理
2. **手动触发**：通过ArticleController的重新生成接口手动触发处理
3. **配置更新**：系统配置更新后，自动重建AI客户端

### 1.3 配置项

| 配置键 | 默认值      | 说明 |
|--------|----------|------|
| llm_base_url | -        | LLM API基础URL |
| llm_api_key | -        | LLM API密钥 |
| language_model_id | -        | 语言模型ID |
| llm_gen_prompt | [见默认提示词] | 内容生成提示词模板 |
| llm_gen_model_config | -        | 模型参数配置（JSON格式） |
| embedding_model_id | -        | 向量模型ID |
| concurrent_limit | 1        | 并发处理限制 |

### 1.4 默认提示词模板

```
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
\{
  "overview": "字符串，含**加粗内容**",
  "key_info": ["信息点1", "信息点2"],
  "tags": ["领域标签", "实体标签1", "实体标签2"],
  "toc": [
    \{
      "title": "### 补充的标题",
      "anchor": "照抄原文段落开头的字符片段"
    \}
  ]
\}

# Input Data
\```
标题：{title}
来源：{source}
正文：
{content}
\```
```

---

## 2. 架构设计

### 2.1 架构层次

```
Event Listener (LlmProcessService @EventListener)
    ↓
AI Client (OpenAiApi - Spring AI)
    ↓
Repository (ArticleExtraRepository)
    ↓
Entity (ArticleExtra)
```

### 2.2 核心组件

| 组件 | 职责 |
|------|------|
| LlmProcessService | 监听文章处理事件，执行AI增强处理 |
| OpenAiApi | Spring AI的OpenAI API客户端 |
| ArticleExtraRepository | 文章增强信息数据访问 |

---

## 3. 核心业务流程

### 3.1 文章处理流程

1. 监听ArticleProcessEvent事件
2. 获取信号量许可（控制并发数）
3. 检查文章是否已处理过
4. 调用语言模型生成内容
5. 解析JSON响应，提取概览、关键信息、标签、toc
6. 拼接概览和关键信息，调用embedding模型生成向量
7. 保存结果到ArticleExtra表
8. 释放信号量许可

### 3.2 内容生成流程

1. 构建提示词模板
2. 注入文章参数（title、author、content）
3. 调用语言模型
4. 解析JSON响应
5. 提取各字段内容并清洗toc项（仅保留有效title/anchor）

### 3.3 向量生成流程

1. **构建丰富向量化文本**：
   - 拼接逻辑：`Summary` (概览) + `\n` + `Key Points` (关键信息)
   - 目的：增加关键论据和实体词权重，提升语义密度
2. 调用embedding模型
3. 生成1024维向量
4. 存储到ArticleExtra的vector字段

### 3.4 配置更新流程

1. 监听ConfigUpdateEvent事件
2. 重建OpenAI API客户端
3. 调整信号量大小以匹配新的并发限制

---

## 4. 数据模型

### 4.1 ArticleExtra

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键ID |
| articleId | Long | 文章ID（外键，唯一） |
| overview | String | 文章概览 |
| keyInformation | String[] | 关键信息列表 |
| tags | String[] | 标签列表 |
| toc | JSONB(String) | AI补充目录（数组：title + anchor） |
| vector | Vector(1024) | 文章向量（1024维，pgvector） |
| status | AnalysisStatus | 处理状态（SUCCESS/FAILED） |
| errorMessage | String | 错误信息 |
| createdAt | LocalDateTime | 创建时间 |
| updatedAt | LocalDateTime | 更新时间 |

### 4.2 枚举类型

- **AnalysisStatus**: SUCCESS（成功）、FAILED（失败）

---

## 5. API接口

该模块无直接REST API接口，通过事件驱动工作。

---

## 6. 关键设计点

### 6.1 事件驱动架构

- 使用Spring的@EventListener监听ArticleProcessEvent
- 使用@TransactionalEventListener(phase = AFTER_COMMIT)监听ConfigUpdateEvent

### 6.2 并发控制

- 使用Semaphore信号量控制并发数
- 默认并发限制为5，可通过配置项调整
- 配置更新时动态调整信号量许可数

### 6.3 异步处理

- 使用虚拟线程处理异步任务
- 获取许可、处理文章、释放许可

### 6.4 AI客户端管理

- 持有独立的OpenAiApi实例
- 基于AppConfig中的llm_base_url和llm_api_key创建客户端
- 配置更新时重建客户端

### 6.5 错误处理

- 捕获所有异常，记录失败结果
- 不重试失败的请求
- 保存错误信息到errorMessage字段