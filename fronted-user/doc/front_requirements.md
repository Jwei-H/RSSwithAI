# RSSwithAI 前台（Front）需求文档

## 文档说明

- 本文档面向前端开发人员，描述 RSSwithAI **前台用户端**的页面结构、交互流程与展示信息。
- 前端开发人员应 **仅结合本文档与** [doc/front_api_reference.md](front_api_reference.md) 进行开发。
- 本文档不描述技术选型与实现细节；UI 风格追求简洁、卡片化布局。
- 特别约束：文章 AI 增强信息中的 **tags 前端暂不展示**。

---

## 名词与范围

- **RSS 源（Source）**：可订阅的信息源，包含 name/icon/category。
- **主题（Topic）**：用户输入的一句话主题（≤30字），用于语义订阅。
- **订阅（Subscription）**：用户订阅 RSS 源或 Topic。
- **时间线（Feed）**：用户订阅的 RSS 文章 + Topic 语义匹配文章的混合流。
- **文章（Article）**：RSS 抓取的文章，正文为 Markdown。
- **AI 增强信息（Article Extra）**：概览 + 关键信息（不展示 tags）。
- **词云（Word Cloud）**：与当前订阅相关的关键词云。
- **热点事件（Hot Events）**：全网热点榜单（score 1~10），用户可一键订阅为 Topic。

---

## 全局交互规范

### 1) 登录态与鉴权

- 未登录用户仅可访问：注册、登录。
- 登录成功后，前台需保存 JWT Token，并在后续请求中携带：`Authorization: Bearer <token>`。
- 当接口返回 401：
  - 优先尝试 `POST /api/refresh` 刷新 token（成功后重放原请求一次）。
  - 若刷新失败或再次 401，则跳转到登录页。

### 2) 时间显示

- 所有列表中的 `pubDate` 需要以“**xx时间前**”形式展示（如“5分钟前 / 2小时前 / 3天前 / 2025-12-01”）。

### 3) 分页形式统一为「无限滚动」

- 文档与 API 中出现的 `page/size` 或 `cursor/size`，前端交互统一为：滚动到底自动加载下一批。
- 需具备：加载中骨架屏 / 失败重试 / 到底提示。

### 4) 空状态与异常提示

- 数据为空时显示明确空状态（例如：未订阅、无搜索结果、无收藏、词云暂无数据）。
- 网络/服务错误使用轻量提示（toast/inline），避免阻塞。

### 5) Markdown 渲染

- 文章正文 `content` 为 Markdown，需要可读的渲染效果（标题、列表、引用、代码块、图片）。
- 文章外链 `link` 需支持“打开原文”（新窗口）。

---

## 信息架构与整体布局

### 1) 侧边栏四个 Tab

侧边栏固定显示 4 个入口，对应 4 个页面：

1. **订阅**（默认进入）
2. **频道广场**
3. **收藏**
4. **个人中心**

> 侧边栏布局与交互可参考示意图（doc/assets 下图片）。

### 1.1) 与参考图对齐的左侧“双层导航”

为贴近参考图的交互习惯，建议将左侧导航拆成两层：

- **最左侧：图标栏（Icon Rail）**：仅图标，用于在「订阅 / 频道广场 / 收藏 / 个人中心」四个页面间快速切换；可额外包含“新增/导入”等快捷入口（如 +）。
- **第二列：页面侧栏（Sidebar）**：展示当前 Tab 下的具体列表/操作（如订阅列表、分类筛选等）。

> 图标栏与页面侧栏是否分两层由前端决定，但交互目标是：在窄屏时也能快速切换页面、并保持列表操作集中在第二列。

### 2) 三列主布局（推荐）

在“订阅/收藏”这类时间线页面，采用简洁的三列布局：

- **左列：侧边栏**（Tab + 与当前 Tab 相关的列表/操作）
- **中列：列表**（时间线/搜索结果/收藏流）
- **右列：信息区**（顶部小词云 + 下方文章 AI 预览区）

其中：
- 词云区域为固定小模块。
- 鼠标 hover 列表中的文章卡片时，右侧展示该文章 AI 预览信息。

### 2.1) 列表顶部工具条（与参考图对齐）

在「订阅时间线 / 收藏时间线 / 搜索结果」的列表区顶部，建议有固定工具条：

- **搜索框**：固定在顶部（见各页面的搜索范围定义）。
- **筛选开关（可选/预留）**：如“仅看未读”。

