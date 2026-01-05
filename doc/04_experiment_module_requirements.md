# 分析实验模块需求文档

## 1. 范围与目标

分析实验模块是系统的核心智能分析组件，负责执行AI驱动的文章分析实验。用户可以在后台选择一批文章，创建一个实验（Experiment），关联到特定的模型配置（ModelConfig）和提示词模板版本（必须是immutable=true的版本），系统会批量执行分析任务并生成分析结果（AnalysisResult）。

主要目标：
1. 提供灵活的实验配置能力，支持不同模型参数和提示词模板的组合测试
2. 实现批量文章的异步AI分析处理
3. 记录完整的实验过程数据，包括请求参数、模型响应、token消耗等
4. 支持实验结果的可追溯性和对比分析

## 2. 功能需求

### 2.1 实验管理

#### 2.1.1 实验创建
- **文章选择**：支持从文章库中批量选择文章id
- **配置关联**：关联一个模型配置（ModelConfig）和一个已锁定的提示词模板

#### 2.1.2 实验执行
- **异步处理**：实验创建后异步执行分析任务

#### 2.1.3 实验查询
- **列表查询**：支持按状态、创建时间等条件筛选
- **结果浏览**：查看实验下所有文章的分析结果

### 2.2 分析结果管理

#### 2.2.1 结果生成
- **数据记录**：记录每篇文章的分析结果

#### 2.2.2 结果查询
- **按实验查询**：查询特定实验的所有分析结果
- **按文章查询**：查询特定文章的所有历史分析结果及关联的实验信息

### 2.3 AI服务集成

#### 2.3.1 模型客户端管理
- **动态配置**：基于AppConfig中的llm_base_url和llm_api_key动态创建OpenAI API客户端
- **配置更新**：当AppConfig中的配置更新时，自动重建API客户端Bean

#### 2.3.2 提示词渲染
- **模板渲染**：使用Spring AI的PromptTemplate功能渲染提示词
- **参数注入**：将文章的title、author、content作为参数注入到提示词模板中

## 3. 数据模型设计

### 3.1 实验表 (experiments)

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| id | BIGSERIAL | PRIMARY KEY | 唯一标识符 |
| name | VARCHAR(255) | NOT NULL | 实验名称 |
| description | text | nullable | 描述 |
| status | VARCHAR(50) | NOT NULL | 实验状态：RUNNING, COMPLETED, FAILED |
| articles | List | NOT NULL | 文章id列表 |
| model_config_id | BIGINT | NOT NULL | 关联的模型配置ID |
| prompt_template_id | BIGINT | NOT NULL | 关联的提示词模版ID |
| prompt_version | Integer | not null | 模板对应的version |
| created_at | TIMESTAMP | NOT NULL | 创建时间 |

### 3.2 分析结果表 (analysis_results)

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| id | BIGSERIAL | PRIMARY KEY | 唯一标识符 |
| experiment_id | BIGINT | NOT NULL | 关联的实验ID |
| article_id | BIGINT | NOT NULL | 关联的文章ID |
| model_config_json | JSONB | NOT NULL | 模型配置参数（JSON格式） |
| prompt_content | TEXT | NOT NULL | 使用的提示词内容（参数拼接后的） |
| analysis_result | TEXT | NULLABLE | 模型原始输出 |
| input_tokens | INTEGER | NULLABLE | 输入token数 |
| output_tokens | INTEGER | NULLABLE | 输出token数 |
| execution_time_ms | INTEGER | NULLABLE | 执行耗时（毫秒） |
| status | VARCHAR(50) | NOT NULL | 执行状态 SUCCESS, FAILED |
| error_message | TEXT | NULLABLE | 错误信息 |
| created_at | TIMESTAMP | NOT NULL | 创建时间 |

## 4. API接口设计

### 4.1 实验管理接口 (`/api/v1/experiments`)

| 方法 | 路径 | 描述 | 请求体 | 响应 |
|------|------|------|--------|------|
| POST | `/` | 创建新实验 | CreateExperimentRequest | ExperimentDTO |
| GET | `/` | 获取实验列表（支持分页、筛选） | 查询参数：status, page, size | Page<ExperimentDTO> |
| GET | `/{id}` | 获取实验详情（包括模型配置和prompt）及实验的分析结果列表（不分页） | - | ExperimentDetailDTO |
| DELETE | `/{id}` | 删除实验（仅限未执行或已完成状态） | - | 级联删除 |

### 4.2 分析结果接口 (`/api/v1/analysis-results`)

| 方法 | 路径 | 描述 | 请求体 | 响应 |
|------|------|------|--------|------|
| GET | `/articles/{articleId}` | 获取文章的所有分析结果（不分页） | - | List<AnalysisResultWithExperimentDTO> |
| GET | `/{id}` | 获取分析结果详情 | - | AnalysisResultDetailDTO |