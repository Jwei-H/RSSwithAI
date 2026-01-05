# 提示词模板模块

## 1. 模块概述

提示词模板模块负责管理提示词模板及其版本，采用两级结构（Template + Version），支持版本控制和锁定机制，为实验系统提供稳定的提示词模板。

### 1.1 核心功能

- 提示词模板的增删改查
- 版本管理（创建新版本、冻结版本）
- 版本锁定机制（immutable标志）
- 版本内容更新
- 只有冻结的版本才能用于实验

### 1.2 用户交互

管理员可以通过REST API进行以下操作：

1. **创建模板**：设置名称和描述，自动创建v1版本（可修改）
2. **查询模板**：查看列表或详情
3. **删除模板**：级联删除所有历史版本
4. **查询版本**：查看指定版本的详细信息
5. **更新版本内容**：修改可修改版本的内容
6. **冻结版本**：将版本标记为不可修改
7. **创建新版本**：基于最新的已锁定版本创建新版本

### 1.3 配置项

无特定配置项。

### 1.4 支持的变量占位符

提示词模板支持以下变量占位符：
- {title}：文章标题
- {author}：文章作者
- {content}：文章内容
- {sourceName}：RSS源名称
- {pubDate}：发布日期
- {categories}：分类

---

## 2. 架构设计

### 2.1 架构层次

```
Controller (PromptController)
    ↓
Service (PromptService)
    ↓
Repository (PromptTemplateRepository, PromptVersionRepository)
    ↓
Entity (PromptTemplate, PromptVersion)
```

### 2.2 核心组件

| 组件 | 职责 |
|------|------|
| PromptController | 提供提示词模板管理的REST API |
| PromptService | 提示词模板管理服务 |
| PromptTemplateRepository | 提示词模板数据访问 |
| PromptVersionRepository | 提示词版本数据访问 |

---

## 3. 核心业务流程

### 3.1 创建模板流程

验证名称唯一性，创建PromptTemplate实体，创建v1版本（immutable=false），设置latestVersion=1。

### 3.2 冻结版本流程

查询版本，检查是否已锁定，设置immutable=true。

### 3.3 创建新版本流程

1. 查询模板和最新版本
2. 检查最新版本是否已锁定
3. 复制最新版本内容
4. 创建新版本（version+1, immutable=false）
5. 更新模板的latestVersion

### 3.4 删除模板流程

级联删除所有历史版本，然后删除模板。

---

## 4. 数据模型

### 4.1 PromptTemplate

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键ID |
| name | String | 模板名称（唯一） |
| description | String | 模板用途描述 |
| latestVersion | Integer | 当前最新版本号 |
| versions | List<PromptVersion> | 版本列表（一对多） |
| createdAt | LocalDateTime | 创建时间 |
| updatedAt | LocalDateTime | 更新时间 |

### 4.2 PromptVersion

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键ID |
| template | PromptTemplate | 所属模板（多对一） |
| templateId | Long | 模板ID（外键） |
| version | Integer | 版本号 |
| content | String | 版本内容 |
| immutable | Boolean | 是否可修改（false=可修改，true=已锁定） |
| createdAt | LocalDateTime | 创建时间 |

---

## 5. API接口

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | /api/admin/v1/prompts | 获取所有模板（分页） |
| POST | /api/admin/v1/prompts | 创建模板 |
| GET | /api/admin/v1/prompts/{tempId} | 获取模板详情 |
| DELETE | /api/admin/v1/prompts/{tempId} | 删除模板 |
| GET | /api/admin/v1/prompts/{tempId}/versions/{versionNum} | 获取指定版本 |
| PUT | /api/admin/v1/prompts/{tempId}/versions/{versionNum} | 更新版本内容 |
| POST | /api/admin/v1/prompts/{tempId}/versions/{versionNum}/freeze | 冻结版本 |
| POST | /api/admin/v1/prompts/{tempId}/versions | 创建新版本 |

---

## 6. 关键设计点

### 6.1 版本管理机制

**版本生命周期**：
```
创建模板 → v1(可修改) → 编辑内容 → 冻结v1 → 创建v2(可修改) → 编辑内容 → 冻结v2 → ...
```

**版本状态**：
- **可修改（immutable=false）**：可以编辑内容，不能用于实验
- **已锁定（immutable=true）**：不能编辑内容，可以用于实验

### 6.2 版本操作规则

| 操作 | 可修改版本 | 已锁定版本 |
|------|-----------|-----------|
| 编辑内容 | ✅ | ❌ |
| 冻结 | ✅ | ❌ |
| 创建新版本 | ❌ | ✅（仅最新版本） |
| 用于实验 | ❌ | ✅ |

### 6.3 级联删除

- 删除模板时级联删除所有历史版本
- 使用JPA的CascadeType.ALL

### 6.4 冗余字段

- latestVersion为冗余字段，便于快速查询最新版本号

### 6.5 业务规则验证

- 更新版本：仅当版本为最新版本且未被锁定时允许
- 创建新版本：当前最新版本必须已被锁定