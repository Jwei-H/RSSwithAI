# RSS源管理模块

## 1. 模块概述

RSS源管理模块负责管理RSS订阅源的配置、调度抓取任务、监控抓取状态，为系统提供稳定的文章数据来源。

### 1.1 核心功能

- RSS源的增删改查操作
- 定时调度抓取（每秒检查一次）
- 支持原始RSS源和RSSHub源两种类型
- 失败重试机制（最多3次，间隔递增）
- 手动触发抓取功能
- 抓取状态监控和统计

### 1.2 用户交互

管理员可以通过REST API进行以下操作：

1. **创建RSS源**：设置名称、URL、类型、描述、抓取间隔
2. **查询RSS源**：查看列表、详情、统计信息
3. **更新RSS源**：修改配置信息或启用/禁用状态
4. **删除RSS源**：移除不需要的源
5. **手动抓取**：触发单个源或所有启用源的抓取

### 1.3 配置项

| 配置键 | 默认值 | 说明 |
|--------|--------|------|
| collector_fetch_interval | 60000 | 调度器检查间隔（毫秒） |
| collector_fetch_timeout | 30 | HTTP请求超时（秒） |
| collector_fetch_max_retries | 3 | 最大重试次数 |
| rsshub_host | http://rsshub.app | RSSHub主机地址 |

---

## 2. 架构设计

### 2.1 架构层次

```
Controller (RssSourceController)
    ↓
Service (RssSourceService, RssSchedulerService, RssFetcherService)
    ↓
Repository (RssSourceRepository)
    ↓
Entity (RssSource)
```

### 2.2 核心组件

| 组件 | 职责 |
|------|------|
| RssSourceController | 提供RSS源管理的REST API |
| RssSourceService | RSS源的CRUD操作和状态管理 |
| RssSchedulerService | 定时调度器，每秒检查需要抓取的源 |
| RssFetcherService | 执行HTTP请求、解析RSS、保存文章 |
| RssSourceRepository | RSS源数据访问 |
| RssUtils | RSS/Atom格式解析工具 |

---

## 3. 核心业务流程

### 3.1 定时调度流程

调度器每秒检查一次，筛选出满足抓取间隔条件的启用源，使用虚拟线程并发抓取。

```
每秒检查 → 筛选启用源 → 检查抓取间隔 → 虚拟线程并发抓取 → 更新状态
```

### 3.2 抓取流程

1. 构建RSS URL（RSSHUB类型需拼接主机地址）
2. 执行HTTP请求，失败则重试（最多3次，间隔2s/4s/6s）
3. 解析RSS/Atom格式，提取文章条目
4. 通过guid或link去重，保存新文章
5. 发布ArticleProcessEvent事件，触发AI处理
6. 更新抓取状态

### 3.3 重试机制

- 最多重试3次
- 重试间隔递增：2秒、4秒、6秒
- 记录失败原因和失败次数

---

## 4. 数据模型

### 4.1 RssSource

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键ID |
| name | String | RSS源名称 |
| url | String | RSS源URL |
| type | SourceType | 源类型（ORIGIN/RSSHUB） |
| description | String | 描述 |
| fetchIntervalMinutes | Integer | 抓取间隔（分钟） |
| status | SourceStatus | 源状态（ENABLED/DISABLED） |
| lastFetchStatus | FetchStatus | 最后抓取状态 |
| lastFetchTime | LocalDateTime | 最后抓取时间 |
| lastFetchError | String | 最后抓取错误 |
| failureCount | Integer | 失败次数 |
| createdAt | LocalDateTime | 创建时间 |
| updatedAt | LocalDateTime | 更新时间 |

### 4.2 枚举类型

- **SourceType**: ORIGIN（原始RSS源）、RSSHUB（RSSHub源）
- **SourceStatus**: ENABLED（启用）、DISABLED（禁用）
- **FetchStatus**: NEVER（从未抓取）、SUCCESS（成功）、FAILED（失败）、FETCHING（抓取中）

---

## 5. API接口

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | /api/admin/v1/rss-sources | 获取所有RSS源（分页） |
| GET | /api/admin/v1/rss-sources/stats | 获取统计信息 |
| GET | /api/admin/v1/rss-sources/{id} | 获取单个RSS源 |
| POST | /api/admin/v1/rss-sources | 创建RSS源 |
| PUT | /api/admin/v1/rss-sources/{id} | 更新RSS源 |
| DELETE | /api/admin/v1/rss-sources/{id} | 删除RSS源 |
| POST | /api/admin/v1/rss-sources/{id}/enable | 启用RSS源 |
| POST | /api/admin/v1/rss-sources/{id}/disable | 禁用RSS源 |
| POST | /api/admin/v1/rss-sources/{id}/fetch | 手动触发抓取 |
| POST | /api/admin/v1/rss-sources/fetch-all | 抓取所有启用的源 |

---

## 6. 关键设计点

### 6.1 并发控制

- 使用`AtomicBoolean`防止任务重叠
- 使用虚拟线程实现高并发抓取
- 每个源独立执行，互不影响

### 6.2 去重机制

- 通过guid或link字段进行去重
- 数据库唯一索引约束

### 6.3 事件驱动

- 抓取成功后发布`ArticleProcessEvent`事件
- `LlmProcessService`监听事件，触发文章增强处理

### 6.4 失败处理

- 记录失败原因到`lastFetchError`
- 累加失败次数到`failureCount`
- 自动重试机制