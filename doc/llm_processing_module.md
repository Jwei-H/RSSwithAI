# 大语言模型处理模块

## 1. 模块概述

大语言模型处理模块负责对原始文章进行AI驱动的深度处理与增强，包括生成文章概览、提取关键信息、生成标签，并生成向量表示。

### 1.1 核心功能

- 监听文章处理事件，自动触发AI增强处理
- 生成文章概览（80字以内，支持Markdown）
- 提取关键信息（1-3条，每条40字以内）
- 生成标签（5个左右）
- 生成文章向量表示（1024维）
- 支持配置动态更新和客户端重建
- 支持并发控制（默认5）

### 1.2 用户交互

该模块为后台处理模块，主要通过事件驱动工作：

1. **自动处理**：RSS抓取器抓取到新文章后，发布ArticleProcessEvent事件，自动触发处理
2. **手动触发**：通过ArticleController的重新生成接口手动触发处理
3. **配置更新**：系统配置更新后，自动重建AI客户端

### 1.3 配置项

| 配置键 | 默认值 | 说明 |
|--------|--------|------|
| llm_base_url | - | LLM API基础URL |
| llm_api_key | - | LLM API密钥 |
| language_model_id | - | 语言模型ID |
| llm_gen_prompt | [见默认提示词] | 内容生成提示词模板 |
| llm_gen_model_config | - | 模型参数配置（JSON格式） |
| embedding_model_id | - | 向量模型ID |
| concurrent_limit | 5 | 并发处理限制 |

### 1.4 默认提示词模板

```
请根据下面的文章内容，给出三部分信息：
1. 文章的简要概览，控制在80字以内；
2. 文章的关键信息，选取1~3条最重要的事实(每条40字以内)，视文章长度调整
3. 文章的关键词或标签，5个左右
请将回答内容以JSON格式返回，包含以下字段：
{
  "overview": "文章概览内容",
  "key_info": ["重要信息1", "重要信息2", "..."],
  "tags": ["标签1", "标签2", "..."]
}
其中overview字段支持markdown格式，其它字段为纯文本内容
文章内容如下：
\```markdown
标题：{title}
来源：{source}
---
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
5. 解析JSON响应，提取概览、关键信息、标签
6. 拼接概览和关键信息，调用embedding模型生成向量
7. 保存结果到ArticleExtra表
8. 释放信号量许可

### 3.2 内容生成流程

1. 构建提示词模板
2. 注入文章参数（title、author、content）
3. 调用语言模型
4. 解析JSON响应
5. 提取各字段内容

### 3.3 向量生成流程

1. 拼接标题 + 概览（overview 为空则仅标题）
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