说明：当前前台 API 未提供未读状态相关字段，因此“仅看未读”属于 UI 预留控件，默认可隐藏或置灰不生效。

---

## 页面需求

## A. 认证

### A1. 注册页

- 表单字段：用户名、密码（可选：确认密码）。
- 点击“注册”调用 `POST /api/register`。
- 注册成功：
  - 可直接跳转登录页并自动填充用户名；或提示“注册成功，请登录”。
- 注册失败：展示接口返回的错误信息（如用户名重复、IP 注册超限）。

### A2. 登录页

- 表单字段：用户名、密码。
- 点击“登录”调用 `POST /api/login`。
- 登录成功：保存 token，进入“订阅”页。
- 登录失败：提示“用户名或密码错误”。

---

## B. 订阅页（核心）

### B1. 页面目标

- 管理订阅（RSS/Topic）。
- 浏览混合时间线（聚合或按单订阅过滤）。
- 快速预览文章 AI 增强信息。
- 进入文章详情阅读与收藏。

### B2. 左侧：订阅列表区

#### 订阅列表加载

- 调用 `GET /api/front/v1/subscriptions`。

#### 列表展示规则

每个订阅项展示：
- RSS 订阅：icon + name + category（可用小标签）。
- Topic 订阅：显示 `content`（可用“主题”标签）。
- 每个订阅项支持“取消订阅”。

与参考图对齐的补充：

- 列表顶部建议提供 **“全部订阅”** 入口（高亮按钮形态），便于回到聚合流。

#### 默认项：全部订阅

- 列表顶部固定一个“全部订阅”项。
- 选中后时间线为聚合流（不传 `subscriptionId`）。

#### 创建 Topic（并可订阅）

- 提供“新建主题”入口（输入 ≤30 字）。
- 创建主题调用 `POST /api/front/v1/topics`。
- 创建后可立即订阅（调用 `POST /api/front/v1/subscriptions`，type=TOPIC）。

#### 取消订阅

- 调用 `DELETE /api/front/v1/subscriptions/{id}`。
- 取消后：
  - 若当前正在查看该订阅的时间线，则自动切回“全部订阅”。
  - 右侧词云与中间时间线同步刷新。

### B3. 中间：时间线列表

#### 数据来源

- 调用 `GET /api/front/v1/articles/feed`（cursor 无限滚动）。
- 选中某个订阅后：携带 `subscriptionId`。

#### 单条文章卡片信息（必须包含）

每个列表元素展示：
- `title`（标题）
- `sourceName`（来源名称）
- 发布时间：`pubDate` → “xx时间前”
- `wordCount`（字数）
- `coverImage`（封面，如有；无则不占位或使用默认占位）

与参考图对齐的卡片排版建议：

- 左侧为封面缩略图（如有），右侧为文本信息。
- 右上角显示“xx时间前”，右下角显示“xxxx字”。
- 标题下方可展示一行更浅色的副标题/摘要（**可选**）：当列表数据未提供摘要时不展示。

#### 卡片交互

- hover：右侧“AI 预览区”展示信息（见 B5）。如果未hover，该区域留空。
- 点击：进入“文章详情模式”（不跳转到其他页面，见 E）。
- 可选快捷操作：收藏/取消收藏（调用收藏接口，见 D）。

### B4. 右侧：词云（固定小模块）

#### 目标与联动规则

- 右侧固定一个小区域展示词云。
- **与当前订阅绑定**：当用户切换订阅（或切回全部订阅）时：
  - 时间线内容变化。
  - 词云内容也必须变化。

#### 数据来源

- 聚合词云：`GET /api/front/v1/trends/wordcloud`（不传 `sourceId`）。
- RSS 订阅词云：`GET /api/front/v1/trends/wordcloud?sourceId={sourceId}`。
- Topic 订阅的词云：由于接口按 source 聚合，默认使用“聚合词云”（不传 `sourceId`）。

#### 展示

- 以关键词 + 权重形式可视化（简洁即可）。
- 无数据时显示“暂无词云”。

### B5. 右侧：文章 AI 预览区（hover 触发）

#### 数据来源

- hover 某篇文章时调用：`GET /api/front/v1/articles/{id}/extra`。

#### 展示内容

- `overview`：作为主要摘要内容展示。
- `keyInformation`：以列表形式展示 1~3 条。
- `tags`：**不展示**（即使接口返回）。

与参考图对齐的模块标题建议：

- 模块标题可命名为“**精华速览**”（对应 overview）。
- 关键信息区标题为“**关键信息**”。

