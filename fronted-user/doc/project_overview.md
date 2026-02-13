# RSSwithAI 前台项目（fronted-user）- Agent 指南

## 项目概述

**RSSwithAI 前台**是一个基于 Vue 3 的现代化 RSS 阅读器应用，集成了 AI 增强功能，为用户提供智能化的内容订阅和阅读体验。

### 核心特性
- **智能订阅系统**：支持 RSS 源订阅和基于语义的主题订阅
- **AI 增强内容**：自动生成文章概览、关键信息提取
- **混合时间线**：聚合 RSS 文章和语义匹配的个性化内容
- **热点趋势**：全网热点事件榜单和关键词云可视化
- **浏览历史**：本地存储的阅读历史记录，支持进度记忆
- **无限滚动**：流畅的无限滚动加载体验
- **响应式设计**：桌面端/移动端自适应布局，移动端底部导航
- **高性能缓存**：内置数据缓存层，优化加载速度

### 技术栈
- **前端框架**：Vue 3（Composition API + `<script setup>`）
- **开发语言**：TypeScript
- **构建工具**：Vite 7.x
- **UI 框架**：shadcn-vue（New York 风格）+ Tailwind CSS 4.x
- **路由**：Vue Router 4
- **Markdown 渲染**：markdown-it（支持代码高亮、KaTeX 公式）
- **数据可视化**：ECharts + echarts-wordcloud（词云）
- **图标库**：Lucide Vue Next
- **状态管理**：Pinia（stores/session.ts、theme.ts、toast.ts、ui.ts）
- **HTTP 客户端**：Fetch API（封装在 services/api.ts）

### 项目架构
```
src/
├── assets/           # 静态资源
├── components/       # Vue 组件
│   ├── articles/    # 文章相关组件
│   ├── common/      # 通用组件（空状态、错误状态、加载状态、Toast）
│   ├── discover/    # 频道广场组件
│   ├── layout/      # 布局组件（图标栏、页面外壳）
│   └── trends/      # 趋势组件（热点事件、词云）
├── composables/     # 组合式函数（useInfiniteScroll）
├── lib/             # 工具库（utils.ts）
├── router/          # 路由配置
├── services/        # API 服务层
│   ├── api.ts      # HTTP 基础封装
│   └── frontApi.ts # 业务 API 调用
├── stores/          # Pinia 状态管理（含 history.ts, cache.ts 等）
├── types/           # TypeScript 类型定义
├── utils/           # 工具函数（markdown、text、time）
└── views/           # 页面视图
    ├── HistoryView.vue # 历史记录页
    └── ...
```

---

## 环境配置

### 前置要求
- Node.js 18+（项目使用 Node 24.10.9）
- npm 或 pnpm

### API 基础配置
- **Base URL**：`http://localhost:8080`
- **认证方式**：JWT Bearer Token（存储在 localStorage）

---

## 核心功能模块

### 1. 认证模块（Auth）
**API 位置**：`services/frontApi.ts` - `authApi`

- **注册**：`POST /api/register`
- **登录**：`POST /api/login`（保存 Token 到 localStorage）
- **刷新 Token**：`POST /api/refresh`（401 时自动刷新）
- **个人信息**：`GET /api/user/profile`
- **修改用户名**：`PUT /api/user/username`
- **修改密码**：`PUT /api/user/password`

**状态管理**：`stores/session.ts` - `isAuthenticated()`

### 2. 订阅系统（Subscription）
**API 位置**：`services/frontApi.ts` - `subscriptionApi`

- **获取订阅列表**：`GET /api/front/v1/subscriptions`（支持 RSS 和 Topic 混合）
- **创建订阅**：`POST /api/front/v1/subscriptions`（type: RSS/TOPIC）
- **取消订阅**：`DELETE /api/front/v1/subscriptions/{id}`
- **创建主题**：`POST /api/front/v1/topics`（content ≤ 30 字）

**关键点**：
- RSS 订阅会自动获取图标（使用 `getIconUrl(link)`）
- Topic 订阅基于语义向量匹配

### 3. 文章模块（Article）
**API 位置**：`services/frontApi.ts` - `feedApi`

