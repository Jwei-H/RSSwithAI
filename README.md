# RSSwithAI

RSSwithAI 是一个 RSS 信息收集、分析和阅读系统。项目包含一个 Spring Boot 后端、一个面向普通用户的前台页面，以及一个用于配置和管理的后台页面。

在线地址：[rssflow.top](https://rssflow.top)

## 功能概览

### 前台用户端

- 账号注册、登录、Token 刷新和个人资料管理。
- 浏览可用 RSS 源，订阅或取消订阅 RSS 源。
- 创建 Topic 主题订阅，基于文章向量召回相关内容。
- 查看混合时间线：合并 RSS 订阅文章和 Topic 语义匹配文章。
- 查看文章详情、AI 生成的概览、关键信息、标签和补充目录。
- 搜索文章，支持全局、订阅范围和收藏范围。
- 收藏文章，查看收藏列表。
- 查看关键词云、热点事件，以及热点事件关联文章。
- 对 Topic 订阅生成事件追踪时间线。

### 后台管理端

- 管理 RSS 源，支持标准 RSS 和 RSSHub 类型。
- 定时抓取 RSS 内容，支持失败重试和手动抓取。
- 查询文章、查看统计信息、重新生成文章增强信息。
- 配置 LLM、Embedding 模型、RSSHub 地址、抓取间隔、并发数等系统参数。
- 管理模型参数和 Prompt 模板版本。
- 创建分析实验，记录模型响应、Token 消耗和执行耗时。

### AI 处理能力

- 为文章生成概览、关键信息、标签和补充目录。
- 为文章和 Topic 生成向量，用于语义检索、推荐和订阅匹配。
- 对标签进行聚合清洗，用于关键词云。
- 从文章集合中提取热点事件。

AI 相关能力依赖管理端中的 LLM 与 Embedding 配置。未配置时，RSS 源管理、普通文章浏览等基础功能仍可使用，但内容增强、语义检索、趋势分析和事件追踪会受影响。

## 技术栈

- 后端：Java 25、Spring Boot 4、Spring Data JPA、Spring AI
- 数据库：PostgreSQL 17、pgvector
- 前端：Vue 3、Vite、TypeScript、Tailwind CSS
- 部署：Docker Compose、Nginx

## 项目结构

```text
RSSwithAI/
├── src/                 # 后端源码
├── fronted-user/        # 前台用户端
├── fronted-admin/       # 后台管理端
├── deploy/              # Docker 镜像和 Nginx 配置
├── doc/                 # 模块说明文档
├── docker-compose.yml   # 容器编排配置
└── pom.xml              # Maven 配置
```

更详细的模块设计见 `doc/`。若文档与代码不一致，以当前代码为准。

## Docker 部署

### 1. 准备环境

需要安装：

- Docker
- Docker Compose

### 2. 克隆项目

```bash
git clone https://github.com/Jwei-H/RSSwithAI.git
cd RSSwithAI
```

### 3. 配置环境变量

```bash
cp .env.example .env
```

使用内置 PostgreSQL 时，建议将 `.env` 配置为：

```env
COMPOSE_PROFILES=postgres
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/rsswithai
DB_USERNAME=rsswithai
DB_PASSWORD=change_this_password
POSTGRES_DB=rsswithai
```

使用外部 PostgreSQL 时：

```env
COMPOSE_PROFILES=
SPRING_DATASOURCE_URL=jdbc:postgresql://your_db_host:5432/rsswithai
DB_USERNAME=your_username
DB_PASSWORD=your_password
POSTGRES_DB=rsswithai
```

外部 PostgreSQL 需要提前安装并启用 `pgvector`：

```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

### 4. 启动服务

内置 PostgreSQL：

```bash
docker compose --profile postgres up -d --build
```

外部 PostgreSQL：

```bash
docker compose up -d --build
```

### 5. 访问地址

- 用户端：`http://localhost:5777`
- 管理端：`http://localhost:5173`
- 后端 API：`http://localhost:9090`

默认管理账号来自系统配置，未修改时为：

- 用户名：`admin`
- 密码：`admin`

首次部署后建议先登录管理端，修改管理员用户名和密码，并配置 LLM 与 Embedding 参数。

## 本地开发

### 1. 环境要求

- JDK 25
- Maven 3.9+
- Node.js 22+
- PostgreSQL 17，并启用 `pgvector`

### 2. 启动后端

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/rsswithai
export DB_USERNAME=your_username
export DB_PASSWORD=your_password
export JWT_SECRET=replace_with_a_random_secret
mvn spring-boot:run
```

后端默认监听 `http://localhost:8080`。

### 3. 启动前台用户端

```bash
cd fronted-user
npm install
VITE_API_BASE_URL=http://localhost:8080 npm run dev
```

用户端默认监听 `http://localhost:5777`。

### 4. 启动后台管理端

```bash
cd fronted-admin
npm install
VITE_API_BASE_URL=http://localhost:8080 npm run dev
```

管理端默认监听 `http://localhost:5173`。

## 常用配置

管理端支持维护运行时配置，主要包括：

- `llm_base_url`、`llm_api_key`、`language_model_id`
- `embedding_base_url`、`embedding_api_key`、`embedding_model_id`
- `rsshub_host`
- `collector_fetch_interval`、`collector_fetch_timeout`、`collector_fetch_max_retries`
- `concurrent_limit`
- `subscription_topic_threshold`
- `trends_word_cloud_frequency_hours`

配置保存在数据库中，部分配置更新后会触发服务内的客户端或调度逻辑刷新。
