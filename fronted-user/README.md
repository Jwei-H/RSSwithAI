# RSSwithAI 前台项目

基于 Vue 3 构建的现代化 AI 增强型 RSS 阅读器，旨在提供智能化的内容消费体验。

## ✨ 核心特性

- **智能订阅**：支持标准 RSS 源订阅和基于语义的主题订阅。
- **AI 增强**：自动生成文章概览和关键信息提取。
- **混合时间线**：聚合 RSS 文章和个性化主题内容的统一流。
- **热点趋势**：实时热点事件榜单和关键词云可视化。
- **响应式设计**：移动端优先的自适应能力，流畅的卡片式布局。
- **本地历史**：客户端本地存储阅读历史，支持阅读进度记忆。
- **性能优化**：内置本地缓存策略和无限滚动加载优化。

## 🛠 技术栈

- **框架**：Vue 3 (Composition API) + TypeScript
- **构建工具**：Vite 7
- **UI 系统**：shadcn-vue + Tailwind CSS 4
- **状态管理**：Pinia
- **图标库**：Lucide Vue Next

## 🚀 快速开始

### 前置要求

- Node.js 18+ (推荐: 20+)

### 安装

```bash
npm install
# 或
pnpm install
```

### 开发

```bash
npm run dev
```

应用默认将在 `http://localhost:5173` 启动。

## 📂 项目文档

更多详细信息，请参阅 `doc/` 目录下的文档：

- [项目概览 (Project Overview)](doc/project_overview.md)：架构与功能的完整指南。
- [API 参考 (API Reference)](doc/front_api_reference.md)：详细的 API 接口文档。

## 📱 PWA 支持

项目已集成 `vite-plugin-pwa`，在 `production build` 下会自动生成：

- `manifest.webmanifest`
- Service Worker（Workbox）

### 本地验证

```bash
npm run build
npm run preview
```

然后在浏览器打开预览地址：

- Chrome DevTools → `Application` → `Manifest` / `Service Workers`
- 地址栏出现“安装应用”提示时即可安装

### 注意事项

- Service Worker 默认仅在 `localhost` 或 `https` 环境生效。
- 当前 manifest 图标使用 `public/rss.svg` 和 `public/vite.svg`，建议后续补充 `192x192`、`512x512` 的 PNG 图标以获得更好的安装体验。
