# 详细设计与功能点分析文档 - RSSwithAI FrontEnd

# 模块：全局架构与基础路由 (Global Architecture & Routing)

## 1. 核心骨架入口 (`src/main.ts`)

`main.ts` 作为前端应用的唯一初始化入口，执行了以下几个核心步骤：
1. **样式装载**：引入 `style.css` (包含 Tailwind CSS 的核心指令与 Shadcn-vue 所需的 CSS 变量) 以及 `highlight.js/styles/github.css` (为之后的 Markdown 代码块渲染提供高亮主题)。
2. **状态水化 (Hydration)**：
    - `initSession()`: 探测 `localStorage` 内的 `rss_token` 和 `rss_profile`，以恢复用户的登录状态（无需等待后台校验即可在 UI 层表现为登录态），供路由鉴权使用。
    - `useThemeStore().initTheme()`: 立即执行暗黑/明亮模式的主题初始化。
3. **PWA 注册**：`registerPwa()` 安装 ServiceWorker 脚本。
4. **统一挂载**：将 Vue 应用实例与 `router`（路由管理器）绑定后挂载至 `#app`。

## 2. 根组件与布局外壳 (`src/App.vue`)

`App.vue` 接管了最高层级的页面呈现以及各端适配。主要职能如下：

### 2.1 顶级组件装载
- **ToastHost**: 为全局消息提示（Toast）提供唯一挂载点，通过 `z-index` 确保覆盖在所有层级之上。
- **WelcomePopup**: 首次登录后的欢迎弹窗容器。
- **ArticleDetailOverlay**: 特殊处理的“覆盖式层级”，受 `uiStore.detailOpen` 状态驱动。在除了详情页外的大多数特定路由中（由前端判断：不处于特定需要隔离的路由段），详情页开启后会接管界面的主体呈现层。

### 2.2 响应式布局自适应 (Responsive Shell)
应用依托于 Tailwind 的媒体查询能力，配合设备的宽度与特定路由，拆分了桌面与移动的呈现外壳结构：
- **容器限制**：外层容器锁定高度 `h-dvh`（适配移动端浏览器的动态视口），文本主色调为 `text-foreground`，并运用了渐变与混色 `bg-muted/40`。同时，由于个人中心需要原生的外部下拉回弹，`allowMobileOuterScroll` (计算自 `route.path.startsWith('/profile')`) 将特定的外层 `overflow` 解锁。
- **导航分布差异**：
    - **Web端 (md: flex)**: 调用 `<IconRail />` 挂载至屏幕最左侧。包含品牌 Logo 与“频道、历史、订阅、收藏、个人中心”等高频路标图标，并监听当前 `route.path` 执行自身高亮。
    - **移动端 (md: hidden)**: 调用 `<MobileBottomNav />` 悬浮于屏幕最底端 `fixed inset-x-0 bottom-0 z-50`。为适配底部安全区，设置了 `pb-safe` (safe-area-inset-bottom)。根据登录状态展示不同数量的（通常为4-5个）小图标 + 文本底栏。

### 2.3 动态与智能文档标题 (Document Title Manager)
利用 `watch` 原型，监测 `route.path` 并随时变更高优先级的窗口层级参数，如遇文章详情打开（`ui.detailOpen === true`），则标题锁定为“阅读 - RSSwithAI”；退回界面后则复原为对应的功能窗区标题。

---

## 3. 路由与多级鉴权 (`src/router/index.ts`)

由 `vue-router` 进行页面路径的管理，并构建了守卫。

### 3.1 路由映射与懒加载
所有的页面采用箭头函数与 `() => import()` 方法包裹，这意味着代码在构建为生产镜像时，会针对单一视图（View）实现 **Code Splitting (代码分割)**，极大缩小首屏包的下载体积。

### 3.2 守卫鉴权 (Route Guard)
通过拦截器 `router.beforeEach` 进行双重检验：
1. **公开白名单**：如果前往 `to.meta.public` 被标记为 `true` 的路由页面（如 `/login`, `/register`, `/discover`, `404`），直接放行。
2. **状态验证**：如果非公开访问（如：`/subscriptions`、`/profile`），探查本地内存池中的 `isAuthenticated()`，通过则予以放行；否则执行阻截，将用户强制路由回 `/login`，并附带宽恕参数 `?redirect=原路径`。以便其在认证完毕后自动引导回想去的界面。

