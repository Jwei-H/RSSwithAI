# RSSwithAI - 智能情报收集与分析系统

RSSwithAI 是一个基于 RSS 信息源的智能情报收集、分析与展示系统。它将传统的信息聚合技术与前沿的 AI 分析能力（LLM + 向量检索）深度结合，构建了一个从"信息采集"到"智能分析"再到"精准展示"的全流程自动化情报平台。

## 🌟 核心特性

### 1. 智能采集与管理
- **多源支持**：支持标准 RSS 及 RSSHub 源。
- **自动化调度**：内置定时任务，自动抓取最新内容，支持失败重试与手动触发。
- **内容清洗**：自动去除广告与无关内容，提取纯净正文。

### 2. AI 深度增强 (LLM)
- **自动摘要**：为每篇文章生成 80 字以内的精炼概览。
- **关键信息提取**：自动提炼 1-3 条核心情报。
- **智能标签**：自动生成 5 个左右的语义标签。
- **向量化处理**：生成文章的 1024 维向量表示，赋能语义搜索与推荐。

### 3. 下一代订阅体验
- **混合订阅模式**：支持传统的 **RSS 源订阅** 和创新的 **语义主题 (Topic) 订阅**。
- **混合时间线 (Hybrid Feed)**：在一个时间流中同时展示订阅源的更新和符合用户关注主题（语义匹配）的全网文章。
- **智能去重与排序**：基于发布时间和语义相关性进行智能排序。

### 4. 热点趋势分析
- **智能词云**：基于订阅源或全局数据生成关键词云，利用 LLM 清洗同义词。
- **全网热点事件**：采用 Map-Reduce 架构，利用 AI 从海量碎片信息中聚合、打分并生成全球热点事件榜单。

### 5. 实验与优化
- **Prompt 实验室**：支持创建对比实验，测试不同模型和 Prompt 对分析结果的影响。
- **动态配置**：支持 LLM 参数（Temperature, Top-P 等）和系统配置的热更新，无需重启。

---

## 🏗 系统架构

系统采用 **"采集 (Collector) — 分析 (Analyzer) — 展示 (Presenter)"** 三层架构模型，并基于 **事件驱动 (Event-Driven)** 机制解耦各模块。

### 技术栈

#### 后端 (Backend)
- **核心框架**: Spring Boot 4
- **开发语言**: Java 25 (利用虚拟线程处理高并发)
- **AI 集成**: Spring AI
- **数据库**: PostgreSQL 17 + pgvector (存储结构化数据与向量数据)
- **架构模式**: DDD (领域驱动设计), RESTful API

#### 前端 - 用户端 (Fronted-User)
- **框架**: Vue 3 + Vite
- **UI 库**: TailwindCSS v4
- **可视化**: ECharts (词云与图表)
- **主要功能**: 混合时间线、语义订阅、热点浏览、文章详情与推荐。

#### 前端 - 管理端 (Fronted-Admin)
- **框架**: Vue 3 + Vite
- **UI 库**: TailwindCSS v4
- **可视化**: Chart.js
- **主要功能**: RSS 源管理、Prompt 管理、实验配置、系统监控。

---

## 📂 项目结构

```
RSSwithAI/
├── src/                # 后端 Java 源码 (Spring Boot)
├── fronted-user/       # 前台用户端源码 (Vue 3)
├── fronted-admin/      # 后台管理端源码 (Vue 3)
├── doc/                # 项目文档
├── pom.xml             # Maven 依赖配置
└── README.md           # 项目说明
```

---

## 🚀 快速开始

### 1. 环境准备

- **JDK**: Java 25
- **Database**: PostgreSQL 17 (必须安装 `vector` 扩展)
- **Node.js**: v18+ (用于前端构建)
- **Maven**: 3.x

### 2. 数据库配置

1. 创建数据库：
   ```sql
   CREATE DATABASE rsswithai;
   ```

2. 启用 pgvector 扩展：
   ```sql
   \c rsswithai
   CREATE EXTENSION vector;
   ```

3. 修改后端配置 (`src/main/resources/application.properties`)：
   ```properties
   spring.datasource.url=jdbc:postgresql://localhost:5432/rsswithai
   spring.datasource.username=your_username
   spring.datasource.password=your_password
   ```

### 3. 启动后端服务

```bash
mvn spring-boot:run
```
服务默认运行在 `http://localhost:8080`。

### 4. 启动前端服务

**启动用户端 (User App):**
```bash
cd fronted-user
npm install
npm run dev
```
用户端默认运行在 `http://localhost:5777`。

**启动管理端 (Admin Panel):**
```bash
cd fronted-admin
npm install
npm run dev
```
管理端默认运行在 `http://localhost:5173`。

### 5. 默认账号 (Admin)

- **用户名**: `admin`
- **密码**: `admin`
*(请在首次登录管理端后通过“个人中心”修改密码)*
