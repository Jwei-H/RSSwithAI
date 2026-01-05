# 实验模块

## 1. 模块概述

实验模块是系统的核心智能分析组件，负责执行AI驱动的文章分析实验。用户可以选择文章、模型配置和提示词模板版本，创建实验并批量执行分析任务，记录完整的实验过程数据。

### 1.1 核心功能

- 创建和管理实验
- 批量文章的异步AI分析处理
- 记录完整的实验过程数据（请求参数、模型响应、token消耗、执行时间）
- 支持实验结果的可追溯性和对比分析
- 按文章或实验查询分析结果

### 1.2 用户交互

管理员可以通过REST API进行以下操作：

1. **创建实验**：选择文章、模型配置和已锁定的提示词版本
2. **查询实验**：查看列表或详情（包括模型配置、提示词模板和分析结果）
3. **删除实验**：删除未执行或已完成的实验
4. **查询分析结果**：按文章或实验查询分析结果
5. **查看分析结果详情**：查看模型配置、提示词内容、分析结果、token消耗等

### 1.3 配置项

复用LLM配置项（llm_base_url、llm_api_key等）。

---

## 2. 架构设计

### 2.1 架构层次

```
Controller (ExperimentController, AnalysisResultController)
    ↓
Service (ExperimentService)
    ↓
Repository (ExperimentRepository, AnalysisResultRepository)
    ↓
Entity (Experiment, AnalysisResult)
```

### 2.2 核心组件

| 组件 | 职责 |
|------|------|
| ExperimentController | 提供实验管理的REST API |
| AnalysisResultController | 提供分析结果查询的REST API |
| ExperimentService | 实验管理服务 |
| ExperimentRepository | 实验数据访问 |
| AnalysisResultRepository | 分析结果数据访问 |

---

## 3. 核心业务流程

### 3.1 创建实验流程

1. 验证模型配置存在
2. 验证提示词模板存在
3. 验证提示词版本存在且已锁定
4. 创建Experiment实体，状态为RUNNING
5. 保存到数据库
6. 使用虚拟线程异步执行实验

### 3.2 执行实验流程

1. 创建ChatModel实例（基于模型配置）
2. 遍历所有选中的文章
3. 渲染提示词模板，注入参数
4. 调用LLM进行分析
5. 记录结果（模型配置、提示词、响应、token消耗、执行时间）
6. 保存AnalysisResult
7. 所有文章处理完成后，更新实验状态为COMPLETED或FAILED

### 3.3 分析文章流程

1. 查询文章
2. 构建提示词（渲染模板+注入参数）
3. 调用LLM
4. 记录响应、token消耗、执行时间
5. 保存结果

### 3.4 删除实验流程

检查实验状态（不能删除运行中的实验），级联删除所有分析结果，然后删除实验。

---

## 4. 数据模型

### 4.1 Experiment

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键ID |
| name | String | 实验名称 |
| description | String | 实验描述 |
| status | ExperimentStatus | 实验状态（RUNNING/COMPLETED/FAILED） |
| articleIds | List<Long> | 文章ID列表 |
| modelConfig | ModelConfig | 模型配置（关联对象） |
| modelConfigId | Long | 模型配置ID（外键） |
| promptTemplate | PromptTemplate | 提示词模板（关联对象） |
| promptTemplateId | Long | 提示词模板ID（外键） |
| promptVersion | Integer | 提示词版本号 |
| createdAt | LocalDateTime | 创建时间 |

### 4.2 AnalysisResult

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键ID |
| experiment | Experiment | 所属实验（关联对象） |
| experimentId | Long | 实验ID（外键） |
| article | Article | 文章（关联对象） |
| articleId | Long | 文章ID（外键） |
| modelConfigJson | String | 模型配置参数（JSON格式） |
| promptContent | String | 实际使用的提示词（参数渲染后） |
| analysisResult | String | 模型输出结果 |
| inputTokens | Integer | 输入token数 |
| outputTokens | Integer | 输出token数 |
| executionTimeMs | Integer | 执行耗时（毫秒） |
| status | AnalysisStatus | 执行状态（SUCCESS/FAILED） |
| errorMessage | String | 错误信息 |
| createdAt | LocalDateTime | 创建时间 |

### 4.3 枚举类型

- **ExperimentStatus**: RUNNING（运行中）、COMPLETED（已完成）、FAILED（失败）
- **AnalysisStatus**: SUCCESS（成功）、FAILED（失败）

---

## 5. API接口

### 5.1 实验管理接口

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | /api/admin/v1/experiments | 获取所有实验（分页，可按状态筛选） |
| POST | /api/admin/v1/experiments | 创建实验 |
| GET | /api/admin/v1/experiments/{id} | 获取实验详情 |
| DELETE | /api/admin/v1/experiments/{id} | 删除实验 |

### 5.2 分析结果接口

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | /api/admin/v1/analysis-results/articles/{articleId} | 按文章查询分析结果 |
| GET | /api/admin/v1/analysis-results/experiments/{experimentId} | 按实验查询分析结果 |
| GET | /api/admin/v1/analysis-results/{id} | 获取分析结果详情 |

---

## 6. 关键设计点

### 6.1 异步执行

- 使用虚拟线程异步执行实验
- 每个实验独立执行，不共享资源
- 单个文章处理失败不影响其他文章

### 6.2 完整的结果记录

- 记录模型配置参数（JSON格式）
- 记录实际使用的提示词（参数渲染后）
- 记录模型输出结果
- 记录token消耗（输入/输出）
- 记录执行耗时

### 6.3 版本验证

- 创建实验时验证提示词版本必须已锁定
- 确保实验使用的提示词版本稳定

### 6.4 级联删除

- 删除实验时级联删除所有分析结果
- 不能删除运行中的实验

### 6.5 JSONB存储

- 使用PostgreSQL的JSONB类型存储模型配置
- 便于查询和解析