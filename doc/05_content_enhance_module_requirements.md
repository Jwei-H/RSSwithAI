# 内容增强模块需求文档

## 1. 范围与目标

内容增强模块是系统的智能处理层，负责对原始文章进行AI驱动的深度处理与增强。当RSS抓取器获取到新文章后，本模块自动触发AI处理流程，生成文章的概览、关键信息、标签，并生成向量表示，为后续的智能检索、相似性分析、内容推荐等功能提供基础。

注意：该模块功能暂时不需要编写接入层（即controller）

## 2. 功能需求

### 2.1 文章增强处理

#### 2.1.1 触发机制

- **事件驱动**：RssFetcherService拉取新文章后，发布ArticleProcessEvent事件
- **异步处理**：LlmProcessService监听事件并异步处理文章增强任务
- **并发控制**：基于AppConfig.concurrentLimit配置控制并发处理数量

#### 2.1.2 AI内容生成

- **概览生成**：生成文章概览，支持Markdown格式
- **关键信息提取**：提取1-3条最重要的关键信息
- **标签生成**：生成5个左右的关键词或标签
- **JSON格式输出**：AI模型输出标准化的JSON格式结果

#### 2.1.3 向量生成

- **文本拼接**：将概览和关键信息拼接为向量生成输入
- **向量化处理**：调用embedding模型生成文章向量表示
- **向量存储**：将生成的向量存储到article_extra表的vector字段

#### 2.1.4 重新生成机制

- **手动触发**：支持通过API手动触发重新生成文章增强信息
- **阻塞调用**：重新生成接口为阻塞调用，直接返回生成结果
- **覆盖更新**：重新生成成功后，覆盖原有的article_extra记录
- **失败处理**：如果重新生成失败，保留原有记录（如果存在）或更新状态为FAILED

### 2.2 配置管理

#### 2.2.1 AI模型配置

- **语言模型配置**：通过AppConfig.language_model_id配置生成模型
- **向量模型配置**：通过AppConfig.embedding_model_id配置向量模型
- **提示词配置**：通过AppConfig.llm_gen_prompt配置生成提示词
- **模型参数配置**：通过AppConfig.llm_gen_model_config配置模型参数

#### 2.2.2 动态配置更新

- **配置订阅**：LlmProcessService订阅SettingsService.ConfigUpdateEvent
- **动态刷新**：当AppConfig中的AI相关配置更新时，自动重建OpenAI API客户端
- **并发限制刷新**：当concurrent_limit配置更新时，动态调整信号量大小

## 3. 数据模型设计

### 3.1 文章扩展表 (article_extra)

与article表为一对一关系，存储AI增强处理结果：

| 字段名             | 类型           | 约束               | 说明                           |
|-----------------|--------------|------------------|------------------------------|
| id              | BIGSERIAL    | PRIMARY KEY      | 唯一标识符                        |
| article_id      | BIGINT       | NOT NULL, UNIQUE | 关联的文章ID，外键指向articles.id      |
| overview        | TEXT         | NULLABLE         | 文章概览                         |
| key_information | TEXT[]       | NULLABLE         | 关键信息列表（1-3条，每条40字以内）         |
| tags            | TEXT[]       | NULLABLE         | 标签列表（5个左右）                   |
| vector          | VECTOR(1024) | NULLABLE         | 文章向量表示（1024维，由embedding模型生成） |
| status          | VARCHAR(50)  | NOT NULL         | 最终状态：SUCCESS, FAILED         |
| error_message   | TEXT         | NULLABLE         | 处理错误信息                       |
| created_at      | TIMESTAMP    | NOT NULL         | 创建时间                         |
| updated_at      | TIMESTAMP    | NOT NULL         | 更新时间                         |

## 3. 技术实现要点

### 3.1 核心组件

#### LlmProcessService

- **事件监听**：监听ArticleProcessEvent，触发文章增强处理
- **并发控制**：使用Semaphore控制并发处理数量，支持动态调整
- **AI调用**：调用OpenAI API进行内容生成和向量化
- **状态管理**：创建或更新article_extra记录
- **配置订阅**：订阅配置更新事件，动态刷新API客户端和信号量

#### OpenAI API客户端管理

- **独立实例**：LlmProcessService持有自己的OpenAiApi实例
- **配置注入**：基于AppConfig中的llm_base_url和llm_api_key创建客户端
- **动态更新**：当配置更新时，调用OpenAiApi.mutate()

#### 并发控制机制

- **虚拟线程**：使用虚拟线程处理
- **信号量管理**：使用Semaphore实现并发限制
- **动态调整**：当concurrent_limit配置更新时，动态调整信号量许可数

### 3.2 处理流程

1. **事件触发**：RssFetcherService保存新文章后发布ArticleProcessEvent
2. **事件监听**：LlmProcessService监听事件
3. **内容生成**：调用语言模型生成概览、关键信息和标签
4. **结果解析**：解析AI返回的JSON格式结果
5. **向量生成**：拼接概览和关键信息，调用embedding模型生成向量
6. **数据保存**：将生成的结果保存到article_extra表
7. **释放许可**：释放信号量许可，允许处理下一篇文章
   其他：处理失败的无需重试，记录status和error_message即可。

## 5. 配置项说明

### 5.1 AppConfig相关配置

| 配置键                  | 类型       | 默认值      | 说明                          |
|----------------------|----------|----------|-----------------------------|
| concurrent_limit     | Integer  | 5        | 并发处理限制，动态调整信号量大小            |
| language_model_id    | String   | -        | 内容生成模型ID                    |
| embedding_model_id   | String   | -        | 向量生成模型ID                    |
| llm_gen_prompt       | String   | [见默认提示词] | 内容生成提示词模板                   |
| llm_gen_model_config | JsonNode | -        | 模型参数配置（temperature, top_p等） |

### 5.2 默认提示词模板

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
```markdown
标题：{title}
来源：{source}
---
{content}
```