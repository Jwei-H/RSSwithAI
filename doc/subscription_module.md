# 用户订阅系统模块

## 1. 模块概述

用户订阅系统模块为前台用户提供个性化订阅能力，支持按 **RSS源** 与按 **语义主题(Topic)** 两种维度进行订阅，并基于文章向量能力构建“混合时间线 (Hybrid Feed)”。

### 1.1 核心功能

- RSS源订阅 / 取消订阅
- 语义主题创建（内容长度限制、生成1024维向量、主题不可变）
- 主题订阅 / 取消订阅
- 获取订阅列表（RSS订阅需返回源的 name/icon/category；主题订阅需返回主题 content）
- RSS源列表按 `latestArticlePubDate DESC`（空值置后）
- 订阅列表固定 RSS 在前、TOPIC 在后，且 RSS 部分按 `latestArticlePubDate DESC`
- 混合时间线（RSS匹配 + 主题语义匹配），按 `pub_date DESC, id DESC` 排序
- 基于复合游标 `(pubDate, articleId)` 的游标分页

### 1.2 用户交互

前台用户通过 REST API 完成以下交互：

1. 浏览可用RSS源列表（仅展示启用源），同时标记自己是否已订阅
2. 预览RSS源文章：点击源查看其最近发布的文章，辅助决定是否订阅
3. 创建主题（输入30字以内的主题描述，系统自动向量化）
4. 创建订阅（订阅RSS源或订阅主题）
5. 取消订阅
6. 进入时间线：
   - 聚合时间线：汇总用户所有RSS订阅与主题订阅
   - 单订阅过滤：传入 `subscriptionId` 只看某一个订阅

### 1.3 配置项

| 配置键 | 默认值 | 说明 |
|--------|--------|------|
| feed_similarity_threshold | 0.45 | 主题语义匹配的距离阈值（使用 pgvector `<=>` 计算距离） |
| subscription_limit | 30 | 单个用户订阅数量上限（包含RSS与主题） |

> 以上配置项通过 `SettingsService` 从数据库 settings 表动态加载并可热更新。

---

## 2. 架构设计

### 2.1 架构层次

```
Controller (interfaces.front.SubscriptionController)
    ↓
Service (SubscriptionService)
    ↓
Repository (SubscriptionRepository / TopicRepository / RssSourceRepository)
    ↓
Entity (Subscription / Topic / RssSource / Article / ArticleExtra)
```

### 2.2 核心组件

| 组件 | 职责 |
|------|------|
| SubscriptionController | 前台订阅相关API入口（/api/front/v1） |
| SubscriptionService | 主题创建、订阅增删查、Hybrid Feed查询与游标解析 |
| TopicRepository | Topic查询/持久化（按content复用同一主题） |
| SubscriptionRepository | 用户订阅查询/持久化（带唯一约束防重复订阅） |
| RssSourceRepository | RSS源分页查询（仅返回 ENABLED 源） |
| LlmProcessService | 复用 embedding 能力：生成 Topic 向量（1024维） |
| FrontJwtFilter / UserContext | JWT鉴权与当前用户上下文（ScopedValue） |

---

## 3. 核心业务流程

### 3.1 主题创建流程

1. **输入限制**：`content` 非空，长度 ≤ 30
2. **去重复用**：按 `Topic.content` 唯一索引，若已存在则直接复用
3. **向量化**：调用 `LlmProcessService.generateVector(content)` 得到 1024 维向量
4. **不可变**：Topic 创建后不提供修改接口（内容与向量强绑定）

### 3.2 订阅创建流程

1. 校验 `type` 与 `targetId`
2. **重复订阅幂等**：若用户已订阅同一目标，直接返回已有订阅
3. **订阅上限**：创建前检查 `subscription_limit`，超过则拒绝
4. 持久化 Subscription

### 3.3 混合时间线逻辑 (Hybrid Feed)

时间线由两个分支结果集合并：

- **RSS匹配分支**：`articles.source_id IN (userSubscribedSourceIds)`
- **主题语义匹配分支**：基于 `article_extra.vector` 与 `topic.vector` 的距离检索：
  - 使用 pgvector `<=>` 计算距离
  - 以 `feed_similarity_threshold` 作为阈值
  - 通过动态 OR 条件拼接多个 topic 向量，尽量利用索引，避免全表扫描