---

## 4. 辅助布局组件 (Layout Helpers)

- `PageShell.vue`: 一个高度抽象的“侧边-主区-侧边”或“侧边-主区”模板，接收布尔值 `props.showRight` 决定右侧列是否展示。采用具名插槽 (`<slot name="sidebar" />` 等) 提供向上的抽象。其被广泛用于不需要极端定制的双栏或三栏页面中，实现桌面段固定尺寸 `grid-cols-[280px_1fr_320px]` 与移动端自适应收缩的无缝切换。
# 模块：认证与个人信息 (Auth & Profile)

## 1. 登录模块 (`LoginView.vue`)

登录页面是系统的第一道大门，承担着获取令牌并开启用户会话的责任。

### 1.1 状态与表单控件
- 维护 `username`、`password`、`loading`（防重复点击）以及 `error`（提交反馈）。
- 支持 URL 回显绑定：`route.query.username` 如果存在，自动推入至用户名输入框，此机制常接在用户刚刚完成注册跳入登录的情景。

### 1.2 认证逻辑 (`submit`)
1. **预校验**：一旦点击立即抛出前端校验（未填必填项报错拦截）。
2. **握手请求**：调用 `authApi.login`（`POST /api/login`）取得携带的 jwt 令牌。
3. **设置长效缓存**：将取得的 `token` 同步推给 `sessionStore.setToken`（内部映射入 `localStorage.rss_token`）。
4. **拉取附加用户信息**：串行调用 `authApi.profile()` 拉起完整的 `Profile` 详情集合，推入 `sessionStore.setProfile` (`localStorage.rss_profile`)。
5. **欢迎层引爆**：执行 `session.openWelcome()` 点亮全局唯一的首次登入欢迎屏 (`WelcomePopup.vue`)。
6. **重定向跳引**：依据来时的足迹（路由拦截器写入的 `redirect` 查询参数）或者默认返回主菜单 `/subscriptions` 执行 `router.replace` 剔除登录历史页面。
7. **异常分支**：拦截错误响应块直接反馈入 `error`。在此期间按钮皆显示 `...loading` 并 `<button :disabled>`。

## 2. 注册模块 (`RegisterView.vue`)

与登录类似的模型构建：
- 新增 `confirm` 二次密码校验，如果检测到与 `password` 录入数据相左，则触发快速表单拦截，避免无意义网络请求。
- 通信 `authApi.register({ username, password })`，成功后拉取 `toast` 提示器触发轻量通知，并通过 `router.push` 将目标打回 `/login` 带参复用：`?username=${username}` 以便于免填录入。

## 3. 个人属性与偏好设置 (`ProfileView.vue`)

个人偏好设立为一个符合标准包含 `#sidebar`（移动段隐匿）、`#main`、`#right`（移动段隐匿）插槽的 `PageShell` 经典视图。

### 3.1 主题控制器
- 调用 `stores/theme`。通过列陈三种状态选择 `跟随系统(system)`、`浅色(light)`、`深色(dark)` 驱动。
- UI回显绑定了当前的激活控制按钮，同时利用底色与虚隐进行弱对比以告诉用户 `theme.effectiveMode` 真正施展的背景极性。

### 3.2 口令更迭 (`updatePassword`)
- 提供 `oldPassword` 和 `newPassword`。请求结束后采用乐观更新模型将密码表位清空。
- `authApi.updatePassword` 作为异步桥梁。

### 3.3 退出生命周期清理 (`logout`)
针对多网端退出处理。
1. 调用 `sessionStore.clear()`。
2. 切断： `token=''`，`profile=null`，`welcomeShown=false`。
3. 回收： `localStorage.removeItem` 分别剪除两个验证头，跳回 `/login` 形成闭环。
# 模块：订阅大厅与信息流 (Subscriptions & Timeline)

这是系统的最核心数据呈现区，代码量庞大（在 `SubscriptionsView.vue` 超 1000 行），它整合了左侧频道侧边栏、中间的时间线列表流以及右部的 AI 词云和预览组件（Web 端特有）。

