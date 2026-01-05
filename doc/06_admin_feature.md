# 任务要求

## 登录及鉴权

### 1. 实现后台管理员登录功能

- 使用 `AppConfig` 类中的用户名和密码字段进行验证

### 2. 提供登录接口

- 返回 JWT token
- token 过期时间：1个月
- JWT 密钥从配置文件读取
- 已引入 jjwt 0.13.0 依赖

### 3. 提供刷新接口

- 返回带有更新日期的新 JWT token

### 4. 配置拦截器

- 作用范围：`interfaces/admin` 目录下的所有 controller（除登录接口外）
- 功能：校验 JWT token 的有效性

### 实现路线

1. 创建 JWT 工具类处理 token 的生成和验证
2. 创建UserController
3. 实现登录接口
4. 实现 token 刷新接口
5. 创建拦截器验证 JWT
6. 注册拦截器到相应的路径

## 系统配置能力

- 使用settings表，以key-value形式配置系统参数，包括base_url、apikey、拉取间隔、全局prompt等。
- 所有参数不停机更新（除了admin账户密码需要重新登录）