# 文章管理模块

## 1. 模块概述

文章管理模块负责管理从RSS源抓取的文章数据，提供文章的查询、检索、统计功能，并展示文章的AI增强信息。

### 1.1 核心功能

- 文章的查询和检索（支持分页）
- 按RSS源筛选文章
- 关键词搜索（标题和作者的模糊匹配）
- 文章统计信息（总数、每日新增）
- 查看文章的AI增强信息
- 重新生成文章增强信息
- 文章收藏：收藏/取消收藏，查看收藏列表（分页，按发布时间倒序）

### 1.2 用户交互

管理员可以通过REST API进行以下操作：

1. **查询文章列表**：查看所有文章，支持分页和关键词搜索
2. **查询文章详情**：查看文章的完整信息（包括内容）
3. **查询指定源的文章**：查看特定RSS源的文章
4. **查看统计信息**：查看文章总数和每日新增趋势
5. **查看增强信息**：查看AI生成的概览、关键信息、标签、补充目录(toc)
6. **重新生成增强信息**：手动触发AI处理（阻塞调用）

前台用户可以通过REST API进行以下操作：

1. **文章详情**：查看文章内容
2. **AI增强信息**：查看文章的关键信息、概览、标签、补充目录(toc)
3. **智能搜索**：
   - 全局搜索：在所有文章中搜索
    - 订阅内搜索：仅在用户已订阅的RSS源中搜索
    - 收藏内搜索：仅在用户已收藏的文章中搜索
4. **推荐文章**：查看与当前文章相似的推荐
5. **文章收藏**：收藏/取消收藏、分页查看收藏列表（按发布时间倒序）

### 1.3 配置项

无特定配置项。

---

## 2. 架构设计

### 2.1 架构层次

```
Controller (ArticleController)
    ↓
Service (ArticleService)
    ↓
Repository (ArticleRepository, ArticleExtraRepository)
    ↓
Entity (Article, ArticleExtra)
```

### 2.2 核心组件

| 组件 | 职责 |
|------|------|
| ArticleController | 提供文章管理的REST API |
| ArticleService | 文章查询和统计服务 |
| ArticleRepository | 文章数据访问 |
| ArticleExtraRepository | 文章增强信息数据访问 |

---

## 3. 核心业务流程

### 3.1 文章查询流程

支持分页查询和关键词搜索，列表接口不返回大字段（description、content）以减少数据传输量。

### 3.2 文章统计流程

计算文章总数和最近7天每日新增文章数量，按日期分组返回。

### 3.3 重新生成增强信息流程

调用LlmProcessService重新生成文章的AI增强信息，为阻塞调用，直接返回生成结果。

---

## 4. 数据模型

### 4.1 Article

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键ID |
| source | RssSource | RSS源（关联对象） |
| sourceName | String | RSS源名称（冗余字段） |
| title | String | 文章标题 |
| link | String | 文章链接 |
| guid | String | 全局唯一标识符（唯一索引） |
| description | String | 文章摘要 |
| content | String | Markdown内容 |
| author | String | 作者 |
| pubDate | LocalDateTime | 发布日期 |
| categories | String | 分类（逗号分隔） |
| wordCount | Long | 字数（中文字符+英文单词） |
| coverImage | String | 封面图片URL |
| fetchedAt | LocalDateTime | 抓取时间 |
| createdAt | LocalDateTime | 创建时间 |

### 4.2 ArticleExtra

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键ID |
| articleId | Long | 文章ID（外键，唯一） |
| overview | String | 文章概览 |
| keyInformation | String[] | 关键信息列表 |
| tags | String[] | 标签列表 |
| toc | JSONB(String) | AI补充目录（数组：title + anchor） |
| vector | Vector(1024) | 文章向量（1024维） |
| status | AnalysisStatus | 处理状态（SUCCESS/FAILED） |
| errorMessage | String | 错误信息 |
| createdAt | LocalDateTime | 创建时间 |
| updatedAt | LocalDateTime | 更新时间 |

### 4.3 枚举类型

- **AnalysisStatus**: SUCCESS（成功）、FAILED（失败）

---

## 5. API接口

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | /api/admin/v1/articles | 获取所有文章（分页，支持搜索） |
| GET | /api/admin/v1/articles/stats | 获取统计信息 |
| GET | /api/admin/v1/articles/{id} | 获取文章详情 |
| GET | /api/admin/v1/articles/source/{sourceId} | 获取指定RSS源的文章 |
| GET | /api/admin/v1/articles/{id}/extra | 获取文章增强信息 |
| POST | /api/admin/v1/articles/{id}/extra/regenerate | 重新生成增强信息 |

### 前台（Front）接口

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | /api/front/v1/articles/{id} | 获取文章详情（ArticleDetailDTO） |
| GET | /api/front/v1/articles/{id}/extra | 获取文章的AI增强信息（ArticleExtraDTO，含toc；如不存在或处理失败返回404，失败仅后台可见错误信息） |
| GET | /api/front/v1/articles/search | 智能搜索（query必填；searchScope可选ALL/SUBSCRIBED/FAVORITE；sourceId可选，仅用于RSS源内搜索） |
| GET | /api/front/v1/articles/{id}/recommendations | 相似文章推荐（最多2条；若当前文章无有效vector则返回空列表） |

---

## 6. 关键设计点

### 6.1 去重机制

- 通过guid字段设置唯一索引
- 保存前检查是否已存在

### 6.2 智能搜索策略

系统采用 "Hybrid RRF + Time Decay" 的混合检索重排策略，以平衡相关性（Relevance）与时效性（Freshness）。

#### 6.2.1 检索策略对比

| 维度 | 旧版策略 (Old) | **优化后策略 (Optimized)** |
|------|----------------|----------------------------|
| **召回源** | 简单合并 (Union) | **并行双路召回** |
| **关键词召回** | 模糊匹配 (LIMIT 20) | 模糊匹配（原query + jieba TFIDF top1关键词） |
| **向量召回** | 阈值截断 (< 0.4) | **Top 50 by Distance** - 保证最相关 |
| **排序逻辑** | 强制按 `pubDate` DESC | **动态加权得分 (Score)** |
| **时间因素** | 绝对主导 (Hard Sort) | **平滑衰减 (Decay Factor)** |
| **缺陷** | 高相关历史文章被埋没 | - |

#### 6.2.2 核心算法

最终得分计算公式：

$$ Score = (S_{semantic} \times W_{vec} + S_{keyword} \times W_{key}) \times Decay(t) $$

- **语义分 ($S_{semantic}$)**: `1.0 - CosineDistance` (权重 $W_{vec}=1.5$)
- **关键词分 ($S_{keyword}$)**: 命中得 1.0 (权重 $W_{key}=1.0$)
- **时间衰减 ($Decay(t)$)**: `1.0 / (1.0 + days_diff * 0.1)` (约10天衰减一半)

补充说明：
1. 当传入 `sourceId` 时，搜索限定在指定 RSS 源。
2. 当在 Topic 订阅上下文中搜索，前端默认使用 `searchScope=ALL`。
3. 关键词召回与向量召回并行执行，以降低整体 RT。

此策略确保了：
1. **搜得准**：语义高度相关的文章（即使是旧文）能浮上来。
2. **搜得新**：同等相关度下，新文章排前面。
3. **有保底**：向量搜不到时，关键词能兜底。

### 6.3 性能优化

- 列表查询使用投影，不返回大字段
- 支持分页查询，默认每页20条
- sourceName为冗余字段，避免关联查询

### 6.4 统计优化

- 使用JPA聚合查询
- 按日期分组统计每日新增数量