**核心接口**：
- **时间线**：`GET /api/front/v1/articles/feed`（cursor 无限滚动）
- **按源预览**：`GET /api/front/v1/articles/source/{sourceId}`（分页）
- **文章详情**：`GET /api/front/v1/articles/{id}`
- **AI 增强信息**：`GET /api/front/v1/articles/{id}/extra`
- **智能搜索**：`GET /api/front/v1/articles/search`（支持模糊匹配 + 向量相似度）
- **相似推荐**：`GET /api/front/v1/articles/{id}/recommendations`（最多 2 条）
- **收藏操作**：`POST /api/front/v1/articles/{id}/favorite` / `DELETE /api/front/v1/articles/{id}/favorite`
- **收藏列表**：`GET /api/front/v1/articles/favorites`（分页）

**分页策略**：
- 时间线和搜索：基于 cursor 的无限滚动
- 源预览和收藏：基于 page/size 的传统分页

### 4. 热点趋势（Trends）
**API 位置**：`services/frontApi.ts` - `trendApi`

- **词云**：`GET /api/front/v1/trends/wordcloud`（可选 sourceId 参数）
- **热点事件**：`GET /api/front/v1/trends/hotevents`（Top10，score 1-10）

### 5. RSS 源管理（RSS Source）
**API 位置**：`services/frontApi.ts` - `rssApi`

- **获取源列表**：`GET /api/front/v1/rss-sources`（支持 category 筛选，分页）

### 6. 浏览历史（History）
**状态管理**：`stores/history.ts`

- **存储方式**：LocalStorage（key: `rss_reading_history`）
- **数据结构**：文章ID、标题、来源、封面、阅读进度、最后阅读时间
- **策略**：
    - 自动清理超过 30 天的记录
    - 记录阅读进度（百分比）
    - 清空历史功能

### 7. 数据缓存（Cache）
**状态管理**：`stores/cache.ts`

- **缓存对象**：
    - 热点事件（HotEvents）
    - RSS 源列表（分页）
    - 源文章列表
- **策略**：
    - 内存缓存（非持久化）
    - 有效期 5 分钟
    - 支持手动强制刷新

---

## 页面结构与交互

### 1. 认证页面
- **登录页**：`views/LoginView.vue`（路径：`/login`）
- **注册页**：`views/RegisterView.vue`（路径：`/register`）

### 2. 主要页面（需要登录）
页面布局根据设备类型自适应：

**桌面端**：
- **左列**：侧边栏（导航 Rail + 二级导航）
- **中列**：内容列表
- **右列**：信息区

**移动端**：
- **底部**：`MobileBottomNav` 导航栏
- **主体**：单栏内容流
- **弹窗**：文章详情使用全屏覆盖层


#### 订阅页（SubscriptionsView.vue）
**路径**：`/subscriptions`（默认首页）

**功能**：
- 左侧：订阅列表（支持"全部订阅"切换、创建 Topic、取消订阅）
- 中间：混合时间线（无限滚动，支持单订阅过滤）
- 右侧：词云（与当前订阅联动）+ 文章 AI 预览（hover 触发）

**关键交互**：
- hover 文章卡片 → 右侧展示 AI 预览（overview + keyInformation）
- 点击文章 → 进入文章详情模式（不跳转页面）
- 切换订阅 → 时间线和词云同步刷新

#### 频道广场页（DiscoverView.vue）
**路径**：`/discover`

**功能**：
- 顶部搜索框（全站搜索，searchScope=ALL）
- 热点事件 Top10（支持一键订阅为 Topic）
- RSS 源列表（卡片栅格，支持 category 筛选）
- 源文章预览弹窗（分页展示该源文章）

**关键交互**：
- hover 热点事件 → 右侧浮现订阅按钮
- 点击 RSS 源卡片 → 打开预览弹窗
- 点击预览列表文章 → 进入文章详情模式

#### 收藏页（FavoritesView.vue）
**路径**：`/favorites`

**功能**：
- 顶部搜索框（收藏搜索，searchScope=FAVORITE）
- 收藏列表（无限滚动，样式与时间线一致）

#### 历史记录页（HistoryView.vue）
**路径**：`/history`

**功能**：
- 展示最近 30 天的阅读记录
- 显示阅读进度条
- 支持清除所有历史
- 点击跳转到对应文章（恢复阅读进度）

