# Topic 事件追踪模块

## 1. 模块概述

Topic 事件追踪模块为 Topic 类型订阅生成结构化事件时间线。系统基于 Topic 向量检索相关文章，再调用 LLM 将文章归纳为按日期倒排的事件进展节点，结果持久化到 `topics.event_tracking_result`。

该模块主要服务两个前端入口：

1. 订阅时间线：基于 `subscriptionId` 查看或生成某个 Topic 订阅的事件追踪。
2. 热点事件弹窗：基于 `topicId` 查看或生成热点事件关联 Topic 的事件追踪。

### 1.1 核心功能

- 获取 Topic 最新事件追踪结果。
- 基于 Topic 向量检索候选文章。
- 通过 SSE 流式生成事件时间线。
- 校验并清洗 LLM 输出，保存结构化 JSON。
- 支持按订阅维度和 Topic 维度访问。
- 对同一 Topic 设置生成冷却和防并发机制。

### 1.2 用户交互

前台用户可以通过页面完成以下操作：

1. 在 Topic 订阅时间线中查看已有事件追踪结果。
2. 触发事件追踪生成，前端通过 SSE 接收生成过程。
3. 在热点事件弹窗中查看该事件对应 Topic 的追踪时间线。
4. 生成完成后查看按日期倒排的事件节点及关联文章。

### 1.3 约束条件

| 约束项 | 值 | 说明 |
|--------|-----|------|
| Topic 最短长度 | 6 字符 | 内容过短不支持生成 |
| 最少候选文章 | 5 篇 | 低于该数量不触发生成 |
| 最多候选文章 | 30 篇 | 去重后截断 |
| 每日最多取文 | 5 篇 | 控制单日文章占比 |
| 预取文章数 | 60 篇 | SQL 查询上限 |
| 时间线最多节点 | 12 个 | 输出截断 |
| 每节点最多关联文章 | 3 篇 | 单节点文章上限 |
| 生成冷却期 | 1 小时 | 同一 Topic 两次生成的最小间隔 |
| SSE 超时 | 5 分钟 | 流式连接超时时间 |
| 向量距离阈值 | 0.4（可配置） | 短 Topic（<16 字符）会加 0.05 |

---

## 2. 架构设计

### 2.1 架构层次

```text
Controller (TopicEventTrackingController)
    ↓
Service (TopicEventTrackingService)
    ↓
Domain Service (AiChatService)
    ↓
Repository (TopicRepository / SubscriptionRepository)
    ↓
Entity (Topic / Article / ArticleExtra)
```

### 2.2 核心组件

| 组件 | 职责 |
|------|------|
| TopicEventTrackingController | 提供事件追踪 REST 与 SSE API |
| TopicEventTrackingService | 编排候选文章检索、生成、校验和持久化 |
| AiChatService | 调用 LLM 并返回流式文本 |
| TopicRepository | 查询与保存 Topic，包括事件追踪结果 |
| SubscriptionRepository | 校验订阅归属和订阅类型 |

---

## 3. 核心业务流程

### 3.1 获取事件追踪结果

1. 根据 `subscriptionId` 校验订阅归属，或根据 `topicId` 查询 Topic。
2. 读取 `topics.event_tracking_result` 字段。
3. 解析 JSON 并封装为 `TopicEventTrackingDTO`。
4. 返回三态结果：
   - `EMPTY`：无结果或 Topic 不满足生成条件。
   - `SUCCESS`：结果可用。
   - `INVALID`：历史结果 JSON 无法解析。

### 3.2 生成事件追踪

1. 校验 Topic 内容长度。
2. 检查同一 Topic 是否已有生成任务正在执行。
3. 检查冷却期，避免短时间内重复生成。
4. 基于 Topic 向量检索候选文章。
5. 候选文章不足时返回失败事件。
6. 通过 SSE 发送 `meta` 事件。
7. 调用 LLM 流式生成事件时间线，逐段发送 `chunk` 事件。
8. 生成结束后校验并丰富节点数据。
9. 保存结果并发送 `done` 事件。

### 3.3 候选文章检索

候选文章通过 `article_extra.vector` 与 Topic 向量做余弦距离检索。查询逻辑包括：

1. 使用 pgvector `<=>` 计算距离。
2. 过滤 `article_extra.status = 'SUCCESS'` 且发布时间不为空的文章。
3. 按发布日分组，每天最多取 5 篇。
4. 最终按 `pub_date DESC, distance ASC, id DESC` 排序。
5. Java 侧再按 `link`、`guid`、`title` 做去重，最多保留 30 篇。

### 3.4 结果校验与丰富

`validateAndEnrich()` 会对 LLM 输出做以下处理：

1. 从模型输出中提取 JSON 对象。
2. 校验节点日期格式和合法性。
3. 截断过长的 `progress`。
4. 校验关联文章必须来自候选文章集合。
5. 为节点补充可用封面图。
6. 按日期倒排，同日保持原始顺序。
7. 最多保留 12 个节点。

无效节点会被丢弃；JSON 无法解析时，本次生成不会保存结果。

### 3.5 并发与事务处理

- 使用内存集合防止同一 Topic 并行生成。
- 使用冷却期限制重复生成。
- SSE 连接状态通过原子变量跟踪，避免重复结束。
- 数据库写入通过短事务完成，避免 LLM 流式调用期间占用长事务。

---

## 4. 数据模型

### 4.1 Topic 扩展字段

`topics` 表通过 `event_tracking_result` 字段保存最近一次生成结果：

```java
@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "event_tracking_result", columnDefinition = "JSONB")
private String eventTrackingResult;
```

### 4.2 结果 JSON 结构

```json
{
  "version": 1,
  "topicId": 42,
  "topic": "OpenAI GPT-5 发布",
  "generatedAt": "2026-05-25T10:30:00",
  "sourceArticleIds": [101, 205, 310],
  "nodes": [
    {
      "date": "2026-05-20",
      "progress": "GPT-5 内部测试消息泄露",
      "coverImage": "https://example.com/img.png",
      "articles": [
        { "id": 101, "title": "OpenAI 内部测试 GPT-5" },
        { "id": 205, "title": "GPT-5 性能基准曝光" }
      ]
    }
  ]
}
```

---

## 5. API 接口

所有接口前缀：`/api/front/v1`

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | `/subscriptions/{subscriptionId}/event-tracking` | 获取某个 Topic 订阅的最新事件追踪结果 |
| POST | `/subscriptions/{subscriptionId}/event-tracking/generate` | 为某个 Topic 订阅流式生成事件追踪 |
| GET | `/topics/{topicId}/event-tracking` | 按 Topic 获取最新事件追踪结果 |
| POST | `/topics/{topicId}/event-tracking/generate` | 按 Topic 流式生成事件追踪 |

POST 接口返回 `SseEmitter`，事件协议如下：

| 事件名 | 数据 | 说明 |
|--------|------|------|
| meta | `{topic, articleCount}` | 生成开始 |
| chunk | `{text}` | LLM 输出片段 |
| done | `{saved, message?}` | 生成完成，`saved=true` 表示已保存 |
| error | `{message}` | 生成失败 |

---

## 6. 前端要点

`TopicEventTrackerCard.vue` 负责事件追踪结果渲染，订阅时间线和热点事件弹窗复用该组件。

流式生成时，前端在 `chunk` 回调中拼接文本，并通过增量解析已闭合节点实现边生成边展示。