最后对合并结果执行统一排序与游标分页：

- 排序：`pub_date DESC, id DESC`
- 游标条件：`(pub_date < cursorTime) OR (pub_date = cursorTime AND id < cursorId)`
- 游标格式：`pubDate,articleId`，例如 `2023-10-27T10:00:00,500`

---

## 4. 数据模型

### 4.1 Topic

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键ID |
| content | String | 主题内容（唯一） |
| vector | Vector(1024) | 主题向量（1024维） |
| createdAt | LocalDateTime | 创建时间 |

### 4.2 Subscription

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键ID |
| userId | Long | 用户ID |
| type | SubscriptionType | RSS / TOPIC |
| source | RssSource | RSS订阅目标（可空） |
| topic | Topic | 主题订阅目标（可空） |
| createdAt | LocalDateTime | 创建时间 |

### 4.3 约束与索引

- `topics.content` 唯一索引（避免重复主题）
- `subscriptions` 表对 `(user_id, source_id)` 与 `(user_id, topic_id)` 设置唯一约束（避免重复订阅）

---

## 5. API接口（前台）

所有接口前缀：`/api/front/v1`

### 5.1 RSS源展示

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | /rss-sources | 展示所有可用源（仅 ENABLED），并标记当前用户是否已订阅（分页） |

排序说明：默认按 `latestArticlePubDate DESC, id DESC` 返回。

响应（UserRssSourceDTO）：

```json
{
  "id": 1,
  "name": "科技新闻",
  "icon": "http://...",
  "category": "TECH",
  "isSubscribed": true,
  "subscriptionId": 88
}
```

### 5.2 主题管理

| 方法 | 路径 | 描述 |
|------|------|------|
| POST | /topics | 创建主题（30字以内；若已存在则复用） |

请求：

```json
{ "content": "国外AI人才动向" }
```

响应（TopicDTO）：

```json
{ "id": 10, "content": "国外AI人才动向", "createdAt": "2026-01-12T10:00:00" }
```

### 5.3 订阅管理

| 方法 | 路径 | 描述 |
|------|------|------|
| POST | /subscriptions | 创建订阅（RSS或TOPIC） |
| DELETE | /subscriptions/{id} | 取消订阅 |
| GET | /subscriptions | 获取当前用户订阅列表（不分页） |

排序说明：返回顺序固定为 RSS 订阅在前、TOPIC 订阅在后；RSS 订阅内部按 `latestArticlePubDate DESC` 排序。

创建请求（CreateSubscriptionRequest）：

```json
{ "type": "RSS", "targetId": 1 }
```

### 5.4 文章列表（时间线）

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | /articles/feed | 获取聚合时间线或单订阅过滤时间线（游标分页） |

参数：

- `subscriptionId`：可选；不传表示聚合时间线
- `cursor`：可选；格式 `pubDate,articleId`
- `size`：可选；默认20，最大100

响应（FeedArticleDTO）：

```json
{
  "id": 100,
  "sourceId": 1,
  "sourceName": "科技新闻",
  "title": "...",
  "link": "https://example.com/article/100",
  "coverImage": "http://...",
  "pubDate": "2026-01-12T10:00:00",
  "wordCount": 1200
}
```

---

## 6. 关键设计点

### 6.1 鉴权与用户上下文

- 前台接口受 `FrontJwtFilter` 保护
- 通过 `UserContext`（ScopedValue）获取当前用户 `userId`/`username`

### 6.2 幂等与并发安全

- 主题创建：先按 content 查库，保存时依赖唯一索引兜底，捕获并发重复创建
- 订阅创建：优先查是否已存在订阅；并在数据库层使用唯一约束兜底

### 6.3 性能与查询策略

- Feed 流只返回必要的元信息字段，避免读取文章大字段
- Topic 订阅数量通过 `subscription_limit` 限制，避免动态 OR 条件过长导致性能下降
- 阈值 `feed_similarity_threshold` 可动态调整，用于平衡召回与噪声