# 用户管理模块

## 1. 模块概述

用户管理模块负责前台用户的注册、登录、信息管理以及安全认证。

### 1.1 核心功能

- 用户注册（含IP限流）
- 用户登录（JWT认证）
- Token刷新
- 用户信息查询与修改（用户名、密码）

### 1.2 用户交互

前台用户可以通过REST API进行以下操作：

1. **注册**：创建新账号，系统会自动记录IP并进行限流检查（24小时内同一IP最多注册10个账号）。
2. **登录**：使用用户名和密码获取JWT Token。
3. **刷新Token**：使用旧Token换取新Token。
4. **个人信息**：查看个人资料、修改用户名、修改密码。

### 1.3 配置项

| 配置键 | 默认值 | 说明 |
|--------|--------|------|
| default_avatar | - | 新用户注册时的默认头像URL |

---

## 2. 架构设计

### 2.1 架构层次

```
Controller (interfaces.front.UserController)
    ↓
Service (UserService)
    ↓
Repository (UserRepository)
    ↓
Entity (User)
```

### 2.2 核心组件

| 组件 | 职责 |
|------|------|
| front.UserController | 处理前台用户相关的HTTP请求 |
| UserService | 实现注册、登录、更新信息等业务逻辑 |
| UserRepository | 用户数据的持久化接口 |
| User | 用户实体，包含用户名、密码hash、IP记录等 |
| JwtUtils | 提供Token生成、解析和验证功能 |
| FrontAuthInterceptor | 前台请求的鉴权拦截器 |

### 2.3 数据结构 (User)

| 字段 | 类型 | 说明 | 索引 |
|------|------|------|------|
| id | Long | 主键 | PK |
| username | String | 用户名 | Unique, idx_user_username |
| password | String | MD5加密后的密码 | - |
| avatar_url | String | 头像链接 | - |
| registration_ip | String | 注册时的IP地址 | idx_user_reg_limiter (组合) |
| created_at | DateTime | 创建时间 | idx_user_reg_limiter (组合) |
| updated_at | DateTime | 最后更新时间 | - |

---

## 3. 核心业务流程

### 3.1 用户注册流

1. **输入校验**：接收用户名和密码。
2. **IP限流**：查询该IP在过去24小时内的注册数量，若>=10则拒绝。
3. **用户名查重**：检查用户名是否已存在。
4. **构建实体**：生成密码hash，设置默认头像，记录注册IP。
5. **持久化**：保存用户到数据库。
6. **返回结果**：返回用户基本信息。

### 3.2 用户登录流程

1. **凭证校验**：查询用户，比对MD5密码。
2. **生成Token**：验证通过后签发JWT Token。
3. **返回Token**：客户端保存Token用于后续请求。
