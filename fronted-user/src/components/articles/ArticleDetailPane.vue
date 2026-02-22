<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { feedApi } from '../../services/frontApi'
import { formatRelativeTime } from '../../utils/time'
import { extractHeadings, renderMarkdown } from '../../utils/markdown'
import { formatOverview } from '../../utils/text'
import type { ArticleDetail, ArticleExtra, ArticleFeed } from '../../types'
import { useToastStore } from '../../stores/toast'
import { useHistoryStore } from '../../stores/history'
import { useCacheStore } from '../../stores/cache'
import { FileText, ListChecks, ListTree, ThumbsUp } from 'lucide-vue-next'
let mermaidModule: typeof import('mermaid') | null = null

const props = defineProps<{
  articleId: number | null
  onClose: () => void
  onOpenArticle: (id: number) => void
}>()

const toast = useToastStore()
const historyStore = useHistoryStore()
const cache = useCacheStore()

const article = ref<ArticleDetail | null>(null)
const extra = ref<ArticleExtra | null>(null)
const mergedContent = ref('')
const recommendations = ref<ArticleFeed[]>([])
const loading = ref(true)
const extraError = ref<string | null>(null)
const favorite = ref(false)

const leftPaneRef = ref<HTMLElement | null>(null)
const mobileScrollRef = ref<HTMLElement | null>(null)
const collapsedHeadings = ref<Set<string>>(new Set())

const leftWidth = ref(60)
const isDragging = ref(false)
const activeHeadingId = ref<string | null>(null)
const scrollRafId = ref<number | null>(null)
const mermaidReady = ref(false)
const imagePreviewOpen = ref(false)
const imagePreviewSrc = ref('')
const imagePreviewAlt = ref('预览图片')

const dividerStyle = computed(() => ({
  width: `${100 - leftWidth.value}%`
}))

const tocItems = computed(() => {
  if (!mergedContent.value) return []
  const raw = extractHeadings(mergedContent.value).map((item) => ({
    ...item,
    text: item.text.replace(/\*\*/g, '')
  }))
  if (!raw.length) return []
  const minLevel = Math.min(...raw.map((item) => item.level))
  return raw.map((item) => ({
    ...item,
    level: item.level - minLevel + 1
  }))
})

