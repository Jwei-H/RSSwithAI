# RSSwithAI 前台 API 文档

## 概述

本文档描述 RSSwithAI 系统面向前台用户（Front）的 REST API 接口。

**Base URL**: `http://localhost:8080`

**认证方式**: JWT Bearer Token

**数据格式**: JSON

---

## 目录

1. [用户管理模块](#用户管理模块)
2. [订阅系统模块](#订阅系统模块)
3. [文章模块](#文章模块)
4. [热点趋势模块](#热点趋势模块)

---

## 通用说明

### 认证

需要认证的接口需要在请求头中携带 JWT Token：

```
Authorization: Bearer <your-token>
```


### 请求格式

- **Content-Type**：`application/json`
- **字符编码**：UTF-8

### 响应格式

所有接口返回 JSON 格式数据。

#### 成功响应

```json
{
  "data": { ... }
}
```

或直接返回数据对象。

#### 错误响应

```json
{
  "timestamp": "2025-12-27T10:00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "错误描述信息",
  "path": "/api/admin/v1/xxx"
}
```

### 分页参数

支持 Spring Data JPA 的分页参数：

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| page | Integer | 0 | 页码（从0开始） |
| size | Integer | 20 | 每页数量 |
| sort | String | - | 排序字段，格式：`field,dir`（如：`createdAt,desc`） |

示例：

```
GET /api/admin/v1/articles?page=0&size=20&sort=pubDate,desc
```

### 分页响应

```json
{
  "content": [
    ...
  ],
  "empty": false,
  "first": true,
  "last": false,
  "number": 0,
  "numberOfElements": 20,
  "pageable": {
    "offset": 0,
    "pageNumber": 0,
    "pageSize": 20,
    "paged": true,
    "sort": {
      "empty": true,
      "sorted": false,
      "unsorted": true
    },
    "unpaged": false
  },
  "size": 20,
  "sort": {
    "empty": true,
    "sorted": false,
    "unsorted": true
  },
  "totalElements": 2248,
  "totalPages": 113
}
```


---

## 用户管理模块

### 1. 用户注册

创建新用户账号。

**接口**: `POST /api/register`

**认证**: 不需要

**请求体**:

```json
{
  "username": "string",
  "password": "string"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| username | string | 是 | 用户名 |
| password | string | 是 | 密码 |

**响应** (200 OK):

```json
{
  "id": 1,
  "username": "string",
  "avatarUrl": "string"
}
```

**业务规则**:
- 同一 IP 在 24 小时内最多注册 10 个账号
- 用户名不能重复
- 密码使用 MD5 加密存储
- 新用户使用系统默认头像（通过 `default_avatar` 配置项设置）

**错误示例**:

```json
{
  "message": "Registration limit exceeded for this IP address (max 10 per 24h)"
}
```

```json
{
  "message": "Username already exists"
}
```

---

### 2. 用户登录

使用用户名和密码登录，获取 JWT Token。

**接口**: `POST /api/login`

**认证**: 不需要

**请求体**:

```json
{
  "username": "string",
  "password": "string"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| username | string | 是 | 用户名 |
| password | string | 是 | 密码 |

**响应** (200 OK):

```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**业务规则**:
- Token 有效期为 1 个月
- Token 包含 `userId` 和 `username` 信息

**错误示例**:

```json
{
  "message": "User not found or invalid credentials"
}
```

---

### 3. 刷新 Token

使用有效的 Token 获取新的 Token。

**接口**: `POST /api/refresh`

**认证**: 需要

**请求头**:

```
Authorization: Bearer <your-token>
```

**响应** (200 OK):

```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**错误示例** (401 Unauthorized):

```
Invalid token
```

---

### 4. 获取个人信息

获取当前登录用户的个人信息。

**接口**: `GET /api/user/profile`

**认证**: 需要

**响应** (200 OK):

```json
{
  "id": 1,
  "username": "string",
  "avatarUrl": "string"
}
```

---

### 5. 修改用户名

修改当前登录用户的用户名。

**接口**: `PUT /api/user/username`

**认证**: 需要

**请求体**:

```json
{
  "newUsername": "string"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| newUsername | string | 是 | 新用户名 |

**响应** (200 OK):

```json
{
  "id": 1,
  "username": "newUsername",
  "avatarUrl": "string"
}
```

**业务规则**:
- 新用户名不能与已有用户名重复

**错误示例**:

```json
{
  "message": "Username already exists"
}
```

---

### 6. 修改密码

修改当前登录用户的密码。

**接口**: `PUT /api/user/password`

**认证**: 需要

**请求体**:

```json
{
  "oldPassword": "string",
  "newPassword": "string"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| oldPassword | string | 是 | 原密码 |
| newPassword | string | 是 | 新密码 |

**响应** (200 OK):

```
(空响应体)
```

**错误示例**:

```json
{
  "message": "Invalid old password"
}
```

---

## 订阅系统模块

### 1. 获取 RSS 源列表

获取所有可用的 RSS 源列表，仅显示已启用的源，并标记当前用户是否已订阅。

**接口**: `GET /api/front/v1/rss-sources`

**认证**: 需要

**查询参数**:

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| page | int | 否 | 0 | 页码（从0开始） |
| size | int | 否 | 20 | 每页大小 |
| sort | string | 否 | id,desc | 排序字段和方向 |
| category | enum | 否 | - | 源分类（NEWS/TECH/SOCIETY/FINANCE/LIFESTYLE/OTHER） |

**响应** (200 OK):

```json
{
  "content": [
    {
      "id": 1,
      "name": "科技新闻",
      "link": "https://example.com",
      "category": "TECH",
      "isSubscribed": true,
      "subscriptionId": 88
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20,
    "sort": {
      "sorted": true,
      "unsorted": false,
      "empty": false
    }
  },
  "totalPages": 5,
  "totalElements": 100,
  "last": false,
  "size": 20,
  "number": 0,
  "sort": {
    "sorted": true,
    "unsorted": false,
    "empty": false
  },
  "first": true,
  "numberOfElements": 20,
  "empty": false
}
```

**枚举值 - SourceCategory**:
- `NEWS`: 新闻
- `TECH`: 科技
- `SOCIETY`: 社会
- `FINANCE`: 财经
- `LIFESTYLE`: 生活
- `OTHER`: 其他

---

### 2. 创建主题

创建语义主题，系统会自动生成主题向量。

**接口**: `POST /api/front/v1/topics`

**认证**: 需要

**请求体**:

```json
{
  "content": "string"
}
```

| 字段 | 类型 | 必填 | 约束      | 说明 |
|------|------|------|---------|------|
| content | string | 是 | 长度 ≤ 30 | 主题内容 |

**响应** (200 OK):

```json
{
  "id": 10,
  "content": "国外AI人才动向",
  "createdAt": "2026-01-12T10:00:00"
}
```

**业务规则**:
- 主题长度限制为 30 字符
- 如果内容已存在，会复用已有主题（幂等操作）
- 主题创建后不可修改
- 主题会自动生成 1024 维向量

**错误示例**:

```json
{
  "message": "content cannot be blank"
}
```

```json
{
  "message": "Topic content length must be within 30 characters"
}
```

```json
{
  "message": "Failed to generate topic vector"
}
```

---

### 3. 创建订阅

订阅 RSS 源或主题。

**接口**: `POST /api/front/v1/subscriptions`

**认证**: 需要

**请求体**:

```json
{
  "type": "RSS",
  "targetId": 1
}
```

| 字段 | 类型 | 必填 | 约束 | 说明 |
|------|------|------|------|------|
| type | enum | 是 | - | 订阅类型（RSS/TOPIC） |
| targetId | long | 是 | 正数 | RSS源ID或主题ID |

**枚举值 - SubscriptionType**:
- `RSS`: RSS 源订阅
- `TOPIC`: 主题订阅

**响应** (200 OK):

```json
{
  "id": 100,
  "type": "RSS",
  "targetId": 1,
  "name": "科技新闻",
  "link": "https://example.com",
  "category": "TECH",
  "content": null,
  "createdAt": "2026-01-12T10:00:00"
}
```

**业务规则**:
- 如果用户已订阅同一目标，直接返回已有订阅（幂等操作）
- 单个用户订阅数量上限由 `subscription_limit` 配置项控制（默认 20）
- RSS 订阅返回源的 name、link、category
- Topic 订阅返回主题的 content

**错误示例**:

```json
{
  "message": "Subscription type is required"
}
```

```json
{
  "message": "targetId must be positive"
}
```

```json
{
  "message": "RSS source not found: 1"
}
```

```json
{
  "message": "Topic not found: 10"
}
```

```json
{
  "message": "Subscription limit reached: 20"
}
```

---

### 4. 取消订阅

取消指定的订阅。

**接口**: `DELETE /api/front/v1/subscriptions/{id}`

**认证**: 需要

**路径参数**:

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | long | 是 | 订阅ID |

**响应** (204 No Content):

```
(空响应体)
```

**错误示例**:

```json
{
  "message": "Subscription not found: 100"
}
```

---

### 5. 获取订阅列表

获取当前用户的所有订阅列表。

**接口**: `GET /api/front/v1/subscriptions`

**认证**: 需要

**响应** (200 OK):

```json
[
  {
    "id": 100,
    "type": "RSS",
    "targetId": 1,
    "name": "科技新闻",
    "link": "https://example.com",
    "category": "TECH",
    "content": null,
    "createdAt": "2026-01-12T10:00:00"
  },
  {
    "id": 101,
    "type": "TOPIC",
    "targetId": 10,
    "name": null,
    "link": null,
    "category": null,
    "content": "国外AI人才动向",
    "createdAt": "2026-01-12T11:00:00"
  }
]
```

---

### 6. 获取时间线（混合 Feed）

获取用户的时间线，支持聚合时间线和单订阅过滤。

**接口**: `GET /api/front/v1/articles/feed`

**认证**: 需要

**查询参数**:

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| subscriptionId | long | 否 | - | 订阅ID（不传表示聚合所有订阅） |
| cursor | string | 否 | - | 游标，格式：`pubDate,articleId` |
| size | int | 否 | 20 | 每页大小（最大100） |

**游标格式**:
- 格式：`ISO日期时间,文章ID`
- 示例：`2026-01-12T10:00:00,500`

**响应** (200 OK):

```json
[
  {
    "id": 100,
    "sourceId": 1,
    "sourceName": "科技新闻",
    "title": "OpenAI发布Sora",
    "coverImage": "https://example.com/cover.jpg",
    "pubDate": "2026-01-12T10:00:00",
    "wordCount": 1200
  }
]
```

**业务规则**:
- 时间线由 RSS 订阅和主题语义匹配混合组成
- RSS 匹配：直接匹配订阅的 RSS 源
- 主题匹配：基于向量相似度匹配（使用 pgvector `<=>` 计算距离）
- 相似度阈值由 `feed_similarity_threshold` 配置项控制（默认 0.3）
- 排序：按 `pub_date DESC, id DESC` 排序
- 游标分页：基于复合游标 `(pubDate, articleId)` 实现高效分页
- 如果没有订阅或订阅源无文章，返回空列表

**游标分页逻辑**:
- 首次请求：不传 `cursor`，返回第一页数据
- 下一页：使用上一页最后一条数据的 `pubDate` 和 `id` 作为 `cursor`
- 游标条件：`(pub_date < cursorTime) OR (pub_date = cursorTime AND id < cursorId)`

---

## 文章模块

### 1. 按 RSS 源分页获取文章（预览）

用于预览指定 RSS 源的最新文章，帮助用户决定是否订阅。

**接口**: `GET /api/front/v1/articles/source/{sourceId}`

**认证**: 需要

**路径参数**:

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| sourceId | long | 是 | RSS 源 ID |

**查询参数**:

| 参数 | 类型 | 必填 | 默认值          | 说明 |
|------|------|------|--------------|------|
| page | int | 否 | 0            | 页码（从 0 开始） |
| size | int | 否 | 10           | 每页大小 |

**响应** (200 OK):

```json
{
  "content": [
    {
      "id": 100,
      "sourceId": 1,
      "sourceName": "科技新闻",
      "title": "OpenAI发布Sora",
      "coverImage": "https://example.com/cover.jpg",
      "pubDate": "2026-01-12T10:00:00"
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20
  },
  "totalElements": 120,
  "totalPages": 6,
  "last": false
}
```

**业务规则**:
- 返回体使用 `ArticleFeedDTO` 与 feed 接口保持一致
- 默认按发布时间倒序排序

### 2. 获取文章详情

获取指定文章的详细信息。

**接口**: `GET /api/front/v1/articles/{id}`

**认证**: 需要

**路径参数**:

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | long | 是 | 文章ID |

**响应** (200 OK):

```json
{
  "id": 100,
  "sourceId": 1,
  "sourceName": "科技新闻",
  "title": "OpenAI发布Sora",
  "link": "https://example.com/article",
  "description": "文章摘要",
  "content": "文章内容（Markdown格式）",
  "author": "张三",
  "pubDate": "2026-01-12T10:00:00",
  "categories": "AI,科技",
  "wordCount": 1500,
  "coverImage": "https://example.com/cover.jpg",
  "isFavorite": false,
  "fetchedAt": "2026-01-12T10:05:00",
  "createdAt": "2026-01-12T10:05:00"
}
```

**错误示例**:

```json
{
  "message": "文章不存在: 100"
}
```

---

### 3. 获取文章增强信息

获取文章的 AI 增强信息（概览、关键信息、标签）。

**接口**: `GET /api/front/v1/articles/{id}/extra`

**认证**: 需要

**路径参数**:

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | long | 是 | 文章ID |

**响应** (200 OK):

```json
{
  "id": 1,
  "articleId": 100,
  "overview": "OpenAI发布了名为Sora的AI视频生成模型，能够根据文本描述生成长达一分钟的高清视频。",
  "keyInformation": [
    "Sora可生成最长1分钟的高清视频",
    "支持复杂的场景和角色一致性",
    "目前处于测试阶段"
  ],
  "tags": [
    "OpenAI",
    "Sora",
    "AI视频生成",
    "人工智能",
    "科技"
  ],
  "status": "SUCCESS",
  "errorMessage": null,
  "createdAt": "2026-01-12T10:06:00",
  "updatedAt": "2026-01-12T10:06:00"
}
```

**枚举值 - AnalysisStatus**:
- `SUCCESS`: 处理成功
- `FAILED`: 处理失败

**业务规则**:
- 如果文章没有增强信息或处理失败，返回 404 Not Found
- 前台用户无法看到失败时的错误信息（`errorMessage` 仅后台可见）

**错误示例** (404 Not Found):

```
(空响应体)
```

---

### 4. 智能搜索文章

基于关键词模糊匹配和向量相似度的混合搜索。

**接口**: `GET /api/front/v1/articles/search`

**认证**: 可选（当 searchScope=SUBSCRIBED 或 FAVORITE 时需要）

**查询参数**:

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| query | string | 是 | 搜索关键词 |
| searchScope | string | 否 | 搜索范围：`ALL` (默认，全站搜索) / `SUBSCRIBED` (仅在已订阅RSS源中搜索) / `FAVORITE` (仅在已收藏的文章中搜索) |

**响应** (200 OK):

```json
[
  {
    "id": 100,
    "sourceId": 1,
    "sourceName": "科技新闻",
    "title": "OpenAI发布Sora",
    "coverImage": "https://example.com/cover.jpg",
    "pubDate": "2026-01-12T10:00:00",
    "wordCount": 1200
  }
]
```

**业务规则**:
- 搜索关键词必填，不能为空
- 混合检索策略：
  1. **模糊匹配**：在标题和作者字段中进行左右模糊匹配，返回最多 20 条
  2. **向量相似度**：将查询词向量化，与文章向量计算相似度，返回最多 50 条
  3. **结果合并**：优先返回交集结果，然后是仅模糊匹配结果，最后是仅向量匹配结果
  4. **结果截断**：最终返回最多 20 条结果
- 如果向量生成失败，仅使用关键词模糊匹配
- 当 `searchScope=FAVORITE` 且当前用户没有收藏时，返回空列表

**错误示例**:

```json
{
  "message": "Search query cannot be blank"
}
```

---

### 5. 获取相似文章推荐

基于向量相似度推荐相似文章。

**接口**: `GET /api/front/v1/articles/{id}/recommendations`

**认证**: 需要

**路径参数**:

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | long | 是 | 文章ID |

**响应** (200 OK):

```json
[
  {
    "id": 101,
    "sourceId": 1,
    "sourceName": "科技新闻",
    "title": "Google发布Gemini 2.0",
    "coverImage": "https://example.com/cover2.jpg",
    "pubDate": "2026-01-12T09:00:00"
  },
  {
    "id": 102,
    "sourceId": 2,
    "sourceName": "AI前沿",
    "title": "AI视频生成技术发展历程",
    "coverImage": "https://example.com/cover3.jpg",
    "pubDate": "2026-01-11T15:00:00"
  }
]
```

**业务规则**:
- 最多返回 2 条相似文章
- 如果当前文章没有有效的向量数据，返回空列表
- 基于文章向量的余弦相似度计算

**错误示例**:

```json
{
  "message": "文章不存在: 100"
}
```

```json
{
  "message": "articleId must be positive"
}
```

### 6. 收藏文章

收藏指定文章。

**接口**: `POST /api/front/v1/articles/{id}/favorite`

**认证**: 需要

**路径参数**:

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | long | 是 | 文章ID |

**响应** (200 OK):

```
(空响应体)
```

**业务规则**:
- 幂等：重复收藏不会报错
- 若文章不存在，返回 404
- `articleId` 需为正数

---

### 7. 取消收藏文章

取消对指定文章的收藏。

**接口**: `DELETE /api/front/v1/articles/{id}/favorite`

**认证**: 需要

**路径参数**:

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | long | 是 | 文章ID |

**响应** (204 No Content):

```
(空响应体)
```

**业务规则**:
- 幂等：未收藏时删除也返回 204
- `articleId` 需为正数

---

### 8. 获取收藏列表

分页查看当前用户收藏的文章，按发布时间倒序。

**接口**: `GET /api/front/v1/articles/favorites`

**认证**: 需要

**查询参数**:

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|------|------|
| page | int | 否 | 0 | 页码（从 0 开始） |
| size | int | 否 | 10 | 每页大小 |
| sort | string | 否 | pubDate,desc | 排序字段，默认按发布时间倒序 |

**响应** (200 OK):

```json
{
  "content": [
    {
      "id": 100,
      "sourceId": 1,
      "sourceName": "科技新闻",
      "title": "OpenAI发布Sora",
      "coverImage": "https://example.com/cover.jpg",
      "pubDate": "2026-01-12T10:00:00",
      "wordCount": 1200
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 10
  },
  "totalElements": 1,
  "totalPages": 1,
  "last": true
}
```

**业务规则**:
- 返回体使用 `ArticleFeedDTO`
- 默认按 `pubDate` 倒序排序
- 若无收藏记录，返回空列表

## 热点趋势模块

### 1. 获取词云

获取关键词云，支持按指定源或聚合用户订阅。

**接口**: `GET /api/front/v1/trends/wordcloud`

**认证**: 需要

**查询参数**:

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| sourceId | long | 否 | RSS源ID（不传则聚合用户所有订阅） |

**响应** (200 OK):

```json
[
  {
    "text": "AI",
    "value": 50
  },
  {
    "text": "Spring Boot",
    "value": 30
  },
  {
    "text": "OpenAI",
    "value": 25
  }
]
```

**业务规则**:
- 如果指定 `sourceId`，返回该源的词云
- 如果不指定 `sourceId`，聚合用户所有订阅源的词云
- 词云数据由后台定时任务生成（使用 LLM 进行同义词清洗）
- 返回结果按词频降序排列
- 如果没有数据，返回空列表

---

### 2. 获取热点事件

获取全网热点事件榜单。

**接口**: `GET /api/front/v1/trends/hotevents`

**认证**: 需要

**响应** (200 OK):

```json
[
  {
    "event": "OpenAI发布Sora",
    "score": 10,
    "isSubscribed": false
  },
  {
    "event": "Java 25发布",
    "score": 9,
    "isSubscribed": true
  },
  {
    "event": "Google推出Gemini 2.0",
    "score": 8,
    "isSubscribed": false
  }
]
```

**业务规则**:
- 返回全网热点事件榜单
- 热点事件由后台定时任务生成（使用 Map-Reduce 架构）
- 事件按热度打分排序（1-10分）
- `isSubscribed` 表示当前用户是否已订阅该事件（基于 Topic 订阅）
- 如果没有数据，返回空列表

---

## 附录

### 枚举类型汇总

#### SubscriptionType（订阅类型）
- `RSS`: RSS 源订阅
- `TOPIC`: 主题订阅

#### SourceCategory（RSS源分类）
- `NEWS`: 新闻
- `TECH`: 科技
- `SOCIETY`: 社会
- `FINANCE`: 财经
- `LIFESTYLE`: 生活
- `OTHER`: 其他

#### AnalysisStatus（分析状态）
- `SUCCESS`: 处理成功
- `FAILED`: 处理失败

### 时间格式

所有时间字段使用 ISO 8601 格式：

```
2026-01-12T10:00:00
```

### 分页游标格式

游标格式：`ISO日期时间,文章ID`

示例：
```
2026-01-12T10:00:00,500
```