## 1. 订阅列表管理 (Subscription Management)
页面起手会在 `onMounted` 阶段拉取用户的个人订阅列表，包含 RSS 以及 Topic（话题订阅）两类混合。
- **合并展现**：通过 `computed` 属性 `orderedSubscriptions`，强制让所有的 RSS 类型排在前面，TOPIC 主题排在尾随部分，保证 UI 的规整。
- **状态同步与持久化**：加载完毕使用 `cache.setSubscriptions` 进行保存。
- **移动端抽屉适配**：由于移动区域逼仄，左侧列表被重组为一个名为 `showMobileSheet` 的下拉绝对定位抽屉，在顶部遮罩背景下由底部滑出触发选择。
- **主动取消**：允许针对单项进行取消 `onCancelSubscription()`，这不仅会清除内存响应式数组，还会深潜入 `cacheStore`，利用 `syncRssSourceSubscription` 清除全站（如发现页）的关联订阅标记。

## 2. 时间线流式加载与分页 (Timeline Feed)
使用 Cursor (游标) 实现时间线的向下推进，而非传统的 Page 数组，以防御高频更新下的偏移。

### 2.1 IntersectionObserver 无限滚动
由组合式函数 `useInfiniteScroll` 提供底层实现。
- **哨兵元素 (Sentinel)**：在 Feed 列表最底部放置一个无内容或仅用于 Loading 文字高度的空 Div。将其 `ref="sentinel"` 送入组合函数中。
- **触碰阈值**：当 `IntersectionObserver` 捕捉到该哨兵元素进入视口，且 `hasMore=true` 时，立即派发 `loadMore (loadFeed)` 函数进行追加加载。新数据取得后更新游标 `feedCursor` (记录如 `${last.pubDate},${last.id}`)。

### 2.2 “拉取刷新”仿原生交互 (Pull-to-refresh，限定移动版)
代码自行原生编写，依托于原生 `touch` 事件构建了具备阻尼的手势逻辑。
1. `@touchstart`：记录 `pullStartY`（起按 Y 轴），开启状态锁 `pulling`。
2. `@touchmove`：运算拉动距离的 `delta`。仅当顶部滚动为 `scrollTop === 0` 时介入。公式 `Math.min(120, delta * 0.5)` 使阻力增加，UI高最大下移 120px，防止画面撕裂。
3. `@touchend`：如果松手时 `pullDistance >= 72px` 阈值，正式上锁 `pullTriggered`，触发并行的全站状态刷新函数 `refreshAll()`。

### 2.3 基于时间的日期切片器
为了提高阅读的心理掌控感，获取的数据并不只是纯线性展示。<br/>
提供了一个叫 `feedDisplay` 的计算属性，通过判断列表中连续 ArticleFeed 的发布时间（利用 `resolveArticleDateKey` 解析前缀），如果发生跨天，强行在其中插入一个 `{ type: 'separator', date: '今天'/'昨天'/或特定日期 }` 的游离元素渲染为时间分隔线 `separator`。该逻辑配合了底层的 `dayRolloverTimer` 跨越定时器实现0点跨天自动重算日历文本。

### 2.4 “未读过滤” (Unread Only)
顶部内置一个眼睛状 `Eye/EyeOff` Toggle。该计算属性结合 `useHistoryStore`。一旦切入未读，会瞬间把内存池的 Feed 进行倒装：`!historyStore.isRead(item.id)`，实现纯净信息流。

### 2.5 降维宽泛搜索
在时间线表头提供的快捷搜索框，它具备作用域嗅探能力（`resolveSearchScope`）：
- 当定焦“全部订阅”，向后端传递 `scope=SUBSCRIBED`（我订的池子）。
- 当定焦特定的 RSS 频道，传递 `scope=ALL, sourceId=X`（变成精准的频道库内搜索）。
- 当定焦特定的 Topic（热点话题），传递 `scope=ALL, sourceId=undefined`（泛发全站搜索）。
所有搜寻动作通过 URL `?q=xxx` 响应式双绑绑定。

---

## 3. 右侧 AI 前瞻与网络节流预挂载

此部分专供 Web 桌面大屏独享 `(!isMobile)`，实现如极客浏览般的丝滑质感。

