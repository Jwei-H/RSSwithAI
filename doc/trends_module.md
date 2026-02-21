# 热点趋势模块

## 1. 模块概述

热点趋势模块（Trends Module）旨在通过对RSS文章的聚合分析，为用户提供直观的关键词云和实时热度榜单。系统利用大语言模型（LLM）对抓取的情报进行深度分析，提取高频标签和关键事件，帮助用户快速感知即时热点。

### 1.1 核心功能

- **智能词云生成**：基于文章标签频率，利用LLM进行语义清洗和同义词合并。
- **全球热点事件提取**：采用Map-Reduce架构，利用LLM从海量文章中提取并聚合关键热点事件。
- **多维度展示**：支持按RSS源查看词云，或聚合查看用户订阅的所有词云。
- **实时更新**：基于定时任务自动刷新分析结果。

### 1.2 用户交互

用户可以通过前台界面查看：
1. **关键词云**：展示当前关注源的高频关键词，支持可视化交互。
2. **热点事件榜单**：展示全网（或全局）最受关注的 Top 事件，按热度打分排序。

### 1.3 配置项

| 配置键                            | 默认值   | 说明                                 |
| --------------------------------- | -------- | ------------------------------------ |
| trends_word_cloud_frequency_hours | 24       | 词云生成任务频率（小时）             |
| trends_hot_events_frequency_hours | 12       | 热点事件生成任务频率（小时）         |
| trends_word_cloud_prompt          | (见下文) | 词云清洗Prompt模板                   |
| trends_hot_events_map_prompt      | (见下文) | 热点事件提取（Map阶段）Prompt模板    |
| trends_hot_events_reduce_prompt   | (见下文) | 热点事件聚合（Reduce阶段）Prompt模板 |

---

## 2. 架构设计

### 2.1 架构层次

```
Controller (FrontTrendsController)
    ↓
Service (TrendsService)
    ↓
Repository (TrendsDataRepository, SubscriptionRepository)
    ↓
Domain Service (TrendsAnalysisService, TrendsTaskScheduler)
    ↓
Entity (TrendsData)
```

### 2.2 核心组件

| 组件                  | 职责                                                      |
| --------------------- | --------------------------------------------------------- |
| FrontTrendsController | 提供前台查询趋势数据的 REST API                           |
| TrendsService         | 封装查询逻辑，处理 DTO 转换                               |
| SubscriptionService   | 复用 Topic 创建/向量检索能力，提供热点事件相关文章预览能力 |
| TrendsAnalysisService | 核心分析服务，执行 LLM 调用、数据清洗与聚合（Map-Reduce） |
| TrendsTaskScheduler   | 定时调度器，触发分析任务                                  |
| TrendsDataRepository  | 趋势数据存取                                              |

---

## 3. 核心业务流程

### 3.1 词云生成流程 (Incremental Cleaning)

1. **数据准备**：选取指定 Source 在 7 天内的文章（不足 20 篇则自动补足）。
2. **初步统计**：统计文章 Tags 频次，截取 Top 100。
3. **LLM 清洗**：调用 AI 识别同义词（如 "AI" = "人工智能"）。
4. **归并存储**：合并同义词频次，存入数据库。

### 3.2 热点事件导出流程 (Map-Reduce)

1. **Map 阶段（源内提取）**：
   - 遍历活跃 RSS 源，选取最近 3 天文章。
  - 为每篇文章提供“标题 + Overview”作为 LLM 参考上下文。
  - 调用 LLM 提取 0-10 个关键事件短描述（每条 40 字内），并要求按重要程度降序返回。
  - Map 输出仅保留事件短描述，不再拆分 `event` 与 `description` 字段。
2. **Reduce 阶段（全局聚合）**：
  - 将 Map 结果按 RSS 源分组后输入 LLM，并明确告知“每个源内事件已按重要程度排序”。
  - 对空事件列表的 RSS 源直接跳过，不参与 Reduce。
  - 调用 LLM 进行跨源语义去重、归并、打分（1-10分）。
3. **持久化**：
  - 词云与热点事件均采用**追加写入**（append-only），不覆盖历史记录。
  - 查询时统一读取指定维度的**最新一条记录**。

### 3.3 热点事件相关文章预览流程（More）

1. 前端点击热点事件“更多”，调用 `/api/front/v1/trends/hotevents/articles?event=...`。
2. 后端按 `event` 查询 Topic：
  - 若已存在，直接复用 Topic 向量；
  - 若不存在，创建 Topic 并调用模型生成向量。
3. 使用该 Topic 向量执行语义检索，复用订阅模块 Hybrid Feed 的 Topic 分支检索策略。
4. 返回 `ArticleFeedDTO` 列表，按 `pub_date DESC, id DESC` 排序并支持游标分页。

---

## 4. 数据模型

### 4.1 TrendsData

使用单个实体存储不同维度的分析结果，利用 `JSONB` 字段存储非结构化数据。

| 字段      | 类型          | 说明                         |
| --------- | ------------- | ---------------------------- |
| id        | Long          | 主键ID                       |
| sourceId  | Long          | 来源ID（0=全局，非0=特定源） |
| type      | String        | 类型：WORD_CLOUD, HOT_EVENTS |
| data      | JSONB         | 核心数据（JSON数组）         |
| createdAt | LocalDateTime | 创建时间                     |
| updatedAt | LocalDateTime | 更新时间                     |

### 4.2 数据结构示例

**WORD_CLOUD (data)**:
```json
[
  {"text": "AI", "value": 50},
  {"text": "Spring Boot", "value": 30}
]
```

**HOT_EVENTS (data)**:
```json
[
  {"event": "OpenAI发布Sora", "score": 10},
  {"event": "Java 25发布", "score": 9}
]
```

**Map 阶段中间结果（按源排序事件）**:
```json
[
  {"source": "TechCrunch", "events": ["OpenAI发布Sora并开启测试", "Google更新Gemini多模态能力"]},
  {"source": "InfoQ", "events": ["Java 25正式发布并带来平台增强"]}
]
```

---

## 5. API接口

| 方法 | 路径                           | 描述                                          |
| ---- | ------------------------------ | --------------------------------------------- |
| GET  | /api/front/v1/trends/wordcloud | 获取词云（可指定 sourceId，否则聚合用户订阅） |
| GET  | /api/front/v1/trends/hotevents | 获取全局热点事件榜单                          |
| GET  | /api/front/v1/trends/hotevents/articles | 获取指定热点事件的相关文章预览（Topic 语义检索） |

---

## 6. 关键设计点

### 6.1 Map-Reduce模式
针对海量文章的分析，采用分治策略：先在单个 RSS 源维度提取（Map），再在全局维度聚合（Reduce），降低 LLM 上下文窗口压力，提高处理效率。

### 6.2 异步调度
分析任务耗时较长，采用 `@Scheduled` 定时任务后台异步执行，生成结果缓存于数据库，前端请求直接读取结果，保证毫秒级响应。
任务首次执行在服务启动后延迟触发（词云 12 小时、热点事件 6 小时）。

### 6.3 动态Prompt配置
所有涉及 LLM 的 Prompt 均存储于 `AppConfig` 并持久化到数据库，支持管理员在线调整 Prompt 策略以优化分析效果，无需重启服务。