如未hover，该区域隐藏
#### 异常处理

- 若接口 404（无增强信息或处理失败）：显示“AI 增强信息暂不可用”。

---

## C. 频道广场页

### C1. 页面目标

- 发现可订阅的 RSS 源。
- 查看热点事件 Top10，并可一键订阅。
- 支持搜索（全站范围）。

### C2. 顶部搜索（全站）

- 搜索框固定在页面顶部。
- 输入关键字后调用：`GET /api/front/v1/articles/search?query=...&searchScope=ALL`。
- 展示方式：以文章卡片列表展示搜索结果（样式与时间线一致），支持点击进入文章详情模式。

> 当搜索框有内容时，页面主体优先展示“搜索结果流”；清空搜索框后恢复广场内容。

与参考图对齐的补充：

- 搜索框建议置于页面顶部大面积区域，右侧可附“搜索”按钮。
- 搜索结果卡片样式与订阅时间线保持一致。

### C3. 热点事件（Top10）

#### 数据来源

- 调用 `GET /api/front/v1/trends/hotevents`，前端仅展示 score 倒排前 10 条。

#### 展示字段

- 序号+`event`（事件标题）

#### 交互：订阅热点事件

- 点击“订阅”后，将热点事件转为 Topic 订阅：
  1) `POST /api/front/v1/topics`，content=event
  2) `POST /api/front/v1/subscriptions`，type=TOPIC，targetId=topicId
- 成功后：
  - 在订阅列表中可见该 Topic。
  - 提示订阅成功。
> 不要每个event后面都跟一个订阅按钮，鼠标hover某条时，右侧浮现订阅按钮
### C4. RSS 源列表（可订阅源）

#### 数据来源

- 调用 `GET /api/front/v1/rss-sources`（分页但交互为无限滚动）。
- 支持 category 筛选（可用下拉/标签）。

与参考图对齐的分类栏：

- 在页面顶部提供一个横向分类选择条（可左右滚动），默认选中“全部”。
- 分类值以接口支持的 `SourceCategory` 为准；如需更多细分类展示，可作为前端展示层的归类（不影响请求参数）。

#### 每个 RSS 源卡片信息（必须包含）

- `icon`（如有）
- `name`
- `category`
- 订阅状态：`isSubscribed`
- 若已订阅需持有 `subscriptionId`（用于取消订阅）

与参考图对齐的布局建议：

- RSS 源以“卡片栅格（多列）”方式展示。
- 卡片底部右侧提供“订阅/已订阅”按钮。

#### 卡片附加信息：最新 3 篇文章

- 每个 RSS 源卡片下方展示该源最新 3 篇文章：标题 + “xx时间前”。
- 数据来源：`GET /api/front/v1/articles/source/{sourceId}?page=0&size=3`。

与参考图对齐的补充：

- 这 3 条文章可采用“左文本 + 右小缩略图（如有）”的紧凑样式。

#### 卡片操作

- **订阅/取消订阅**：
  - 订阅：`POST /api/front/v1/subscriptions`（type=RSS, targetId=sourceId）
  - 取消：`DELETE /api/front/v1/subscriptions/{subscriptionId}`
- **预览**：打开“源文章预览弹窗”（见 C5）。

### C5. 源文章预览弹窗

#### 目标

- 在不离开频道广场的情况下，分页/滚动浏览该源文章列表。
- 点击某条文章进入完整阅读体验。

#### 内容

- 弹窗顶部显示 RSS 源信息（name/icon/category）。
- 弹窗主体为文章列表（无限滚动）：数据来自 `GET /api/front/v1/articles/source/{sourceId}`（page/size）。

#### 交互

- 点击列表中的文章：进入“文章详情模式”（见 E）。
- 从文章详情返回时：回到弹窗，并保持弹窗内滚动位置。

---

## D. 收藏页

### D1. 页面目标

- 以时间线形式查看已收藏文章。
- 支持搜索（仅收藏范围）。

### D2. 收藏列表

- 数据来源：`GET /api/front/v1/articles/favorites`（分页但交互为无限滚动）。
- 单条卡片展示字段与订阅时间线一致：title / sourceName / xx时间前 / wordCount / coverImage。
- 点击文章进入“文章详情模式”（见 E）。

### D3. 收藏搜索

- 顶部固定搜索框。
- 调用：`GET /api/front/v1/articles/search?query=...&searchScope=FAVORITE`。
- 展示为文章卡片列表，点击进入文章详情模式。

---