#### 个人中心页（ProfileView.vue）
**路径**：`/profile`

**功能**：
- 展示个人信息（头像、用户名）
- 修改用户名
- 修改密码
- 退出登录

### 3. 文章详情模式（全局状态）
**实现方式**：覆盖当前页面内容，不跳转路由

**布局**：
- 左列：文章正文（Markdown 渲染，支持"打开原文"）
- 右列：AI 信息（精华速览 + 关键信息 + 推荐文章）

**交互**：
- 左上角"返回"按钮 → 恢复到进入前的列表状态和滚动位置
- 点击推荐文章 → 在详情模式中切换到新文章（支持返回上一层）

---

## 全局交互规范

### 1. 认证与鉴权
- 未登录用户只能访问 `/login`、`/register`
- 登录成功后保存 JWT Token，所有请求携带 `Authorization: Bearer <token>`
- 接口返回 401 → 自动调用刷新 Token → 失败则跳转登录页

### 2. 时间显示
所有 `pubDate` 字段必须格式化为"xx时间前"（如：5分钟前、2小时前、3天前、2025-12-01）

**工具函数**：`utils/time.ts`

### 3. 无限滚动
- 使用 composables/useInfiniteScroll 实现
- 支持加载中骨架屏、失败重试、到底提示

### 4. 空状态与异常
- 数据为空时展示明确的空状态组件（components/common/EmptyState.vue）
- 网络错误使用 Toast 轻量提示（stores/toast.ts）

### 5. Markdown 渲染
- 使用 markdown-it + highlight.js + markdown-it-katex
- 支持标题、列表、引用、代码块、图片、数学公式

**工具函数**：`utils/markdown.ts`

---

## API 错误处理

### 401 Unauthorized
- 调用 `POST /api/refresh` 刷新 Token
- 成功后重放原请求
- 失败则跳转登录页

### 404 Not Found
- 文章不存在 / AI 增强信息不可用
- 展示友好的错误提示

### 其他错误
- 使用 Toast 提示用户
- 不阻塞用户操作

---

## 开发规范

### 1. 代码风格
- 使用 Composition API + `<script setup>`
- 严格遵循 TypeScript 类型定义
- 使用 shadcn-vue 组件库（风格：new-york）
- Tailwind CSS 类名优先（cssVariables: true）

### 2. 组件拆分
- 可复用组件放在 `components/` 对应目录
- 避免过度拆分，保持合理的组件粒度
- 使用 `defineProps` 和 `defineEmits` 定义接口

### 3. API 调用
- 所有 API 调用封装在 `services/frontApi.ts`
- 使用 TypeScript 类型约束请求和响应
- 统一错误处理逻辑

### 4. 状态管理
- 使用 Pinia stores 管理全局状态
- 避免在组件间直接传递复杂状态

### 5. 工具函数
- 公共工具函数放在 `utils/` 或 `lib/`
- 纯函数优先，便于测试和维护

---

## 特殊约束

### 1. AI 增强信息
- **tags 字段不展示**（即使接口返回）
- 仅展示 overview 和 keyInformation

### 2. 图标处理
- RSS 源图标使用 `getIconUrl(link)` 生成
- Topic 订阅显示固定图标或默认图标

### 3. 文章收藏
- 幂等操作（重复收藏/取消收藏不报错）
- 支持在文章详情和时间线中快速收藏/取消

### 4. 搜索范围
- 全站搜索：searchScope=ALL
- 订阅搜索：searchScope=SUBSCRIBED
- 收藏搜索：searchScope=FAVORITE

### 5. 静态资源 URL 重写
- 为避免部分图片防盗链导致加载失败，对图片 URL 进行重写
- 规则配置位置：`src/utils/url-rewrites.ts`
- 应用范围：文章正文图片（Markdown 渲染）与文章卡片封面

---

## 文档参考

### 项目文档（doc/）
- `front_api_reference.md` - API 文档（完整的接口说明）

### 外部文档
本项目采用较新的技术栈，如果有不确定的用法，请调用context7工具查阅相关文档，举例，查询shadcn-vue的文档，libraryId 为`/websites/shadcn-vue`。