const escapeSelector = (value: string) => {
  if (typeof CSS !== 'undefined' && typeof CSS.escape === 'function') {
    return CSS.escape(value)
  }
  return value.replace(/([ #;?%&,.+*~'":!^$\[\]()=>|\/])/g, '\\$1')
}

const getScrollContainer = () => (window.innerWidth < 768 ? mobileScrollRef.value : leftPaneRef.value)

const setupCollapseToggle = () => {
  // 获取 markdown 内容容器（优先选择可见容器）
  const markdownBodies = document.querySelectorAll<HTMLElement>('.markdown-body')
  
  // 对所有 markdown 内容应用折叠逻辑
  markdownBodies.forEach((markdownBody) => {
    // 找到所有折叠按钮
    const toggleButtons = markdownBody.querySelectorAll<HTMLButtonElement>('.md-heading-toggle')

    const toggleHeading = (heading: HTMLElement, btn: HTMLButtonElement) => {
      const contentWrapper = getContentBetweenHeadings(heading, markdownBody)
      const isExpanded = btn.getAttribute('aria-expanded') === 'true'
      btn.setAttribute('aria-expanded', isExpanded ? 'false' : 'true')

      if (contentWrapper) {
        contentWrapper.classList.toggle('collapsed', isExpanded)
      }

      if (isExpanded) {
        collapsedHeadings.value.add(heading.id)
      } else {
        collapsedHeadings.value.delete(heading.id)
      }
    }

    toggleButtons.forEach((btn) => {
      if (btn.dataset.setupDone === 'true') return
      btn.dataset.setupDone = 'true'

      btn.addEventListener('click', (e) => {
        e.preventDefault()
        e.stopPropagation()

        const headingId = btn.getAttribute('data-toggle-id')
        if (!headingId) return

        const heading = markdownBody.querySelector<HTMLElement>(`#${escapeSelector(headingId)}`)
        if (!heading) return

        toggleHeading(heading, btn)
      })
    })

    const headings = markdownBody.querySelectorAll<HTMLElement>('h1, h2, h3, h4, h5, h6')
    headings.forEach((heading) => {
      if (heading.dataset.collapseSetup === 'true') return
      const btn = heading.querySelector<HTMLButtonElement>('.md-heading-toggle')
      if (!btn) return
      heading.dataset.collapseSetup = 'true'

      heading.addEventListener('click', (e) => {
        const target = e.target as HTMLElement
        if (target.closest('.md-heading-toggle')) return
        toggleHeading(heading, btn)
      })
    })
  })
}

const loadMermaid = async () => {
  if (!mermaidModule) {
    const mod = await import('mermaid')
    mermaidModule = mod
  }
  return mermaidModule.default
}

const renderMermaidDiagrams = async () => {
  const containers = Array.from(document.querySelectorAll<HTMLElement>('.markdown-body .mermaid'))
  if (!containers.length) return

  const mermaid = await loadMermaid()
  const isDark = document.documentElement.classList.contains('dark')
  if (!mermaidReady.value) {
    mermaid.initialize({ startOnLoad: false, theme: isDark ? 'dark' : 'default' })
    mermaidReady.value = true
  } else {
    mermaid.initialize({ startOnLoad: false, theme: isDark ? 'dark' : 'default' })
  }

  containers.forEach((node) => node.removeAttribute('data-processed'))
  await mermaid.run({ nodes: containers })
}

const getContentBetweenHeadings = (startHeading: HTMLElement, container: HTMLElement): HTMLElement | null => {
  // 创建一个包装容器来包含这个标题和它的内容
  // 找到下一个同级或更高级的标题
  const currentLevel = parseInt(startHeading.tagName.charAt(1))
  let currentElement = startHeading.nextElementSibling as HTMLElement | null
  const contentElements: Element[] = []
  
  while (currentElement && container.contains(currentElement)) {
    // 如果遇到标题
    if (currentElement.tagName.match(/^H[1-6]$/)) {
      const nextLevel = parseInt(currentElement.tagName.charAt(1))
      // 如果下一个标题级别小于等于当前级别，停止
      if (nextLevel <= currentLevel) {
        break
      }
    }
    contentElements.push(currentElement)
    currentElement = currentElement.nextElementSibling as HTMLElement | null
  }
  
  // 如果没有内容元素，返回 null
  if (contentElements.length === 0) return null
  
  // 检查是否已经有包装容器
  const firstElement = contentElements[0]
  if (firstElement && firstElement.classList?.contains('md-heading-content')) {
    return firstElement as HTMLElement
  }
  
  // 创建包装容器
  const wrapper = document.createElement('div')
  wrapper.className = 'md-heading-content'
  
  // 将所有内容元素移入包装容器
  contentElements.forEach((el) => {
    wrapper.appendChild(el)
  })
  
  // 将包装容器插入到标题后面
  startHeading.after(wrapper)
  
  return wrapper
}

const getHeadingElements = () => {
  const container = getScrollContainer()
  if (!container) return []
  return Array.from(container.querySelectorAll<HTMLElement>('h1, h2, h3, h4, h5, h6')).filter(
    (el) => !!el.id
  )
}

const updateActiveHeading = () => {
  const container = getScrollContainer()
  if (!container) return
  const headings = getHeadingElements()
  if (!headings.length) {
    activeHeadingId.value = null
    return
  }
  const marker = container.scrollTop + container.clientHeight * 0.25
  let current = headings[0]!
  for (const heading of headings) {
    if (heading.offsetTop <= marker) {
      current = heading
    } else {
      break
    }
  }
  activeHeadingId.value = current.id
}


const onLeftPaneScroll = () => {
  if (scrollRafId.value !== null) return
  scrollRafId.value = window.requestAnimationFrame(() => {
    scrollRafId.value = null
    updateActiveHeading()
    updateReadingProgress()
  })
}

// 移动端触摸结束时更新进度
const onMobileTouchEnd = () => {
  // 延迟执行以等待滚动惯性结束
  setTimeout(() => {
    updateReadingProgress()
  }, 150)
}

const updateReadingProgress = () => {
  // 根据当前平台选择正确的容器
  const isMobile = window.innerWidth < 768
  const container = isMobile ? mobileScrollRef.value : leftPaneRef.value

  if (!container || !props.articleId) return

  const scrollTop = container.scrollTop
  const clientHeight = container.clientHeight
  const scrollHeight = container.scrollHeight
  const maxScroll = scrollHeight - clientHeight

  // 调试日志（可在生产环境移除）
  // console.log('Progress update:', { scrollTop, clientHeight, scrollHeight, maxScroll, isMobile })

  // 边界情况：内容不需要滚动（短文章），直接视为 100%
  if (maxScroll <= 10) {
    // 确保内容已渲染（scrollHeight 大于一个最小值）
    if (scrollHeight > 100) {
      historyStore.updateProgress(props.articleId, 1)
    }
    return
  }

  // 正常计算进度
  const progress = Math.min(1, Math.max(0, scrollTop / maxScroll))
  historyStore.updateProgress(props.articleId, progress)
}

const scrollToHeading = (id: string) => {
  const container = getScrollContainer()
  if (!container) return
  const target = container.querySelector<HTMLElement>(`#${escapeSelector(id)}`)
  if (!target) return
  const offset = Math.round(container.clientHeight * 0.25)
  container.scrollTo({
    top: Math.max(0, target.offsetTop - offset),
    behavior: 'smooth'
  })
  activeHeadingId.value = id
}

const normalizeHeadingTitle = (title: string) => {
  const trimmed = title.trim()
  if (!trimmed) return ''
  return /^#{2,6}\s/.test(trimmed) ? trimmed : `## ${trimmed.replace(/^#+\s*/, '')}`
}

const mergeAiTocIntoContent = (content: string, toc?: ArticleExtra['toc']) => {
  if (!content || !toc?.length) return content
  let merged = content
  for (const item of toc) {
    const title = normalizeHeadingTitle(item.title || '')
    const anchor = (item.anchor || '').trim()
    if (!title || !anchor) continue

    const anchorIndex = merged.indexOf(anchor)
    if (anchorIndex < 0) continue

    const lineStart = merged.lastIndexOf('\n', anchorIndex)
    const insertPos = lineStart < 0 ? 0 : lineStart + 1
    const before = merged.slice(0, insertPos)
    const after = merged.slice(insertPos)
    merged = `${before.trimEnd()}\n\n${title}\n\n${after.trimStart()}`
  }
  return merged
}

const rebuildMergedContent = () => {
  if (!article.value?.content || !props.articleId) {
    mergedContent.value = ''
    return
  }
  const content = mergeAiTocIntoContent(article.value.content, extra.value?.toc)
  mergedContent.value = content
  cache.setArticleMergedContent(props.articleId, content)
}

const toArticleFeed = (item: ArticleDetail): ArticleFeed => ({
  id: item.id,
  sourceId: item.sourceId,
  sourceName: item.sourceName,
  title: item.title,
  link: item.link,
  coverImage: item.coverImage,
  pubDate: item.pubDate,
  wordCount: item.wordCount
})

const openImagePreview = (src: string, alt?: string) => {
  if (!src) return
  imagePreviewSrc.value = src
  imagePreviewAlt.value = alt || '预览图片'
  imagePreviewOpen.value = true

  if (typeof document !== 'undefined') {
    document.body.style.overflow = 'hidden'
    document.body.style.touchAction = 'none'
  }
}

const closeImagePreview = () => {
  imagePreviewOpen.value = false

  if (typeof document !== 'undefined') {
    document.body.style.overflow = ''
    document.body.style.touchAction = ''
  }
}

const onMarkdownClick = (event: MouseEvent) => {
  const target = event.target as HTMLElement | null
  const image = target?.closest('.markdown-body img') as HTMLImageElement | null
  if (!image) return

  event.preventDefault()
  const src = image.currentSrc || image.src
  openImagePreview(src, image.alt)
}

const onKeyDown = (event: KeyboardEvent) => {
  if (event.key === 'Escape' && imagePreviewOpen.value) {
    closeImagePreview()
  }
}

const load = async () => {
  if (!props.articleId) return
  loading.value = true
  extraError.value = null
  favorite.value = false
  recommendations.value = []

  const articleId = props.articleId
  const cachedArticle = cache.getArticleDetail(articleId)
  const cachedExtra = cache.getArticleExtra(articleId)
  const cachedMergedContent = cache.getArticleMergedContent(articleId)

  article.value = cachedArticle
  extra.value = cachedExtra
  favorite.value = cachedArticle?.isFavorite ?? false
  mergedContent.value = cachedMergedContent || cachedArticle?.content || ''

  const detailPromise = (async () => {
    try {
      const data = await feedApi.detail(articleId)
      article.value = data
      favorite.value = data?.isFavorite ?? false
      cache.setArticleDetail(articleId, data)
      rebuildMergedContent()

      historyStore.addReading({
        articleId: data.id,
        title: data.title,
        sourceName: data.sourceName,
        coverImage: data.coverImage,
        pubDate: data.pubDate
      })
    } catch {
      if (!article.value) {
        toast.push('文章加载失败，请稍后重试', 'error')
      }
    } finally {
      loading.value = false
      setTimeout(() => {
        updateReadingProgress()
      }, 300)
    }
  })()

  const extraPromise = (async () => {
    if (cachedExtra) {
      rebuildMergedContent()
      return
    }

    try {
      const data = await feedApi.extra(articleId)
      extra.value = data
      if (data && data.status === 'SUCCESS') {
        cache.setArticleExtra(articleId, data)
      }
    } catch {
      extra.value = null
      extraError.value = 'AI 增强信息暂不可用'
    } finally {
      rebuildMergedContent()
    }
  })()

  const recommendationPromise = (async () => {
    try {
      recommendations.value = await feedApi.recommendations(articleId)
    } catch {
      recommendations.value = []
    }
  })()

  await Promise.allSettled([detailPromise, extraPromise, recommendationPromise])
}

const toggleFavorite = async () => {
  if (!article.value) return
  try {
    if (favorite.value) {
      await feedApi.unfavorite(article.value.id)
      favorite.value = false
      article.value.isFavorite = false
      cache.removeFavorite(article.value.id)
      cache.setArticleDetail(article.value.id, article.value)
      toast.push('已取消收藏', 'success')
    } else {
      await feedApi.favorite(article.value.id)
      favorite.value = true
      article.value.isFavorite = true
      cache.upsertFavorite(toArticleFeed(article.value))
      cache.setArticleDetail(article.value.id, article.value)
      toast.push('已加入收藏', 'success')
    }
  } catch (error) {
    console.error('收藏操作错误:', error)
    toast.push('收藏操作失败', 'error')
  }
}

const onMouseMove = (event: MouseEvent) => {
  if (!isDragging.value) return
  const total = window.innerWidth
  const percentage = Math.min(72, Math.max(35, (event.clientX / total) * 100))
  leftWidth.value = percentage
}

const onMouseUp = () => {
  isDragging.value = false
}

watch(
  () => props.articleId,
  () => {
    load()
  },
  { immediate: true }
)

watch(
  () => mergedContent.value,
  async () => {
    collapsedHeadings.value.clear()
    await nextTick()
    updateActiveHeading()
    setupCollapseToggle()
    await renderMermaidDiagrams()
  }
)

onMounted(() => {
  window.addEventListener('mousemove', onMouseMove)
  window.addEventListener('mouseup', onMouseUp)
  window.addEventListener('keydown', onKeyDown)
})

onUnmounted(() => {
  closeImagePreview()

  if (scrollRafId.value !== null) {
    window.cancelAnimationFrame(scrollRafId.value)
  }
  window.removeEventListener('mousemove', onMouseMove)
  window.removeEventListener('mouseup', onMouseUp)
  window.removeEventListener('keydown', onKeyDown)
})
</script>

<template>
  <div class="flex h-full flex-col">
    <header class="flex items-center justify-between border-b border-border px-4 py-2 md:px-6">
      <button class="rounded-lg border border-border px-2 py-1 text-sm text-muted-foreground hover:bg-muted"
        @click="onClose">
        返回
      </button>
      <div class="flex items-center gap-3">
        <button
          class="flex items-center gap-1.5 rounded-lg border border-border px-3 py-1 text-xs text-muted-foreground transition-colors hover:bg-muted"
          @click="toggleFavorite" :title="favorite ? '取消收藏' : '收藏'">
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" :fill="favorite ? 'currentColor' : 'none'"
            stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"
            class="h-4 w-4 transition-colors" :class="favorite ? 'fill-yellow-500 stroke-yellow-500' : ''">
            <polygon
              points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2" />
          </svg>
          {{ favorite ? '已收藏' : '收藏' }}
        </button>
      </div>
    </header>

    <!-- 桌面端：双栏分割布局 -->
    <div class="hidden flex-1 overflow-hidden md:flex">
      <section class="h-full overflow-y-auto border-r border-border px-2 py-6 scrollbar-thin"
        :style="{ width: `${leftWidth}%` }" ref="leftPaneRef" @scroll.passive="onLeftPaneScroll">
        <div v-if="!article && loading" class="text-sm text-muted-foreground">加载中...</div>
        <div v-else-if="article" class="space-y-4">
          <div>
            <h1 class="text-2xl font-semibold text-foreground">{{ article.title }}</h1>
            <div class="mt-2 flex flex-wrap gap-3 text-sm text-muted-foreground">
              <span>{{ article.sourceName }}</span>
              <span>{{ formatRelativeTime(article.pubDate) }}</span>
              <span v-if="article.author">作者：{{ article.author }}</span>
              <span v-if="article.wordCount">{{ article.wordCount }} 字</span>
            </div>
            <a v-if="article.link" :href="article.link" target="_blank"
              class="mt-2 inline-flex text-sm text-primary underline-offset-4 hover:underline">
              打开原文
            </a>
          </div>
          <div v-if="mergedContent" class="markdown-body" v-html="renderMarkdown(mergedContent)" @click="onMarkdownClick" />
          <p v-else class="text-sm text-muted-foreground">正文加载中...</p>
        </div>
      </section>

      <div class="w-1 cursor-col-resize bg-border" @mousedown="() => (isDragging = true)" />

      <section class="h-full flex-1 overflow-y-auto px-8 pb-6 pt-2 scrollbar-thin" :style="dividerStyle">
        <div class="space-y-2">
          <div class="rounded-2xl border border-border bg-card p-4">
            <div class="flex items-center gap-2">
              <FileText class="h-4 w-4 text-primary" />
              <h3 class="text-sm font-semibold text-foreground">精华速览</h3>
            </div>
            <p v-if="extra" class="mt-3 text-[15px] leading-7 text-muted-foreground"
              v-html="formatOverview(extra.overview)" />
            <p v-else class="mt-2 text-sm text-muted-foreground">{{ extraError || '暂无内容' }}</p>
          </div>
          <div class="rounded-2xl border border-border bg-card p-4">
            <div class="flex items-center gap-2">
              <ListChecks class="h-4 w-4 text-primary" />
              <h3 class="text-sm font-semibold text-foreground">关键信息</h3>
            </div>
            <ul v-if="extra?.keyInformation?.length"
              class="mt-3 list-decimal pl-4 text-[15px] leading-7 text-muted-foreground">
              <li v-for="(item, index) in extra.keyInformation" :key="`${item}-${index}`">
                {{ item }}
              </li>
            </ul>
            <p v-else class="mt-2 text-sm text-muted-foreground">暂无关键信息</p>
          </div>
          <div v-if="tocItems.length" class="rounded-2xl border border-border bg-card p-4">
            <div class="flex items-center gap-2">
              <ListTree class="h-4 w-4 text-primary" />
              <h3 class="text-sm font-semibold text-foreground">目录</h3>
            </div>
            <ul class="mt-3 space-y-2 text-sm text-muted-foreground/70">
              <li v-for="item in tocItems" :key="item.id" :style="{ paddingLeft: `${(item.level - 1) * 12}px` }">
                <button type="button"
                  class="line-clamp-1 w-full text-left underline-offset-4 hover:text-sky-600 hover:underline" :class="activeHeadingId === item.id
                    ? 'font-semibold text-sky-600'
                    : 'text-muted-foreground/70'
                    " :title="item.text" @click="scrollToHeading(item.id)">
                  {{ item.text }}
                </button>
              </li>
            </ul>
          </div>

          <div class="rounded-2xl border border-border bg-card p-4">
            <div class="flex items-center gap-2">
              <ThumbsUp class="h-4 w-4 text-primary" />
              <h3 class="text-sm font-semibold text-foreground">相似推荐</h3>
            </div>
            <div v-if="!recommendations.length" class="mt-2 text-sm text-muted-foreground">暂无推荐</div>
            <button v-for="item in recommendations" :key="item.id"
              class="mt-3 flex w-full items-start justify-between gap-3 rounded-xl border border-border px-3 py-3 text-left text-sm hover:bg-muted"
              @click="props.onOpenArticle(item.id)">
              <div class="flex-1">
                <p class="line-clamp-2 text-foreground">{{ item.title }}</p>
                <p class="mt-2 text-xs text-muted-foreground">{{ item.sourceName }}</p>
              </div>
              <span class="text-xs text-muted-foreground">{{ formatRelativeTime(item.pubDate) }}</span>
            </button>
          </div>
        </div>
      </section>
    </div>

    <!-- 移动端：单栏垂直滚动布局 -->
    <div ref="mobileScrollRef" class="flex-1 overflow-y-auto overflow-x-hidden px-4 py-4 md:hidden" @scroll.passive="onLeftPaneScroll"
      @touchend.passive="onMobileTouchEnd">
      <div v-if="!article && loading" class="text-sm text-muted-foreground">加载中...</div>
      <div v-else-if="article" class="space-y-4">
        <!-- 1. 文章元信息 -->
        <div class="rounded-2xl border border-border bg-card p-4">
          <h1 class="text-lg font-semibold text-foreground">{{ article.title }}</h1>
          <div class="mt-2 flex flex-wrap gap-2 text-xs text-muted-foreground">
            <span>{{ article.sourceName }}</span>
            <span>{{ formatRelativeTime(article.pubDate) }}</span>
            <span v-if="article.wordCount">{{ article.wordCount }} 字</span>
          </div>
          <a v-if="article.link" :href="article.link" target="_blank"
            class="mt-3 inline-flex text-sm text-primary underline-offset-4 hover:underline">
            打开原文 →
          </a>
        </div>

        <!-- 2. 精华速览 -->
        <div class="rounded-2xl border border-border bg-card p-4">
          <div class="flex items-center gap-2">
            <FileText class="h-4 w-4 text-primary" />
            <h3 class="text-sm font-semibold text-foreground">精华速览</h3>
          </div>
          <p v-if="extra" class="mt-3 text-sm leading-6 text-muted-foreground"
            v-html="formatOverview(extra.overview)" />
          <p v-else class="mt-2 text-sm text-muted-foreground">{{ extraError || '加载中...' }}</p>
        </div>

        <!-- 3. 关键信息 -->
        <div class="rounded-2xl border border-border bg-card p-4">
          <div class="flex items-center gap-2">
            <ListChecks class="h-4 w-4 text-primary" />
            <h3 class="text-sm font-semibold text-foreground">关键信息</h3>
          </div>
          <ul v-if="extra?.keyInformation?.length"
            class="mt-3 list-decimal pl-4 text-sm leading-6 text-muted-foreground">
            <li v-for="(item, index) in extra.keyInformation" :key="`mobile-key-${index}`">
              {{ item }}
            </li>
          </ul>
          <p v-else class="mt-2 text-sm text-muted-foreground">暂无关键信息</p>
        </div>

        <!-- 4. 目录 -->
        <div v-if="tocItems.length" class="rounded-2xl border border-border bg-card p-4">
          <div class="flex items-center gap-2">
            <ListTree class="h-4 w-4 text-primary" />
            <h3 class="text-sm font-semibold text-foreground">目录</h3>
          </div>
          <ul class="mt-3 space-y-2 text-sm text-muted-foreground/70">
            <li v-for="item in tocItems" :key="`mobile-toc-${item.id}`" :style="{ paddingLeft: `${(item.level - 1) * 12}px` }">
              <button type="button"
                class="line-clamp-1 w-full text-left underline-offset-4 hover:text-sky-600 hover:underline" :class="activeHeadingId === item.id
                  ? 'font-semibold text-sky-600'
                  : 'text-muted-foreground/70'
                  " :title="item.text" @click="scrollToHeading(item.id)">
                {{ item.text }}
              </button>
            </li>
          </ul>
        </div>

        <!-- 5. 文章正文 -->
        <div class="rounded-2xl border border-border bg-card p-4">
          <div v-if="mergedContent" class="markdown-body" v-html="renderMarkdown(mergedContent)" @click="onMarkdownClick" />
          <p v-else class="text-sm text-muted-foreground">正文加载中...</p>
        </div>

        <!-- 6. 相似推荐 -->
        <div class="rounded-2xl border border-border bg-card p-4">
          <div class="flex items-center gap-2">
            <ThumbsUp class="h-4 w-4 text-primary" />
            <h3 class="text-sm font-semibold text-foreground">相似推荐</h3>
          </div>
          <div v-if="!recommendations.length" class="mt-2 text-sm text-muted-foreground">暂无推荐</div>
          <button v-for="item in recommendations" :key="`mobile-rec-${item.id}`"
            class="mt-3 flex w-full items-start justify-between gap-3 rounded-xl border border-border px-3 py-3 text-left text-sm active:bg-muted"
            @click="props.onOpenArticle(item.id)">
            <div class="flex-1">
              <p class="line-clamp-2 text-foreground">{{ item.title }}</p>
              <p class="mt-1 text-xs text-muted-foreground">{{ item.sourceName }}</p>
            </div>
            <span class="text-xs text-muted-foreground">{{ formatRelativeTime(item.pubDate) }}</span>
          </button>
        </div>
      </div>
    </div>

    <Transition enter-active-class="transition duration-200 ease-out" enter-from-class="opacity-0"
      enter-to-class="opacity-100" leave-active-class="transition duration-150 ease-in" leave-from-class="opacity-100"
      leave-to-class="opacity-0">
      <div v-if="imagePreviewOpen" class="fixed inset-0 z-[80] flex items-center justify-center bg-background/70 p-4 backdrop-blur-sm"
        @click="closeImagePreview">
        <img :src="imagePreviewSrc" :alt="imagePreviewAlt"
          class="max-h-[90vh] max-w-[95vw] rounded-xl border border-border object-contain shadow-2xl transition-transform duration-200 ease-out"
          @click.stop />
      </div>
    </Transition>
  </div>
</template>