## E. 文章详情模式（通用，不跳转页面）

### E1. 进入方式

- 在时间线/搜索结果/收藏/源预览弹窗中点击文章卡片进入。

### E2. 交互原则（关键）

- 进入文章详情时：
  - **不跳转到其他页面**。
  - 左侧侧边栏折叠（或完全隐藏）。
  - 原列表（时间线/收藏/搜索结果/弹窗列表）隐藏。
  - 内容区展示文章详情。
- 左上角提供“返回”按钮：
  - 返回后必须回到进入前的列表状态，并恢复原来的滚动位置。

### E3. 详情布局（参考示例图）

- 左右两列布局，中间分隔线支持拖动调整宽度。
- 左列：文章正文；右列：AI 信息。
- 左右两列都允许各自滚动。

与参考图对齐的右列信息结构建议：

- 顶部为“精华速览”卡片（overview）。
- 中部为“关键信息”卡片（keyInformation）。
- 底部为“全文拆解/目录”区域（可选）：展示文章的章节目录，便于快速跳转。

说明：接口未提供“全文拆解/目录”的独立数据时，该区域仅作为阅读辅助目录（按正文结构呈现即可）。

### E4. 左列：文章详情信息（必须包含）

数据来源：`GET /api/front/v1/articles/{id}`

展示字段：
- `title`
- `sourceName`
- 发布时间：`pubDate`（同时可显示具体时间）
- `author`（若为空可隐藏）
- `categories`（若为空可隐藏或以标签展示）
- `wordCount`
- `coverImage`（如有）
- `content`（Markdown 渲染）
- 原文入口：`link`（按钮“打开原文”）

收藏操作：
- 收藏：`POST /api/front/v1/articles/{id}/favorite`
- 取消收藏：`DELETE /api/front/v1/articles/{id}/favorite`

### E5. 右列：AI 增强信息

数据来源：`GET /api/front/v1/articles/{id}/extra`

展示内容：
- `overview`
- `keyInformation` 列表
- `tags`：**不展示**

无 AI 信息时：显示占位文案“AI 增强信息暂不可用”。

### E6. 推荐文章

- 位于右列 AI 信息的底部。
- 数据来源：`GET /api/front/v1/articles/{id}/recommendations`（最多 2 条）。
- 展示方式：小卡片列表（title / sourceName / xx时间前）。
- 点击推荐文章：在详情模式中切换到对应文章（视为一次新的详情打开），并支持返回到上一层详情或直接返回列表（实现方式由前端自行选择，但用户体验需明确）。

---

## F. 个人中心页

### F1. 页面目标

- 查看个人信息。
- 修改用户名、修改密码。
- 退出登录。

### F2. 信息展示

- 调用 `GET /api/user/profile`。
- 展示：头像 `avatarUrl`、用户名 `username`。

### F3. 修改用户名

- 调用 `PUT /api/user/username`。
- 成功后刷新个人信息。

### F4. 修改密码

- 调用 `PUT /api/user/password`。
- 成功后提示“密码已更新”。

### F5. 退出登录

- 清理本地 token，回到登录页。

---

## 接口映射速查（按功能）

- 注册：`POST /api/register`
- 登录：`POST /api/login`
- 刷新 Token：`POST /api/refresh`
- 个人信息：`GET /api/user/profile`
- 修改用户名：`PUT /api/user/username`
- 修改密码：`PUT /api/user/password`

- RSS 源列表：`GET /api/front/v1/rss-sources`
- 创建 Topic：`POST /api/front/v1/topics`
- 创建订阅：`POST /api/front/v1/subscriptions`
- 取消订阅：`DELETE /api/front/v1/subscriptions/{id}`
- 订阅列表：`GET /api/front/v1/subscriptions`

- 时间线：`GET /api/front/v1/articles/feed`
- 源文章预览：`GET /api/front/v1/articles/source/{sourceId}`
- 文章详情：`GET /api/front/v1/articles/{id}`
- AI 信息：`GET /api/front/v1/articles/{id}/extra`
- 搜索：`GET /api/front/v1/articles/search`
- 推荐：`GET /api/front/v1/articles/{id}/recommendations`
- 收藏：`POST /api/front/v1/articles/{id}/favorite`
- 取消收藏：`DELETE /api/front/v1/articles/{id}/favorite`
- 收藏列表：`GET /api/front/v1/articles/favorites`

- 词云：`GET /api/front/v1/trends/wordcloud`
- 热点事件：`GET /api/front/v1/trends/hotevents`