### 3.1 悬停秒回预览 (Hover Pre-fetch)
借由 Vue 的 `@mouseenter` 和 `@mouseleave` 事件，绑定 `onHoverArticle(id)`。
- **防抖阀门**：配置 200ms 的 `hoverTimer`。仅当用户确切停留在某个 ArticleCard 超过 0.2 秒后，才视为有诚意的预读。
- **状态注入**：此时触发向 `ArticlePreviewPanel.vue` (右下侧) 发送异步请求 `feedApi.extra()` 提取 AI 概览短文本。

### 3.2 可视区域全静默预加载 (Visible Prefetching)
系统的终极追求：用户在点击前，系统已经拉取好了一切（针对 AI Extra 进行静默请求预存）。代码引入了双队列系统维护性能与体验平衡：
- 重新利用 `IntersectionObserver` 监控 `listContainer`（根），寻找所有的 ArticleItem 组件元素卡片。
- 当有卡片进入视口，提取其 `dataset.articleId`；检查是否已被请求过（是否存在于缓存表 `cache.getArticleExtra(id)`）。若不存在，则作为候选人压入 `queuedPrefetchIds`。
- **三道并发锁 (Concurrency Limit)**：函数 `runPrefetchQueue()` 限制最高只有 `3` 条请求线（`maxPrefetchConcurrency`）在向后端要数据，防止高频向上向下滚动压垮后端并使得用户自身的其他关键请求受阻。取得的内容直接被灌入 `cache.setArticleExtra(id, data)`。

## 4. 词云趋势卡片联动 (WordCloud)
订阅大厅不仅呈现时间线，还呈现词汇趋势。（非话题订阅源下）利用 `loadWordCloud` 从后台并行接口拉取频次排序数组：`[{ text: string, value: number }]`，右上方置的 `WordCloudCard` 将之通过内部画幅图谱转化。如果是移动端，该微缩卡片会被挤压放置在列表时间线的第一项前部展示。
# 模块：文章详情展现层与 AI 增强层 (Article Detail & Extra)

文章详情组件 `ArticleDetailPane.vue` 是整个项目中最复杂的可视化构件。它不仅承担了 Markdown 高级渲染，还要将后台分析出的结构化 AI 数据无缝融入到阅读流中，并维持用户精湛的交互体验。

## 1. 响应式双态结构与重组

组件并未设计为独立的物理路由（Route），而是一个基于覆盖层（Overlay）理念弹出的面板。

- **Web 桌面端：支持拖拽宽度的双栏布局**。
  - 左侧：单纯呈现文章的标题头与 Markdown 长文本正文 (`leftPaneRef`)。
  - 右侧：常驻挂载 AI 相关的扩增块（“精华速览”、“关键信息”、“动态抽取目录”、“跨源相似推荐”）。
  - **动态拖拽分割线**：中间嵌有 `w-1 cursor-col-resize` 句柄，绑定 `onMouseMove`，允许用户在 `35%` 到 `72%` 视宽内自由分配左右侧阅读屏展比。
- **移动设备：单柱铺排展示**。
  - 左侧边栏在手机隐藏。信息结构被大幅度竖向重排：`文章元信息 -> 精华速览(AI) -> 关键信息(AI) -> 目录导航(AI) -> Markdown正文(极长) -> 相似推荐`。
  - 全部装载于 `mobileScrollRef` 滚动窗体中。

## 2. 内容获取与融合展示

### 2.1 高并发挂载解析 (`load()`)
针对长文访问慢的痛点，使用了 `Promise.allSettled` 并行发出三股请求流：
1. **获取详情 `detail`** (优先通过本地 `cache` 推断填补屏幕，后台接管请求保证最新状态，包含标题、原文等)。
2. **获取扩增 `extra`** (拉取 AI 抽取的 overview、keyInformation、toc)。
3. **获取推荐 `recommendations`** (底层基于向量相似度，拉取可能喜爱的关联阅读列表)。

### 2.2 Markdown TOC 无缝注入组装 (`mergeAiTocIntoContent`)
AI 返回的内容附带有基于段落归纳出的多级目录（即不在 Markdown 原文内直接显现的标题逻辑）。前端执行组装函数：
- 识别 `extra.toc` 中的标题与文本内容锚点（抽取字串的头几个字）。
- 去原文大文本（`content`）中进行游标 `indexOf` 测算定位。
- 通过正则注入如 `## 标题` 的 Markdown 符号，在指定段落头部强制辟出换行并追加标签。
- 最终产出合成文本 `mergedContent`，将其推入本地缓存。

