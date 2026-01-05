# 大模型与prompt模块需求评审

## 1. 范围与目标

本模块负责系统核心资产（模型配置参数集和 Prompt 模板）的创建、存储和生命周期管理。目标是提供一个高度可控、可追溯的配置中心，为之后的评测任务提供稳定且版本化的输入。

## 2. 功能需求

### 2.1 模型配置管理

#### 2.1.1 获取可用模型列表

* **配置：** 是`settings`表的配置，分别为`llm_base_url`和`llm_api_key`，你需要修改`AppConfig`这个类来获取。
* **处理：** 后端服务需调用`OpenAiApi.listModels()`实时获取模型清单。
* **约束：** 仅列出支持**文本生成** 的模型。

#### 2.1.2 模型配置的 CRUD 操作

用户可创建、查看、修改和删除一套模型配置。

* **数据字段要求：**

  | 字段名 | 类型 | 必填 | 说明                         |
                                  | :--- | :--- | :--- |:---------------------------|
  | `name` | String | 是 | 配置名称                       |
  | `description` | Text | 否 | 详细描述                       |
  | `model_id` | String | 是 | 从 2.1.1 列表选择的模型 ID，但不需要做校验 |
  | `temperature` | Float | 否 | 0.0 - 2.0                  |
  | `top_p` | Float | 否 | 0.0 - 1.0                  |
  | `top_k` | Integer | 否 | 采样数量                       |
  | `max_tokens` | Integer | 否 | 最大生成长度                     |
  | `seed` | Long | 否 | 随机种子，用于确定性输出               |

- **补充**：在实际建表时你需要补充字段，如id、create_at和update_at,参考已有的数据模型

-----

### 2.2 提示词版本管理

采用 **Prompt Template (父级概念)** + **Prompt Version (子级内容)** 的两级结构。

提示词仓库表 (`prompt_template`)
这张表代表一个 Prompt 的逻辑集合或“概念”，是所有版本的父级元数据。

| 字段名              | 类型             | 约束                  | 说明                        |
|:-----------------|:---------------|:--------------------|:--------------------------|
| `name`           | `VARCHAR(255)` | NOT NULL, UNIQUE    | Prompt 仓库的名称（例如："代码审查模板"） |
| `description`    | `TEXT`         | NULLABLE            | 仓库的用途描述                   |
| `latest_version` | `INTEGER`      | NOT NULL, DEFAULT 0 | 当前最新的版本号（冗余字段，便于快速查询最新版本） |

提示词版本表 (`prompt_versions`)
这张表存储了特定版本的 Prompt 内容和其状态（可否修改）。

| 字段名           | 类型          | 约束                      | 说明                                     |
|:--------------|:------------|:------------------------|:---------------------------------------|
| `id`          | `BIGSERIAL` | PRIMARY KEY             | 唯一标识符                                  |
| `Template_id` | `BIGINT`    | NOT NULL                | 指向 `prompt_template` 表                 |
| `version`     | `INTEGER`   | NOT NULL                | 版本号 (v1, v2, v3, ...)                  |
| `content`     | `TEXT`      | NOT NULL                | 实际的 Prompt 模板内容                        |
| `immutable`   | `BOOLEAN`   | NOT NULL, DEFAULT FALSE | **核心状态字段：** `FALSE` = 可修改；`TRUE` = 已锁定 |

#### 2.2.1 Prompt Template (父级) 管理

* **创建：** 用户定义 Prompt 的名称和描述。后端自动生成第一个版本 (v1)，并使其**默认可修改**。
    * *逻辑：* 插入 `prompt_template`，并插入配套的 `prompt_versions` (version=1, immutable=FALSE)。
* **删除：** 删除父级 Template 时，必须使用**级联删除**，同时删除旗下所有历史版本（手动删除，不要依赖数据库外键）。
* **列表/详情：** 支持查询所有 Template，详情中应包含当前最新版本的引用。

#### 2.2.2 Prompt Version (子级) 操作

| 动作                         | 描述                                     | 状态转换与约束                                                                               |
|:---------------------------|:---------------------------------------|:--------------------------------------------------------------------------------------|
| **创建 v1**                  | 随 Template 创建而自动生成。                    | `immutable = FALSE` (可修改)                                                             |
| **修改 (Edit)**              | 用户修改当前最新的且未被锁定的版本内容。                   | **约束：** 仅当 `immutable = FALSE` 时允许 `UPDATE` 操作。                                       |
| **保存快照 (Snapshot/Freeze)** | **核心动作**。用户将当前版本的内容永久锁定。               | **转换：** `immutable` 字段由 `FALSE` 变为 `TRUE`。版本内容被永久冻结，不可逆转。                             |
| **新建版本 (New Version)**     | 用户基于最新的已锁定的版本内容，创建下一个新的版本。             | **逻辑：** 复制源版本的内容，创建 `version = max(version) + 1` 的新记录，且新记录 `immutable = FALSE` (可修改)。 |
| **删除历史版本**                 | 禁止删除已锁定的历史版本，只能通过删除父级 Template 进行整体清理。 | **约束：** 不允许针对单个版本进行 DELETE 操作。                                                        |

-----

## 3. API 接口定义 (RESTful Endpoints)

### 3.1 模型配置接口 (`/api/v1/model-configs`)

| 方法       | 路径        | 描述         |
|:---------|:----------|:-----------|
| `GET`    | `/models` | 获取模型列表     |
| `POST`   | `/`       | 创建模型配置     |
| `GET`    | `/{id}`   | 获取/列表/搜索配置 |
| `PUT`    | `/{id}`   | 更新配置       |
| `DELETE` | `/{id}`   | 删除配置       |

### 3.2 Prompt 版本管理接口 (`/api/v1/prompts`)

| 方法       | 路径                                       | 描述             | 业务逻辑                                      |
|:---------|:-----------------------------------------|:---------------|:------------------------------------------|
| `POST`   | `/`                                      | 创建新的 Prompt 仓库 | 自动创建 Template 和 v1 版本 (`immutable=FALSE`) |
| `DELETE` | `/{tempId}`                              | 删除 Prompt 仓库   | 级联删除所有版本                                  |
| `GET`    | `/{tempId}/versions/{versionNum}`        | 获取特定版本详情       |                                           |
| `PUT`    | `/{tempId}/versions/{versionNum}`        | **修改内容**       | **约束：** 仅当版本号为最新且 `immutable=FALSE` 时允许   |
| `POST`   | `/{tempId}/versions/{versionNum}/freeze` | **保存快照**       | 将 `immutable` 设为 `TRUE`                   |
| `POST`   | `/{tempId}/versions/new`                 | **创建新版本**      | 基于最新的已锁定版本内容，创建 v(N+1)，`immutable=FALSE`  |
