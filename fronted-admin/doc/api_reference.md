# RSSwithAI 后台管理系统 API 参考文档

## 目录

1. [项目概述](#项目概述)
2. [系统架构](#系统架构)
3. [核心交互流程](#核心交互流程)
4. [认证说明](#认证说明)
5. [通用说明](#通用说明)
6. [API 接口文档](#api-接口文档)
   - [用户认证](#用户认证)
   - [RSS源管理](#rss源管理)
   - [文章管理](#文章管理)
   - [模型配置管理](#模型配置管理)
   - [提示词模板管理](#提示词模板管理)
   - [实验管理](#实验管理)
   - [分析结果管理](#分析结果管理)
   - [系统配置管理](#系统配置管理)

---

## 项目概述

RSSwithAI 是一个基于 RSS 信息源的智能情报收集、分析与展示系统。系统将前沿的 AI 分析能力与成熟的信息聚合技术有效结合，构建从信息采集、智能分析到最终展示的全流程自动化系统。

### 系统参与者

- **系统管理员**：负责 RSS 源管理、模型配置与系统监控
- **研究分析人员**：关注 Prompt 模板优化、AI 效果测试与情报质量评估
- **情报消费者**：决策者、分析师、研究人员，专注快速、精准获取有价值的情报内容

### 系统特点

- **三层架构**：采集（Collector）→ 分析（Analyzer）→ 展示（Presenter）
- **AI 驱动**：集成大语言模型进行内容增强和智能分析
- **版本管理**：支持 Prompt 模板的版本控制和锁定机制
- **实验追踪**：完整的实验记录和分析结果追溯
- **异步处理**：使用虚拟线程实现高并发处理

---

## 系统架构

### 技术栈

- **后端框架**：Spring Boot 4
- **开发语言**：Java 25
- **AI 集成**：Spring AI
- **数据库**：PostgreSQL 17 with pgvector
- **设计模式**：DDD 领域驱动设计、RESTful API

### 核心模块

1. **信息收集器（Collector）**
   - RSS 源管理
   - 定时调度抓取
   - 内容解析与去重

2. **智能分析器（Analyzer）**
   - 内容增强处理（概览、关键信息、标签）
   - 向量化处理
   - 实验分析

3. **配置管理**
   - 模型配置管理
   - Prompt 模板版本管理
   - 系统配置管理

---

## 核心交互流程

### 1. RSS 文章采集流程

```
定时调度器 → 检查需要抓取的源 → 并发抓取 → 解析RSS → 去重保存 → 发布文章处理事件
```

**详细步骤：**

1. **RssSchedulerService** 每秒检查一次需要抓取的 RSS 源
2. 筛选出满足抓取间隔条件的启用的源
3. 使用虚拟线程并发抓取
4. **RssFetcherService** 执行 HTTP 请求获取 RSS 内容
5. 使用 **RssUtils** 解析 RSS/XML 格式数据
6. 通过 GUID 或 Link 进行去重
7. 保存新文章到数据库
8. 发布 **ArticleProcessEvent** 事件

### 2. 文章内容增强流程

```
文章处理事件 → LlmProcessService监听 → 获取信号量许可 → 调用LLM生成内容 → 生成向量 → 保存结果
```

**详细步骤：**

1. **LlmProcessService** 监听 **ArticleProcessEvent** 事件
2. 获取信号量许可（控制并发数）
3. 检查文章是否已处理过
4. 调用语言模型生成：
   - 文章概览（80字以内，支持 Markdown）
   - 关键信息（1-3条，每条40字以内）
   - 标签（5个左右）
5. 拼接概览和关键信息，调用 embedding 模型生成向量
6. 保存结果到 **article_extra** 表

### 3. 实验分析流程

```
创建实验 → 验证配置 → 异步执行 → 遍历文章 → 调用LLM分析 → 保存分析结果
```

**详细步骤：**

1. 用户选择文章、模型配置和已锁定的 Prompt 版本
2. 创建实验，状态为 **RUNNING**
3. 使用虚拟线程异步执行实验
4. 遍历所有选中的文章
5. 使用 Prompt 模板渲染提示词
6. 调用 LLM 进行分析
7. 记录：
   - 模型配置参数
   - 实际使用的提示词
   - 模型输出结果
   - Token 消耗（输入/输出）
   - 执行耗时
8. 保存分析结果到 **analysis_results** 表
9. 更新实验状态为 **COMPLETED** 或 **FAILED**

### 4. 配置更新流程

```
修改配置 → 保存到数据库 → 更新AppConfig → 发布ConfigUpdateEvent → 服务监听并重建客户端
```

**详细步骤：**

1. 用户通过 **SettingsController** 修改配置
2. 配置保存到 **settings** 表
3. **SettingsService** 更新 **AppConfig** 的值
4. 发布 **ConfigUpdateEvent** 事件
5. **LlmProcessService** 监听事件，重建 OpenAI API 客户端
6. 调整信号量大小以匹配新的并发限制

---

## 认证说明

### JWT Token 认证

系统使用 JWT（JSON Web Token）进行身份认证，除登录接口外，所有 `/api/admin/**` 路径下的接口都需要携带有效的 JWT Token。

#### 获取 Token

通过登录接口获取：

```http
POST /api/admin/login
Content-Type: application/json

{
  "username": "admin",
  "password": "password"
}
```

成功响应：

```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

#### 使用 Token

在请求头中添加 Authorization 字段：

```http
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

#### Token 刷新

Token 有效期为 1 个月，过期前可通过刷新接口获取新 Token：

```http
POST /api/admin/refresh
Authorization: Bearer <old_token>
```

#### 认证拦截器

- **拦截范围**：`/api/admin/**`
- **排除路径**：`/api/admin/login`
- **验证逻辑**：验证 Token 有效性（签名、过期时间）

---

## 通用说明

### 基础 URL

```
http://<host>:<port>/api/admin
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

## API 接口文档

### 用户认证

#### 登录

管理员登录，获取 JWT Token。

**接口地址：** `POST /api/admin/login`

**请求参数：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| username | String | 是 | 用户名 |
| password | String | 是 | 密码 |

**请求示例：**

```json
{
  "username": "admin",
  "password": "admin123"
}
```

**响应示例：**

```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTczNTI5OTIwMCwiZXhwIjoxNzM3ODkxMjAwfQ.signature"
}
```

**状态码：**

- 200：登录成功
- 401：用户名或密码错误

---

#### 刷新 Token

使用有效的 Token 刷新获取新 Token。

**接口地址：** `POST /api/admin/refresh`

**请求头：**

```
Authorization: Bearer <token>
```

**响应示例：**

```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**状态码：**

- 200：刷新成功
- 401：Token 无效或已过期

---

### RSS源管理

RSS 源管理接口，用于管理 RSS 订阅源的配置和抓取。

#### 获取 RSS 源统计信息

**接口地址：** `GET /api/admin/v1/rss-sources/stats`

**响应示例：**

```json
{
  "total": 10,
  "statusCounts": {
    "SUCCESS": 8,
    "FAILED": 2,
    "NEVER": 0,
    "FETCHING": 0
  }
}
```

#### 获取所有 RSS 源

**接口地址：** `GET /api/admin/v1/rss-sources`

**请求参数：**

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| page | Integer | 0 | 页码 |
| size | Integer | 20 | 每页数量 |

**响应示例：**

```json
{
  "content": [
    {
      "id": 1,
      "name": "Tech News",
      "url": "https://example.com/rss",
      "type": "ORIGIN",
      "description": "科技新闻源",
      "icon": "https://example.com/icon.png",
      "fetchIntervalMinutes": 30,
      "status": "ENABLED",
      "category": "TECH",
      "lastFetchStatus": "SUCCESS",
      "lastFetchTime": "2025-12-27T10:00:00",
      "lastFetchError": null,
      "failureCount": 0,
      "createdAt": "2025-12-01T00:00:00",
      "updatedAt": "2025-12-27T10:00:00"
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

---

#### 获取单个 RSS 源详情

**接口地址：** `GET /api/admin/v1/rss-sources/{id}`

**路径参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Long | 是 | RSS 源 ID |

**响应示例：**

```json
{
  "id": 1,
  "name": "Tech News",
  "url": "https://example.com/rss",
  "type": "ORIGIN",
  "description": "科技新闻源",
  "fetchIntervalMinutes": 30,
  "status": "ENABLED",
  "category": "TECH",
  "lastFetchStatus": "SUCCESS",
  "lastFetchTime": "2025-12-27T10:00:00",
  "lastFetchError": null,
  "failureCount": 0,
  "createdAt": "2025-12-01T00:00:00",
  "updatedAt": "2025-12-27T10:00:00"
}
```

---

#### 创建 RSS 源

**接口地址：** `POST /api/admin/v1/rss-sources`

**请求参数：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| name | String | 是 | RSS 源名称 |
| url | String | 是 | RSS 源 URL 或 RSSHub 路由 |
| type | SourceType | 是 | 源类型（ORIGIN 或 RSSHUB） |
| description | String | 否 | 描述 |
| icon | String | 否 | RSS 源图标 URL |
| fetchIntervalMinutes | Integer | 否 | 抓取间隔（分钟），默认 30 |
| category | SourceCategory | 否 | 源分类，默认 OTHER |

**SourceType 枚举值：**

- `ORIGIN`：原始 RSS 源
- `RSSHUB`：RSSHub 源

**SourceCategory 枚举值：**

- `NEWS`：新闻
- `TECH`：科技
- `SOCIETY`：社会
- `FINANCE`：财经
- `LIFESTYLE`：生活
- `OTHER`：其他（默认）

**请求示例：**

```json
{
  "name": "Tech News",
  "url": "https://example.com/rss",
  "type": "ORIGIN",
  "description": "科技新闻源",
  "icon": "https://example.com/icon.png",
  "fetchIntervalMinutes": 30,
  "category": "TECH"
}
```

**响应示例：**

```json
{
  "id": 1,
  "name": "Tech News",
  "url": "https://example.com/rss",
  "type": "ORIGIN",
  "description": "科技新闻源",
  "icon": "https://example.com/icon.png",
  "fetchIntervalMinutes": 30,
  "status": "ENABLED",
  "category": "TECH",
  "lastFetchStatus": "NEVER",
  "lastFetchTime": null,
  "lastFetchError": null,
  "failureCount": 0,
  "createdAt": "2025-12-27T10:00:00",
  "updatedAt": "2025-12-27T10:00:00"
}
```

**状态码：**

- 201：创建成功

---

#### 更新 RSS 源

**接口地址：** `PUT /api/admin/v1/rss-sources/{id}`

**路径参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Long | 是 | RSS 源 ID |

**请求参数：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| name | String | 否 | RSS 源名称 |
| url | String | 否 | RSS 源 URL |
| type | SourceType | 否 | 源类型 |
| description | String | 否 | 描述 |
| icon | String | 否 | RSS 源图标 URL |
| fetchIntervalMinutes | Integer | 否 | 抓取间隔（分钟） |
| status | SourceStatus | 否 | 源状态（ENABLED 或 DISABLED） |
| category | SourceCategory | 否 | 源分类 |

**SourceStatus 枚举值：**

- `ENABLED`：启用
- `DISABLED`：禁用

**请求示例：**

```json
{
  "name": "Updated Tech News",
  "description": "更新后的描述",
  "status": "ENABLED",
  "category": "TECH"
}
```

**响应示例：**

```json
{
  "id": 1,
  "name": "Updated Tech News",
  "url": "https://example.com/rss",
  "type": "ORIGIN",
  "description": "更新后的描述",
  "icon": "https://example.com/icon.png",
  "fetchIntervalMinutes": 30,
  "status": "ENABLED",
  "category": "TECH",
  "lastFetchStatus": "SUCCESS",
  "lastFetchTime": "2025-12-27T10:00:00",
  "lastFetchError": null,
  "failureCount": 0,
  "createdAt": "2025-12-01T00:00:00",
  "updatedAt": "2025-12-27T11:00:00"
}
```

---

#### 删除 RSS 源

**接口地址：** `DELETE /api/admin/v1/rss-sources/{id}`

**路径参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Long | 是 | RSS 源 ID |

**状态码：**

- 204：删除成功

---

#### 启用 RSS 源

**接口地址：** `POST /api/admin/v1/rss-sources/{id}/enable`

**路径参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Long | 是 | RSS 源 ID |

**响应示例：**

```json
{
  "id": 1,
  "name": "Tech News",
  "url": "https://example.com/rss",
  "type": "ORIGIN",
  "description": "科技新闻源",
  "fetchIntervalMinutes": 30,
  "status": "ENABLED",
  ...
}
```

---

#### 禁用 RSS 源

**接口地址：** `POST /api/admin/v1/rss-sources/{id}/disable`

**路径参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Long | 是 | RSS 源 ID |

**响应示例：**

```json
{
  "id": 1,
  "name": "Tech News",
  "url": "https://example.com/rss",
  "type": "ORIGIN",
  "description": "科技新闻源",
  "fetchIntervalMinutes": 30,
  "status": "DISABLED",
  ...
}
```

---

#### 手动触发抓取指定 RSS 源

**接口地址：** `POST /api/admin/v1/rss-sources/{id}/fetch`

**路径参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Long | 是 | RSS 源 ID |

**状态码：**

- 202：抓取任务已接受（异步执行）

---

#### 手动触发抓取所有启用的 RSS 源

**接口地址：** `POST /api/admin/v1/rss-sources/fetch-all`

**状态码：**

- 202：抓取任务已接受（异步执行）

---

### 文章管理

文章管理接口，用于查询从 RSS 源抓取的文章及其增强信息。

#### 获取文章统计信息

**接口地址：** `GET /api/admin/v1/articles/stats`

**响应示例：**
返回总文章数和七日内新增文章数
```json
{
  "total": 1000,
  "dailyCounts": [
    {
      "date": "2023-10-20",
      "count": 50
    },
    {
      "date": "2023-10-21",
      "count": 60
    }, 
     ...
  ]
}
```

#### 获取文章列表

**接口地址：** `GET /api/admin/v1/articles`

**请求参数：**

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| page | Integer | 0 | 页码 |
| size | Integer | 20 | 每页数量 |
| searchWord | String | null | 搜索关键词，支持对标题和作者的左右模糊匹配 |

**响应示例：**

```json
{
  "content": [
    {
      "id": 1,
      "sourceId": 1,
      "sourceName": "Tech News",
      "title": "AI 技术突破",
      "link": "https://example.com/article/1",
      "guid": "article-1",
      "description": null,
      "rawContent": null,
      "content": null,
      "author": "John Doe",
      "pubDate": "2025-12-27T09:00:00",
      "categories": "AI,Technology",
      "wordCount": 1500,
      "fetchedAt": "2025-12-27T10:00:00",
      "createdAt": "2025-12-27T10:00:00"
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20,
    "totalElements": 100,
    "totalPages": 5
  }
}
```

**注意：** 列表接口不返回 `description`、`rawContent`、`content` 字段，以减少数据传输量。
结果按照pubDate倒序返回
---

#### 获取单个文章详情

**接口地址：** `GET /api/admin/v1/articles/{id}`

**路径参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Long | 是 | 文章 ID |

**响应示例：**

```json
{
  "id": 1,
  "sourceId": 1,
  "sourceName": "Tech News",
  "title": "AI 技术突破",
  "link": "https://example.com/article/1",
  "guid": "article-1",
  "description": "文章摘要",
  "rawContent": "<div>HTML内容</div>",
  "content": "Markdown内容",
  "author": "John Doe",
  "pubDate": "2025-12-27T09:00:00",
  "categories": "AI,Technology",
  "wordCount": 1500,
  "coverImage": "https://example.com/image.jpg",
  "fetchedAt": "2025-12-27T10:00:00",
  "createdAt": "2025-12-27T10:00:00"
}
```

---

#### 获取指定 RSS 源的文章

**接口地址：** `GET /api/admin/v1/articles/source/{sourceId}`

**路径参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| sourceId | Long | 是 | RSS 源 ID |

**请求参数：**

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| page | Integer | 0 | 页码 |
| size | Integer | 20 | 每页数量 |
| searchWord | String | null | 搜索关键词，支持对标题和作者的左右模糊匹配 |

**响应示例：**

```json
{
  "content": [ ... ],
  "pageable": { ... }
}
```

---

#### 获取文章增强信息

获取文章的 AI 增强处理结果，包括概览、关键信息、标签等。

**接口地址：** `GET /api/admin/v1/articles/{id}/extra`

**路径参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Long | 是 | 文章 ID |

**响应示例：**

```json
{
  "id": 1,
  "articleId": 1,
  "overview": "AI技术在自然语言处理领域取得重大突破",
  "keyInformation": [
    "模型性能提升30%",
    "推理速度提高2倍"
  ],
  "tags": ["AI", "NLP", "技术突破"],
  "status": "SUCCESS",
  "errorMessage": null,
  "createdAt": "2025-12-27T10:05:00",
  "updatedAt": "2025-12-27T10:05:00"
}
```

**状态码：**

- 200：成功
- 404：文章增强信息不存在

---

#### 重新生成文章增强信息

重新生成文章的 AI 增强处理结果。该接口为阻塞调用，可能需要 2-3 秒。

**接口地址：** `POST /api/admin/v1/articles/{id}/extra/regenerate`

**路径参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Long | 是 | 文章 ID |

**响应示例：**

```json
{
  "id": 1,
  "articleId": 1,
  "overview": "AI技术在自然语言处理领域取得重大突破",
  "keyInformation": [
    "模型性能提升30%",
    "推理速度提高2倍"
  ],
  "tags": ["AI", "NLP", "技术突破"],
  "status": "SUCCESS",
  "errorMessage": null,
  "createdAt": "2025-12-27T10:05:00",
  "updatedAt": "2025-12-27T10:10:00"
}
```

**状态码：**

- 200：成功
- 404：文章不存在

---

### 模型配置管理

模型配置管理接口，用于管理 LLM 模型的配置参数。

#### 获取所有模型配置

**接口地址：** `GET /api/admin/v1/model-configs`

**请求参数：**

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| page | Integer | 0 | 页码 |
| size | Integer | 20 | 每页数量 |

**响应示例：**

```json
{
  "content": [
    {
      "id": 1,
      "name": "GPT-4 Config",
      "description": "GPT-4 高配置",
      "modelId": "gpt-4",
      "temperature": 0.7,
      "topP": 0.9,
      "topK": null,
      "maxTokens": 2000,
      "seed": 42,
      "createdAt": "2025-12-01T00:00:00",
      "updatedAt": "2025-12-27T10:00:00"
    }
  ],
  "pageable": { ... }
}
```

---

#### 创建模型配置

**接口地址：** `POST /api/admin/v1/model-configs`

**请求参数：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| name | String | 是 | 配置名称 |
| description | String | 否 | 详细描述 |
| modelId | String | 是 | 模型 ID |
| temperature | Double | 否 | 温度参数（0.0 - 2.0） |
| topP | Double | 否 | Top P 参数（0.0 - 1.0） |
| topK | Integer | 否 | Top K 采样数量 |
| maxTokens | Integer | 否 | 最大生成长度 |
| seed | Long | 否 | 随机种子 |

**请求示例：**

```json
{
  "name": "GPT-4 Config",
  "description": "GPT-4 高配置",
  "modelId": "gpt-4",
  "temperature": 0.7,
  "topP": 0.9,
  "maxTokens": 2000,
  "seed": 42
}
```

**响应示例：**

```json
{
  "id": 1,
  "name": "GPT-4 Config",
  "description": "GPT-4 高配置",
  "modelId": "gpt-4",
  "temperature": 0.7,
  "topP": 0.9,
  "topK": null,
  "maxTokens": 2000,
  "seed": 42,
  "createdAt": "2025-12-27T10:00:00",
  "updatedAt": "2025-12-27T10:00:00"
}
```

---

#### 获取模型配置详情

**接口地址：** `GET /api/admin/v1/model-configs/{id}`

**路径参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Long | 是 | 模型配置 ID |

**响应示例：**

```json
{
  "id": 1,
  "name": "GPT-4 Config",
  "description": "GPT-4 高配置",
  "modelId": "gpt-4",
  "temperature": 0.7,
  "topP": 0.9,
  "topK": null,
  "maxTokens": 2000,
  "seed": 42,
  "createdAt": "2025-12-01T00:00:00",
  "updatedAt": "2025-12-27T10:00:00"
}
```

---

#### 更新模型配置

**接口地址：** `PUT /api/admin/v1/model-configs/{id}`

**路径参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Long | 是 | 模型配置 ID |

**请求参数：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| name | String | 否 | 配置名称 |
| description | String | 否 | 详细描述 |
| modelId | String | 否 | 模型 ID |
| temperature | Double | 否 | 温度参数 |
| topP | Double | 否 | Top P 参数 |
| topK | Integer | 否 | Top K 采样数量 |
| maxTokens | Integer | 否 | 最大生成长度 |
| seed | Long | 否 | 随机种子 |

**请求示例：**

```json
{
  "name": "Updated GPT-4 Config",
  "temperature": 0.8
}
```

**响应示例：**

```json
{
  "id": 1,
  "name": "Updated GPT-4 Config",
  "description": "GPT-4 高配置",
  "modelId": "gpt-4",
  "temperature": 0.8,
  "topP": 0.9,
  "topK": null,
  "maxTokens": 2000,
  "seed": 42,
  "createdAt": "2025-12-01T00:00:00",
  "updatedAt": "2025-12-27T11:00:00"
}
```

---

#### 删除模型配置

**接口地址：** `DELETE /api/admin/v1/model-configs/{id}`

**路径参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Long | 是 | 模型配置 ID |

**状态码：**

- 204：删除成功

---

### 提示词模板管理

提示词模板管理接口，采用 Template（父级）+ Version（子级）的两级结构，支持版本控制和锁定机制。

#### 获取所有提示词模板

**接口地址：** `GET /api/admin/v1/prompts`

**请求参数：**

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| page | Integer | 0 | 页码 |
| size | Integer | 20 | 每页数量 |

**响应示例：**

```json
{
   "content": [
      {
         "id": 1,
         "name": "文章分析模板",
         "description": "用于分析文章内容的模板",
         "latestVersion": 2,
         "createdAt": "2025-12-01T00:00:00",
         "updatedAt": "2025-12-27T10:00:00",
         "latestVersionDetail": {
            "id": 2,
            "templateId": 1,
            "version": 2,
            "content": "请分析以下文章：{title}...",
            "immutable": true,
            "createdAt": "2025-12-27T10:00:00"
         }
      }
   ],
   "pageable": { ... }
}
```

---

#### 创建提示词模板

创建新的 Prompt 模板，自动创建 v1 版本（可修改）。

**接口地址：** `POST /api/admin/v1/prompts`

**请求参数：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| name | String | 是 | 模板名称（唯一） |
| description | String | 否 | 模板描述 |

**请求示例：**

```json
{
  "name": "文章分析模板",
  "description": "用于分析文章内容的模板"
}
```

**响应示例：**

```json
{
   "id": 1,
   "name": "文章分析模板",
   "description": "用于分析文章内容的模板",
   "latestVersion": 1,
   "createdAt": "2025-12-27T10:00:00",
   "updatedAt": "2025-12-27T10:00:00",
   "latestVersionDetail": {
      "id": 1,
      "templateId": 1,
      "version": 1,
      "content": "",
      "immutable": false,
      "createdAt": "2025-12-27T10:00:00"
   }
}
```

---

#### 删除提示词模板

删除模板及其所有历史版本（级联删除）。

**接口地址：** `DELETE /api/admin/v1/prompts/{tempId}`

**路径参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| tempId | Long | 是 | 模板 ID |

**状态码：**

- 204：删除成功

---

#### 获取指定版本的提示词

**接口地址：** `GET /api/admin/v1/prompts/{tempId}/versions/{versionNum}`

**路径参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| tempId | Long | 是 | 模板 ID |
| versionNum | Integer | 是 | 版本号 |

**响应示例：**

```json
{
  "id": 1,
  "templateId": 1,
  "version": 1,
  "content": "请分析以下文章：{title}...",
  "immutable": false,
  "createdAt": "2025-12-27T10:00:00"
}
```

---

#### 更新提示词版本内容

仅当版本为最新版本且未被锁定（immutable=false）时允许修改。

**接口地址：** `PUT /api/admin/v1/prompts/{tempId}/versions/{versionNum}`

**路径参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| tempId | Long | 是 | 模板 ID |
| versionNum | Integer | 是 | 版本号 |

**请求参数：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| content | String | 是 | 提示词内容 |

**请求示例：**

```json
{
  "content": "请分析以下文章：\n标题：{title}\n作者：{author}\n内容：{content}"
}
```

**响应示例：**

```json
{
  "id": 1,
  "templateId": 1,
  "version": 1,
  "content": "请分析以下文章：\n标题：{title}\n作者：{author}\n内容：{content}",
  "immutable": false,
  "createdAt": "2025-12-27T10:00:00"
}
```

**错误情况：**

- 400：版本已被锁定（immutable=true）
- 400：版本不是最新版本

---

#### 锁定提示词版本

将版本标记为不可修改（immutable=true），版本内容被永久冻结，不可逆转。

**接口地址：** `POST /api/admin/v1/prompts/{tempId}/versions/{versionNum}/freeze`

**路径参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| tempId | Long | 是 | 模板 ID |
| versionNum | Integer | 是 | 版本号 |

**状态码：**

- 200：锁定成功

---

#### 创建新版本

基于最新的已锁定版本内容，创建下一个新版本（可修改）。

**接口地址：** `POST /api/admin/v1/prompts/{tempId}/versions`

**路径参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| tempId | Long | 是 | 模板 ID |

**响应示例：**

```json
{
  "id": 2,
  "templateId": 1,
  "version": 2,
  "content": "请分析以下文章：{title}...",
  "immutable": false,
  "createdAt": "2025-12-27T11:00:00"
}
```

**约束：**

- 当前最新版本必须已被锁定（immutable=true）才能创建新版本

---

### 实验管理

实验管理接口，用于创建和管理 AI 文章分析实验。

#### 获取所有实验

**接口地址：** `GET /api/admin/v1/experiments`

**请求参数：**

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| status | ExperimentStatus | 否 | 实验状态筛选 |
| page | Integer | 0 | 页码 |
| size | Integer | 20 | 每页数量 |

**ExperimentStatus 枚举值：**

- `RUNNING`：运行中
- `COMPLETED`：已完成
- `FAILED`：失败

**响应示例：**

```json
{
  "content": [
    {
      "id": 1,
      "name": "GPT-4 文章分析实验",
      "description": "使用 GPT-4 分析科技文章",
      "status": "COMPLETED",
      "articleIds": [1, 2, 3],
      "modelConfigId": 1,
      "modelConfigName": "GPT-4 Config",
      "promptTemplateId": 1,
      "promptTemplateName": "文章分析模板",
      "promptVersion": 1,
      "createdAt": "2025-12-27T10:00:00"
    }
  ],
  "pageable": { ... }
}
```

---

#### 创建实验

创建新的分析实验，实验将异步执行。

**接口地址：** `POST /api/admin/v1/experiments`

**请求参数：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| name | String | 是 | 实验名称 |
| description | String | 否 | 实验描述 |
| articleIds | List\<Long\> | 是 | 文章 ID 列表 |
| modelConfigId | Long | 是 | 模型配置 ID |
| promptTemplateId | Long | 是 | 提示词模板 ID |
| promptVersion | Integer | 是 | 提示词版本号 |

**约束：**

- 提示词版本必须已被锁定（immutable=true）

**请求示例：**

```json
{
  "name": "GPT-4 文章分析实验",
  "description": "使用 GPT-4 分析科技文章",
  "articleIds": [1, 2, 3],
  "modelConfigId": 1,
  "promptTemplateId": 1,
  "promptVersion": 1
}
```

**响应示例：**

```json
{
  "id": 1,
  "name": "GPT-4 文章分析实验",
  "description": "使用 GPT-4 分析科技文章",
  "status": "RUNNING",
  "articleIds": [1, 2, 3],
  "modelConfigId": 1,
  "modelConfigName": "GPT-4 Config",
  "promptTemplateId": 1,
  "promptTemplateName": "文章分析模板",
  "promptVersion": 1,
  "createdAt": "2025-12-27T10:00:00"
}
```

---

#### 获取实验详情

获取实验详情，包括模型配置、提示词模板和所有分析结果列表。

**接口地址：** `GET /api/admin/v1/experiments/{id}`

**路径参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Long | 是 | 实验 ID |

**响应示例：**

```json
{
   "id": 1,
   "name": "GPT-4 文章分析实验",
   "description": "使用 GPT-4 分析科技文章",
   "status": "COMPLETED",
   "articleIds": [1, 2, 3],
   "modelConfig": {
      "id": 1,
      "name": "GPT-4 Config",
      "description": "GPT-4 高配置",
      "modelId": "gpt-4",
      "temperature": 0.7,
      "topP": 0.9,
      "topK": null,
      "maxTokens": 2000,
      "seed": 42,
      "createdAt": "2025-12-01T00:00:00",
      "updatedAt": "2025-12-27T10:00:00"
   },
   "promptTemplate": {
      "id": 1,
      "name": "文章分析模板",
      "description": "用于分析文章内容的模板",
      "latestVersion": 1,
      "createdAt": "2025-12-01T00:00:00",
      "updatedAt": "2025-12-27T10:00:00",
      "latestVersionDetail": {
         "id": 1,
         "templateId": 1,
         "version": 1,
         "content": "请分析以下文章：{title}...",
         "immutable": true,
         "createdAt": "2025-12-27T10:00:00"
      }
   },
   "promptVersion": 1,
   "createdAt": "2025-12-27T10:00:00"
}
```

---

#### 删除实验

删除实验（仅限未执行或已完成状态）。

**接口地址：** `DELETE /api/admin/v1/experiments/{id}`

**路径参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Long | 是 | 实验 ID |

**约束：**

- 不能删除运行中的实验（status=RUNNING）

**状态码：**

- 204：删除成功

---

### 分析结果管理

分析结果管理接口，用于查询实验的分析结果。

#### 获取文章的所有分析结果

查询指定文章的所有历史分析结果。

**接口地址：** `GET /api/admin/v1/analysis-results/articles/{articleId}`

**路径参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| articleId | Long | 是 | 文章 ID |

**请求参数：**

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| page | Integer | 0 | 页码 |
| size | Integer | 20 | 每页数量 |

**响应示例：**

```json
{
  "content": [
    {
      "id": 1,
      "experimentId": 1,
      "experimentName": "GPT-4 文章分析实验",
      "articleId": 1,
      "articleTitle": "AI 技术突破",
      "status": "SUCCESS",
      "errorMessage": null,
      "createdAt": "2025-12-27T10:05:00"
    }
  ],
  "pageable": { ... }
}
```

---

#### 获取实验的分析结果

查询指定实验的所有分析结果。

**接口地址：** `GET /api/admin/v1/analysis-results/experiments/{experimentId}`

**路径参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| experimentId | Long | 是 | 实验 ID |

**请求参数：**

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| page | Integer | 0 | 页码 |
| size | Integer | 20 | 每页数量 |

**响应示例：**

```json
{
  "content": [
    {
      "id": 1,
      "experimentId": 1,
      "experimentName": "GPT-4 文章分析实验",
      "articleId": 1,
      "articleTitle": "AI 技术突破",
      "status": "SUCCESS",
      "errorMessage": null,
      "createdAt": "2025-12-27T10:05:00"
    }
  ],
  "pageable": { ... }
}
```

---

#### 获取分析结果详情

获取分析结果的详细信息，包括模型配置、提示词内容、分析结果、Token 消耗等。

**接口地址：** `GET /api/admin/v1/analysis-results/{id}`

**路径参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Long | 是 | 分析结果 ID |

**响应示例：**

```json
{
  "id": 1,
  "experimentId": 1,
  "articleId": 1,
  "articleTitle": "AI 技术突破",
  "modelConfigJson": "{\"id\":1,\"name\":\"GPT-4 Config\",\"modelId\":\"gpt-4\",\"temperature\":0.7,\"topP\":0.9,\"maxTokens\":2000,\"seed\":42}",
  "promptContent": "请分析以下文章：\n标题：AI 技术突破\n作者：John Doe\n内容：...",
  "analysisResult": "这是一篇关于AI技术突破的文章...",
  "inputTokens": 500,
  "outputTokens": 300,
  "executionTimeMs": 2500,
  "status": "SUCCESS",
  "errorMessage": null,
  "createdAt": "2025-12-27T10:05:00"
}
```

**AnalysisStatus 枚举值：**

- `SUCCESS`：成功
- `FAILED`：失败

---

### 系统配置管理

系统配置管理接口，用于管理系统的运行时配置。

#### 获取所有配置

**接口地址：** `GET /api/admin/settings`

**响应示例：**

```json
[
  {
    "key": "collector_fetch_interval",
    "value": "60000",
    "description": null
  },
  {
    "key": "llm_base_url",
    "value": "https://api.openai.com/v1",
    "description": null
  },
  {
    "key": "llm_api_key",
    "value": "sk-***",
    "description": null
  },
  ...
]
```

---

#### 更新配置

批量更新系统配置，更新后会触发配置更新事件，相关服务会自动刷新。

**接口地址：** `POST /api/admin/settings`

**请求参数：**

Map 结构，key 为配置键，value 为配置值。

**常用配置键：**

| 配置键 | 类型 | 说明 |
|--------|------|------|
| collector_fetch_interval | Long | 抓取间隔（毫秒），默认 60000 |
| collector_fetch_timeout | Integer | 抓取超时（秒），默认 30 |
| collector_fetch_max_retries | Integer | 最大重试次数，默认 3 |
| rsshub_host | String | RSSHub 主机地址，默认 http://rsshub.app |
| llm_base_url | String | LLM API 基础 URL |
| llm_api_key | String | LLM API 密钥 |
| language_model_id | String | 语言模型 ID |
| llm_gen_prompt | String | 内容生成提示词模板 |
| llm_gen_model_config | JsonNode | 模型参数配置 |
| embedding_model_id | String | 向量模型 ID |
| concurrent_limit | Integer | 并发处理限制，默认 5 |
| admin_username | String | 管理员用户名 |
| admin_password | String | 管理员密码 |

**请求示例：**

```json
{
   "collector_fetch_interval": "30000",
   "concurrent_limit": "10",
   "language_model_id": "gpt-4",
   "llm_api_key": "sk-new-key"
}
```

**状态码：**

- 200：更新成功

**注意：** 更新 LLM 相关配置后，相关服务会自动重建 API 客户端。

---

## 附录

### 枚举类型说明

#### SourceType（RSS 源类型）

| 值 | 说明 |
|----|------|
| ORIGIN | 原始 RSS 源 |
| RSSHUB | RSSHub 源 |

#### SourceStatus（RSS 源状态）

| 值 | 说明 |
|----|------|
| ENABLED | 启用 |
| DISABLED | 禁用 |

#### FetchStatus（抓取状态）

| 值 | 说明 |
|----|------|
| NEVER | 从未抓取 |
| SUCCESS | 抓取成功 |
| FAILED | 抓取失败 |
| FETCHING | 正在抓取 |

#### ExperimentStatus（实验状态）

| 值 | 说明 |
|----|------|
| RUNNING | 运行中 |
| COMPLETED | 已完成 |
| FAILED | 失败 |

#### AnalysisStatus（分析结果状态）

| 值 | 说明 |
|----|------|
| SUCCESS | 成功 |
| FAILED | 失败 |

---

### 错误码说明

| HTTP 状态码 | 说明 |
|-------------|------|
| 200 | 请求成功 |
| 201 | 创建成功 |
| 204 | 删除成功 |
| 400 | 请求参数错误 |
| 401 | 未授权（Token 无效或过期） |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |

---

### 常见问题

#### Q: 如何获取可用的模型列表？

A: 目前系统需要用户手动输入模型 ID。模型 ID 可以从 LLM 提供商（如 OpenAI）的文档中获取。

#### Q: 提示词版本如何管理？

A: 系统采用两级结构：
1. 创建模板时自动生成 v1 版本（可修改）
2. 修改完成后调用 `freeze` 接口锁定版本
3. 基于已锁定版本创建新版本 v2（可修改）
4. 重复上述流程