## 3. 增强型 Markdown 渲染体系

将 `mergedContent` 交与底层解析器执行之前/之后的处理：

### 3.1 基础渲染与语法高亮
调用 `utils/markdown.ts` 中封装的 `markdown-it` 引擎，解析基础文本。通过 `highlight.js` (于 `main.ts` 加载了 Github 样式) 供给代码块色彩包裹；通过 `markdown-it-katex` 对理工及财会内容中的数学算式与公式加以支撑。

### 3.2 Mermaid 拓扑图谱动态装载
对于 `class="mermaid"` 类的节点。如果检测到有流程图：
- 避免全站主包体积暴涨，执行**异步动态按需引入** (`await import('mermaid')`)。
- 当处于暗夜模式（随用户终端环境变化），向参数内自动设定 `theme: 'dark'` 以规避图谱配色被屏幕底色吞噬。
- 在 DOM 的 `nextTick` 期清除原生属性并执行 `mermaid.run()` 原地画图。

### 3.3 图片无缝暗箱 (Image LightboxPreview)
借助原生的 DOM 事件委派，通过在主外壳绑定 `@click="onMarkdownClick"`，捕获所有底下的 `.markdown-body img` 对象：
- 读取其实际的 `src` 和 `alt`。
- 拉起一个全覆屏 `fixed z-80` 的遮罩。在不跳转新页面的情况下居中浮现该大图，支持外击关闭或键盘 `ESC` 中止。

### 3.4 交互层：标题块体折叠 (setupCollapseToggle)
为了避免大段无关内容过载视心：
- 组件监听渲染的 `HTML` 块并寻找通过解析器置入的标题开关 `<button class="md-heading-toggle">`。
- 创建闭包区域 `getContentBetweenHeadings` 即时演算，动态封装一段到下一个同级/更高级标题前的所有 HTML 节点为其设立容器。
- 切换 `aria-expanded` 控制容器显隐。

## 4. “读至某处”联动的双向观测器

这里有严密的埋点以及指示器绑定追踪能力。

### 4.1 目录阅读视口高亮 (Scrollspy Tracking)
- 当用户在阅读框（左盘面 / 手机整段面）进行滑动时，会触发节流计算。
- 探测当前容器内的全部 `h1~h6` 锚点元素与顶端视口的偏移比 `offsetTop`。
- 将进入屏幕上半测光区 `clientHeight * 0.25` 的标题提取出来，反向推回状态 `activeHeadingId` 中。
- 并借此使右侧 AI 目录树 (`tocItems`) 上的对应菜单发生字体蓝色高亮化 (`text-sky-600 font-semibold`)。

### 4.2 进度反馈与历史快照留存 (`updateReadingProgress`)
- 每当用户结束一次有意义的滑拽（例如手机特有 `@touchend` 后延时 150ms 等待越界回弹结束）。
- 公式推导当前滑动进程：`progress = scrollTop / (scrollHeight - clientHeight)`。
- 发送给 `historyStore.updateProgress`，它会在浏览器内存存下类似 `{ articleId: X, readProgress: 0.82 }`，用于之后回到主大厅的 “眼睛图符” 判断是否标亮为“已读”，或返回历史频道查看具体的浏览纵深比例饼图。
# 模块：发现频道与热点事件大厅 (Discover & Hot Events)

`DiscoverView.vue` 承载了全站搜索、热点事件（Topic）榜单展示、RSS源分类发现、以及两种维度的在线预览弹窗。

## 1. 结构化布局与跨端呈现

不同于订阅主页的重度两片/三片式切割，发现页更多采用卡片陈列。

### 1.1 Web端大屏陈列
- 左侧边栏：固定的 “热点事件追踪区”。包含 `HotEventsList`。以及一个用于快捷创建并订阅字面主题（Topic）的输入框组件。
- 中部核心：全站宽泛搜索入口 `+` 当前存在的公共 RSS 分类检索库。在网格中双列铺排 `RssSourceCard`。

### 1.2 Mobile 移动端流式收起
- 为了不在手机首屏挤占过多核心空间：
  - “快捷创定主题” 被扁平化放在了最顶部行内表单内。
  - 热点事件清单通过 HTML5 `<details>` 原生折叠标签实现。通过内部运算 `mobileVisibleCount`，默认在手机段只放出前 15 条(`HOT_EVENTS_DEFAULT_VISIBLE_COUNT`)，留出 `更多` 拓展按钮，保证下方 RSS 分类卡位在视觉可见范围内。

