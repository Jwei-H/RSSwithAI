# 系统配置与认证模块

## 1. 模块概述

系统配置与认证模块负责管理系统的运行时配置和用户认证，提供配置的动态管理、JWT令牌认证和用户登录功能。

### 1.1 核心功能

- 系统配置的动态管理（数据库存储）
- 配置热更新（无需重启）
- JWT令牌认证
- 用户登录和令牌刷新
- 配置更新事件发布

### 1.2 用户交互

管理员可以通过REST API进行以下操作：

1. **查询配置**：查看所有系统配置
2. **更新配置**：批量更新配置项，更新后自动刷新相关服务
3. **用户登录**：输入用户名和密码，获取JWT令牌
4. **刷新令牌**：使用有效的令牌获取新令牌

### 1.3 配置项

### 1.3.1 采集器配置

| 配置键 | 默认值 | 说明 |
|--------|-----|------|
| collector_fetch_interval | 60 | 抓取间隔（秒） |
| collector_fetch_timeout | 30 | 抓取超时（秒） |
| collector_fetch_max_retries | 3 | 最大重试次数 |
| rsshub_host | http://rsshub.app | RSSHub主机地址 |

### 1.3.2 LLM配置

| 配置键 | 默认值 | 说明 |
|--------|--------|------|
| llm_base_url | - | LLM API基础URL |
| llm_api_key | - | LLM API密钥 |
| language_model_id | - | 语言模型ID |
| embedding_model_id | - | 向量模型ID |

### 1.3.3 内容增强配置

| 配置键 | 默认值 | 说明 |
|--------|--------|------|
| llm_gen_prompt | [见默认提示词] | 内容生成提示词模板 |
| llm_gen_model_config | - | 模型参数配置 |
| concurrent_limit | 5 | 并发处理限制 |

### 1.3.4 管理员配置

| 配置键 | 默认值 | 说明 |
|--------|--------|------|
| admin_username | - | 管理员用户名 |
| admin_password | - | 管理员密码 |

### 1.3.5 订阅系统配置

| 配置键 | 默认值 | 说明 |
|--------|--------|------|
| feed_similarity_threshold | 0.3 | 主题语义匹配距离阈值（pgvector `<=>`） |
| subscription_limit | 20 | 单个用户订阅数量上限（RSS + Topic 合计） |

---

## 2. 架构设计

### 2.1 架构层次

```
Controller (SettingsController, UserController)
    ↓
Service (SettingsService, JwtUtils)
    ↓
Repository (SettingRepository)
    ↓
Entity (Setting)
    ↓
Configuration (AppConfig)
    ↓
Interceptor (JwtInterceptor)
```

### 2.2 核心组件

| 组件 | 职责 |
|------|------|
| SettingsController | 提供系统配置管理的REST API |
| UserController | 提供用户认证的REST API |
| SettingsService | 系统配置管理服务 |
| JwtUtils | JWT令牌工具类 |
| SettingRepository | 系统配置数据访问 |
| AppConfig | 系统配置类 |
| JwtInterceptor | JWT令牌验证拦截器 |

---

## 3. 核心业务流程

### 3.1 配置加载流程

1. 查询所有配置
2. 遍历配置项
3. 通过反射更新AppConfig字段
4. 完成

### 3.2 配置更新流程

1. 接收配置更新请求
2. 开启事务
3. 遍历配置项，保存或更新配置
4. 更新AppConfig
5. 提交事务
6. 发布ConfigUpdateEvent事件

### 3.3 登录流程

1. 接收用户名和密码
2. 验证用户名和密码（与AppConfig中的admin_username和admin_password对比）
3. 生成JWT令牌
4. 返回令牌

### 3.4 令牌刷新流程

1. 接收旧令牌
2. 验证令牌有效性
3. 生成新令牌
4. 返回新令牌

### 3.5 令牌验证流程

1. 从请求头提取令牌
2. 验证令牌格式
3. 验证令牌签名
4. 验证令牌过期时间
5. 继续处理或返回401

---

## 4. 数据模型

### 4.1 Setting

| 字段 | 类型 | 说明 |
|------|------|------|
| key | String | 配置键（主键） |
| value | String | 配置值 |
| description | String | 配置描述 |

---

## 5. API接口

### 5.1 系统配置接口

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | /api/admin/settings | 获取所有配置 |
| POST | /api/admin/settings | 更新配置 |

### 5.2 用户认证接口

| 方法 | 路径 | 描述 |
|------|------|------|
| POST | /api/admin/login | 用户登录 |
| POST | /api/admin/refresh | 刷新令牌 |

---

## 6. 关键设计点

### 6.1 配置热更新

- 配置存储在数据库
- 使用反射动态更新AppConfig字段
- 发布ConfigUpdateEvent事件，通知相关服务刷新配置

### 6.2 JWT令牌设计

- 使用jjwt库生成和验证令牌
- 令牌有效期1个月
- 密钥从配置文件读取
- 令牌包含用户名和过期时间

### 6.3 拦截器配置

- 拦截路径：/api/admin/**
- 排除路径：/api/admin/login
- 验证令牌有效性

### 6.4 事件机制

- 配置更新后发布ConfigUpdateEvent
- LlmProcessService监听事件，重建AI客户端
- 使用@TransactionalEventListener(phase = AFTER_COMMIT)确保事务提交后执行

### 6.5 反射更新

- 使用@SettingKey注解标记配置项
- 通过反射更新AppConfig字段
- 支持类型转换（String → 目标类型）

### 6.6 事务管理

- 配置更新使用@Transactional保证事务性
- 事件监听器在事务提交后执行