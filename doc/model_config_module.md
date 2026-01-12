# 模型配置模块

## 1. 模块概述

模型配置模块负责管理大语言模型的配置参数，为实验系统提供模型选择和参数配置能力。

### 1.1 核心功能

- 模型配置的增删改查
- 存储LLM模型参数（temperature、top_p、top_k、max_tokens、seed等）
- 为实验系统提供模型选择能力

### 1.2 用户交互

管理员可以通过REST API进行以下操作：

1. **创建模型配置**：设置名称、描述、模型ID和各项参数
2. **查询模型配置**：查看列表或详情
3. **更新模型配置**：修改配置参数
4. **删除模型配置**：移除不需要的配置

### 1.3 配置项

无特定配置项。

---

## 2. 架构设计

### 2.1 架构层次

```
Controller (ModelConfigController)
    ↓
Service (ModelConfigService)
    ↓
Repository (ModelConfigRepository)
    ↓
Entity (ModelConfig)
```

### 2.2 核心组件

| 组件 | 职责 |
|------|------|
| ModelConfigController | 提供模型配置管理的REST API |
| ModelConfigService | 模型配置管理服务 |
| ModelConfigRepository | 模型配置数据访问 |

---

## 3. 核心业务流程

### 3.1 创建配置流程

验证必填字段和参数范围，构建ModelConfig实体，保存到数据库。

### 3.2 更新配置流程

查询配置，使用Builder模式更新字段，保存到数据库。

---

## 4. 数据模型

### 4.1 ModelConfig

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键ID |
| name | String | 配置名称 |
| description | String | 详细描述 |
| modelId | String | 模型标识符 |
| temperature | Double | 温度参数（0.0-2.0） |
| topP | Double | 核采样参数（0.0-1.0） |
| topK | Integer | 采样数量 |
| maxTokens | Integer | 最大生成长度 |
| seed | Long | 随机种子 |
| createdAt | LocalDateTime | 创建时间 |
| updatedAt | LocalDateTime | 更新时间 |

---

## 5. API接口

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | /api/admin/v1/model-configs | 获取所有配置（分页） |
| POST | /api/admin/v1/model-configs | 创建配置 |
| GET | /api/admin/v1/model-configs/{id} | 获取单个配置 |
| PUT | /api/admin/v1/model-configs/{id} | 更新配置 |
| DELETE | /api/admin/v1/model-configs/{id} | 删除配置 |

---

## 6. 关键设计点

### 6.1 参数说明

- **Temperature**（温度参数）：控制模型输出的随机性，值越高输出越随机（0.0-2.0）
- **Top P**（核采样）：控制从概率最高的词中选择（0.0-1.0）
- **Top K**（采样数量）：从概率最高的K个词中选择，K越大输出越多样
- **Max Tokens**（最大生成长度）：限制模型输出的最大token数量
- **Seed**（随机种子）：设置相同种子可获得确定性的输出

### 6.2 数据验证

- 名称和模型ID为必填字段
- temperature范围：0.0-2.0
- topP范围：0.0-1.0
- topK、maxTokens、seed必须为非负整数