## 2. 状态融合与一致性调度 (`syncWithSubscriptionCache`)

此界面的数据不仅来自于公用接口 `trendApi` 及 `rssApi`，它还需要与用户的“已订阅”私有库保持高强度的表现一致。
一旦界面在 `onActivated`（Vue Router缓存恢复）或 `onMounted` 阶段，会触发 `syncHotEventsWithSubscriptionCache` 与 `syncSourcesWithSubscriptionCache`。
- 函数遍历本地 `cache.getSubscriptions()`，反查出热点的 `content` 或者 RSS 源的 `targetId`。
- 将视图模型中的卡片源附上 `isSubscribed: true` 且提取挂载其后端的真实 `subscriptionId`。从而实现用户在发现页可以直接点按源卡片上的“爱心/取消”做增删改。

## 3. 全局宽泛搜索入口 (Globally Search)
借由 `feedApi.search`。不同于 `SubscriptionsView` 内的近端上下文嗅探，这里的查询强制限定了入参 `scope: 'ALL'`（全公用库内容覆盖检索）。

## 4. 无闪现预览模式 (Preview Overlays)

这是发现页最提高沉浸感的逻辑：**预览而非直接订阅**。

### 4.1 RSS源内透视 (`SourcePreviewDialog.vue`)
- 当用户对不认识的分类源点击预览，跳出遮罩层。
- 后台通过单独的请求 `rssApi.articles(source.id, page)` 获取该未知源的过往文章进行切片渲染展示。其本质是复用了带无限滚动逻辑的一个微缩“时间线”。
- 即便是在预览的浮层中，用户也能长按或单击点破某篇文章深入观察。

### 4.2 热点 Topic 透视 (`HotEventPreviewDialog.vue`)
- 展示热点事件本身的文字、热度。
- 底层抓取通过 `trendApi.eventArticles(eventItem.event)` 收拢目前全网下谈论该事件的所有聚簇文章。
- 提供显著的快捷“立即订阅该话题”入口，由 `onSubscribeHot` 将其转为个人数据库内的 `TOPIC` 行。

### 4.3 路由弹窗锚点记忆
这两个预览框均带有极深的 URL 锚点双向路由透传：
- `?previewSourceId=` 对应 RSS 源弹窗。
- `?previewEvent=` 对应热点弹窗。
好处是，如果在手机浏览器中用户习惯性按下了“物理返回键”，利用 `watch route.query` 截获该请求，系统不是执行粗暴的页面跳出离开发现大厅，而是优雅地合上遮罩 `previewOpen.value = false`，保证状态连贯。
# 模块：阅读历史与收藏夹 (History & Favorites)

这两个模块在产品定义上属辅助轴线，但也承接了用户核心资产沉淀的重任。在代码结构上，它们存在着极高的相似性（列表呈现 + 详情覆盖），且在移动端 `FavoritesView.vue` 中巧妙地被揉捏于同一个路由下透过 Tab 切换。

## 1. 收藏夹体系 (Favorites)

收藏本质是从服务端远端拉取的高优持久化列表。

- **多端触达**：
  - Web端独享左侧菜单入口 `Favorites`。
  - 内部实现依托于 `feedApi.favorites()` 与服务端通讯，带有分页与无限滚动 `useInfiniteScroll` 逻辑。
- **强制一致性锁**：
  - 在 `onMounted` 阶段，通过比对新拉回的第一页流与本地 `cache` 的哈希特征（`isSameFeed`：校验 ID 和发布日期），决定是否抛弃旧缓存重绘画布。
- **全站联动（Favorite Toggle）**：
  - 用户在 `ArticleDetailPane` 中点按收藏时，会向 Backend 发起 `feedApi.favorite(id)`。
  - 同步调用 `cache.upsertFavorite(article)`，确保即便网络慢，用户回到收藏夹时马上能看到刚点的文章。

## 2. 浏览历史沉淀 (Reading History)

不同于收藏，**浏览历史纯粹依赖客户端本地计算与存储**。旨在打造一个无网可看且绝对涉及隐私防偷窥的沙盒。

