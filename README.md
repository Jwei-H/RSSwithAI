# RSSwithAI

基于RSS信息源的智能情报收集、分析与展示系统。

## 项目简介

RSSwithAI是一个智能情报收集分析系统，集成了RSS信息采集、AI内容增强、实验分析等功能，帮助用户从海量信息中快速获取有价值的内容。

## 核心功能

- **RSS源管理**：支持多种RSS源类型，定时抓取最新内容
- **AI内容增强**：自动生成文章概览、关键信息和标签
- **向量化处理**：生成文章向量表示，支持智能检索
- **实验管理**：创建AI分析实验，对比不同模型和提示词的效果
- **灵活配置**：支持动态配置系统参数和模型参数

## 技术栈

- **后端**：Spring Boot 4, Java 25
- **AI集成**：Spring AI
- **数据库**：PostgreSQL 17 with pgvector
- **设计模式**：DDD领域驱动设计、RESTful API

## 快速开始

### 环境要求

- JDK 25
- PostgreSQL 17 with pgvector扩展
- Maven 3.x

### 配置数据库

1. 创建数据库：
```sql
CREATE DATABASE rsswithai;
```

2. 启用pgvector扩展：
```sql
CREATE EXTENSION vector;
```

### 配置应用

修改`src/main/resources/application.properties`中的数据库连接信息：

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/rsswithai
spring.datasource.username=your_username
spring.datasource.password=your_password
```

### 运行应用

```bash
mvn spring-boot:run
```

### 默认账号

- 用户名：admin
- 密码：admin

（请在首次登录后通过系统配置修改密码）

## 模块说明

- [RSS源管理模块](doc/rss_source_module.md) - 管理RSS订阅源
- [文章管理模块](doc/article_module.md) - 管理文章数据
- [大语言模型处理模块](doc/llm_processing_module.md) - AI内容增强
- [模型配置模块](doc/model_config_module.md) - 管理模型配置
- [提示词模板模块](doc/prompt_template_module.md) - 管理提示词模板
- [实验模块](doc/experiment_module.md) - 执行AI分析实验
- [系统配置与认证模块](doc/system_config_auth_module.md) - 系统配置和用户认证

## API文档

详细的API文档请参考 [API参考文档](doc/api_reference.md)

## 开发规范

详细的开发规范请参考 [开发规范文档](doc/00_develop_specification.md)

## 文档

- [项目概述](doc/01_project_overview.md)
- [开发规范](doc/00_develop_specification.md)
- [API参考](doc/api_reference.md)
- [前端需求](doc/fronted_admin_requirements.md)