- **存储中枢 (`useHistoryStore`)**：
  - 采用 Pinia 结合 `localStorage` 存放。配置了最长 30 天的回溯保留期。
- **无感记录触发**：
  - 它不由独立的页面维护，而是依附于文章详情板的 `load()` 尾部：只要文章成功打开并渲染，就会偷偷呼叫 `historyStore.addReading()` 追加对象。
- **进度追读还原 (Progress Tracking)**：
  - 文章组件内的防抖计算器每秒推演用户正文阅读滚动的纵深比 (`readProgress: 0.82`) 并送入。
  - 在 History 列表中，它被反向读取，并用一段迷你进度条 `<div class="h-full bg-primary" :style="{ width: ${item.readProgress * 100}% }" />` 直观地糊在了每张历史卡片的角落里。

## 3. 移动端融合 (Mobile Tab Switching)

为了适配移动端逼仄的下底栏，作者并未给 History 留出独立路由底座，而是将其融合至 `FavoritesView` 的躯干内。
- 引入 `activeTab = ref<'favorites' | 'history'>('favorites')` 控制器。
- 利用 URL 的查询参数 `?tab=history` 进行状态粘性投送。
- 在页面顶部刻画形如 `收藏 | 历史` 的分馏胶囊选项卡，点击时互斥展示下方的无限滚动集或本地内存表。

## 4. 彻底的隐私销毁
历史区给出了唯一一个强互动的破坏性操作：`onClearHistory`。由原生 `confirm` 发起最后通牒，继而调拨 `historyStore.clearAll()` 进行物理抹除 `localStorage` 中 `reading_history_v1` 的所有键值。
# 模块：全局状态与缓存层 (Pinia - Store & Cache)

由于是一个内容类/阅读类项目，缓存拦截是本项目的命脉。前端利用了 Vue 的 `reactive` 响应式 API 配合原生 `localStorage` 设计了不借助于外部复杂库的极简却高效的状态机器。

## 1. 超级缓存阀：`cache.ts`

这是整个站点抗压防抖的网关。所有前端对于列表和长文本的攫取，优先通过它的把关拦截。

### 1.1 分级过期策略
根据不同数据的时效性容忍度，代码制定了三根过期标尺：
- **热点及短读数据**：`5分钟` 过期 (`isCacheValid`)。例如话题排行榜，这类变动频率高的数据。
- **大盘趋势数据**：`12小时` 过期 (`isMediumTermCacheValid`)。例如 AI 构建的全局词云归纳。
- **重型长文本持久层**：`48小时` 过期 (`isLongTermCacheValid`)。例如文章详细内容、已被转换拼接完成的 Markdown 节点。

### 1.2 LRU 有限容量置换机制
为了防止 48 小时极长容忍期撑爆内存甚至撑爆仅有 5M 容量限额的 Web LocalStorage。在写入 `setArticleDetail`、`setArticleExtra` 或 `setArticleMergedContent` 时：
```typescript
if (state.articleDetails.size > 100) {
    const first = state.articleDetails.keys().next().value
    if (first !== undefined) state.articleDetails.delete(first)
}
```
通过 ES6 `Map` 天然的有序迭代特性（即先插入者的 Key 会排在迭代器头部），严格把持详情池子的最高承载水位在 `100` 篇。

### 1.3 持久化水合 (Hydration / Persistence)
- **水合 (`hydrateArticleCaches`)**：在 `cache.ts` 纯文件载入期（极早期）立即尝试反序列化 `localStorage`。如果有值，筛出那些未触及 48 小时极值的数据复原至内存响应对象 `state` 内，使得整个应用启动时即便断网也有数据基底。
- **固化 (`persistArticleCaches`)**：每次增删详情态数据，都会序列化那三个超大体积的 Map `[ID -> Item]` 推入磁盘。

## 2. 其余细核 Store

### 2.1 历史 Store (`history.ts`)
- 作为用户的隐私资产，设计了一个长达 `30天` (EXPIRY_DAYS) 的滚动抛弃列。在每次启动水合载入 `loadFromStorage` 的连带阶段调用 `cleanExpired` 进行清理。
- 负责文章细度下探反馈（`readProgress` 大小值过滤式单向推高）。

### 2.2 主题切换 Store (`theme.ts`)
- 维护了 `light`、`dark`、`system` (跟随系统) 的三态枚举。
- 利用底层 API `window.matchMedia('(prefers-color-scheme: dark)')` 与原生监听器 `addEventListener('change')` 融合。在系统级浅/深色切变时可以不刷新实时反衬到项目的 `<html class="dark">` 上。

### 2.3 状态控制 Store (`session.ts`, `toast.ts`, `ui.ts`)
- **`session.ts`**：存放用户的 Token 与基础账号字典 Profile。它的清空代表一次 `Logout`。它同时掌管着一个阅后即焚标识 `welcomeShown` 用于控制用户刚刚登录那一刻是否要弹拉新手致辞卡。
- **`toast.ts`**：全局漂浮通知生成器。通过内置的随机碰撞算法 `Date.now() + Math.random()` 计算唯一自毁散列签。控制其 2400 毫秒后自动消失。
- **`ui.ts`**：文章详情卡弹出的中心状态局。它不存放数据，它记录当前打开文章的 ID (`articleId`)。更绝妙的是，它通过 `fromContainer` 和 `fromScrollTop` 幽灵般地记录下了用户点开某篇文章那一截点的背部滚动条景深。当关闭文章时，`closeDetail` 在 `requestAnimationFrame` 里瞬时拉回这个历史坐标点，达成完全无损的列表上下文回跳。
# 模块：通用库与工具层 (Utilities & Composables)

这一层封装了脱离于具体业务组件的纯逻辑代码，是维持代码整洁度 (Clean Code) 的关键基建。

## 1. 组合式函数 (Composables / Hooks)

采用了 Vue 3 的 Composition API 思想，抽离跨组件的 UI 响应状态。

### 1.1 `useInfiniteScroll`
实现信息流分页的核心。
- 接收一个加载回调函数 `onLoadMore` 与一个滚动的视口容器根节点（`root`）。
- 内部借用现代浏览器的 `IntersectionObserver` 接口监控宿主传入的探测锚点元素（`sentinel`，通常是一个位于列表最后方的高度为零或很小的 `div`）。
- 一旦 `isIntersecting` 为真（哨兵进入视线底部 `threshold: 0.1` 阈值内），即代表用户滑动到底，自动向上级派发数据请求。并随组件卸载阶段 `onUnmounted` 自主断开观察 (`disconnect`) 防泄漏。

### 1.2 `useDevice`
处理响应式断点在非 CSS 层面的计算（JS 侧设备的断言处理）。
- 以经典 `MD` 节点 `768px` 作为切割线。
- 组件 `onMounted` 时在 `window` 绑上缩放监听。如果宽度低于 768，抛出响应式的布尔值 `isMobile = true`，用于控制底层由桌面网格平滑降级至移动端侧滑抽屉或覆盖层（如 `ArticleDetailPane` 中对侧边宽度的释放）。

## 2. 纯净函数库 (Utilities)

### 2.1 编排与解析 (`text.ts` & `markdown.ts`)
虽然主要逻辑外发给 `markdown-it` 处理。在此附带的：
- **`formatOverview(text)`**：处理 AI 返回的长字串，对可能干扰 DOM 的危险符 `<` `>` `&` 做原生防范转义。
- **动态抽取结构 (`extractHeadings`)**：用正则抓取带 `#` 的层级结构，转为树状大纲返回，作为 AI 侧边附加栏内的交互依据。

### 2.2 防护型 URL 重写中枢 (`url-rewrites.ts`)
网络防崩溃核心。
针对已知含有防盗链报错（例如：少数派 `sspai.com`）的图片资源服务器：
- 注册映射字典表 `UrlRewriteRule`。
- 在页面渲染含有 `img` 来源时预先包裹该函数：`rewriteUrl("https://cdnfile.sspai.com/x.jpg")` -> `https://img.430503.xyz/x.jpg`。
这让不可靠的源图在代理服务器前沿得以重定向而不再引发裂图。

### 2.3 `time.ts` (基于时间语义的人性化呈现)
没有引入庞大体积的第三方日期库 (如 moment.js)，手写了精简的差值刻度比较器 `formatRelativeTime`：
与当前系统时钟进行对比，落进范围即反馈 “刚刚”、“X分钟前”、“X天前”。如果在更早，则直接只取 `toISOString()` 的日期截断部分 `YYYY-MM-DD` 作为底线显示。
