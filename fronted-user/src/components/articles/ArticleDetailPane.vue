<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { feedApi } from '../../services/frontApi'
import { formatRelativeTime } from '../../utils/time'
import { extractHeadings, renderMarkdown } from '../../utils/markdown'
import { formatOverview } from '../../utils/text'
import type { ArticleDetail, ArticleExtra, ArticleFeed } from '../../types'
import { useToastStore } from '../../stores/toast'
import { FileText, ListChecks, ListTree, ThumbsUp } from 'lucide-vue-next'

const props = defineProps<{
  articleId: number | null
  onClose: () => void
  onOpenArticle: (id: number) => void
}>()

const toast = useToastStore()

const article = ref<ArticleDetail | null>(null)
const extra = ref<ArticleExtra | null>(null)
const recommendations = ref<ArticleFeed[]>([])
const loading = ref(true)
const extraError = ref<string | null>(null)
const favorite = ref(false)

const leftPaneRef = ref<HTMLElement | null>(null)

const leftWidth = ref(60)
const isDragging = ref(false)
const activeHeadingId = ref<string | null>(null)
const scrollRafId = ref<number | null>(null)

const dividerStyle = computed(() => ({
  width: `${100 - leftWidth.value}%`
}))

const tocItems = computed(() => {
  if (!article.value?.content) return []
  const raw = extractHeadings(article.value.content).map((item) => ({
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

const getHeadingElements = () => {
  if (!leftPaneRef.value) return []
  return Array.from(leftPaneRef.value.querySelectorAll<HTMLElement>('h1, h2, h3, h4, h5, h6')).filter(
    (el) => !!el.id
  )
}

const updateActiveHeading = () => {
  if (!leftPaneRef.value) return
  const headings = getHeadingElements()
  if (!headings.length) {
    activeHeadingId.value = null
    return
  }
  const container = leftPaneRef.value
  const marker = container.scrollTop + container.clientHeight * 0.25
  let current = headings[0]
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
  })
}

const scrollToHeading = (id: string) => {
  if (!leftPaneRef.value) return
  const target = leftPaneRef.value.querySelector<HTMLElement>(`#${escapeSelector(id)}`)
  if (!target) return
  const container = leftPaneRef.value
  const offset = Math.round(container.clientHeight * 0.25)
  container.scrollTo({
    top: Math.max(0, target.offsetTop - offset),
    behavior: 'smooth'
  })
  activeHeadingId.value = id
}

const load = async () => {
  if (!props.articleId) return
  loading.value = true
  extraError.value = null
  favorite.value = false
  recommendations.value = []
  try {
    article.value = await feedApi.detail(props.articleId)
    favorite.value = article.value?.isFavorite ?? false
  } catch {
    toast.push('文章加载失败，请稍后重试', 'error')
  } finally {
    loading.value = false
  }

  try {
    extra.value = await feedApi.extra(props.articleId)
  } catch {
    extra.value = null
    extraError.value = 'AI 增强信息暂不可用'
  }

  try {
    recommendations.value = await feedApi.recommendations(props.articleId)
  } catch {
    recommendations.value = []
  }
}

const toggleFavorite = async () => {
  if (!article.value) return
  try {
    if (favorite.value) {
      await feedApi.unfavorite(article.value.id)
      favorite.value = false
      toast.push('已取消收藏', 'success')
    } else {
      await feedApi.favorite(article.value.id)
      favorite.value = true
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
  }
)

watch(
  () => article.value?.content,
  async () => {
    await nextTick()
    updateActiveHeading()
  }
)

onMounted(() => {
  load()
  window.addEventListener('mousemove', onMouseMove)
  window.addEventListener('mouseup', onMouseUp)
})

onUnmounted(() => {
  if (scrollRafId.value !== null) {
    window.cancelAnimationFrame(scrollRafId.value)
  }
  window.removeEventListener('mousemove', onMouseMove)
  window.removeEventListener('mouseup', onMouseUp)
})
</script>

<template>
  <div class="flex h-full flex-col">
    <header class="flex items-center justify-between border-b border-border px-6 py-2">
      <button
        class="rounded-lg border border-border px-0 py-1 text-sm text-muted-foreground hover:bg-muted"
        @click="onClose"
      >
        返回
      </button>
      <div class="flex items-center gap-3">
        <button
          class="flex items-center gap-1.5 rounded-lg border border-border px-3 py-1 text-xs text-muted-foreground transition-colors hover:bg-muted"
          @click="toggleFavorite"
          :title="favorite ? '取消收藏' : '收藏'"
        >
          <svg
            xmlns="http://www.w3.org/2000/svg"
            viewBox="0 0 24 24"
            :fill="favorite ? 'currentColor' : 'none'"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
            class="h-4 w-4 transition-colors"
            :class="favorite ? 'fill-yellow-500 stroke-yellow-500' : ''"
          >
            <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2" />
          </svg>
          {{ favorite ? '已收藏' : '收藏' }}
        </button>
      </div>
    </header>

    <div class="flex flex-1 overflow-hidden">
      <section
        class="h-full overflow-y-auto border-r border-border px-2 py-6 scrollbar-thin"
        :style="{ width: `${leftWidth}%` }"
        ref="leftPaneRef"
        @scroll.passive="onLeftPaneScroll"
      >
        <div v-if="loading" class="text-sm text-muted-foreground">加载中...</div>
        <div v-else-if="article" class="space-y-4">
          <div>
            <h1 class="text-2xl font-semibold text-foreground">{{ article.title }}</h1>
            <div class="mt-2 flex flex-wrap gap-3 text-sm text-muted-foreground">
              <span>{{ article.sourceName }}</span>
              <span>{{ formatRelativeTime(article.pubDate) }}</span>
              <span v-if="article.author">作者：{{ article.author }}</span>
              <span v-if="article.wordCount">{{ article.wordCount }} 字</span>
            </div>
            <a
              v-if="article.link"
              :href="article.link"
              target="_blank"
              class="mt-2 inline-flex text-sm text-primary underline-offset-4 hover:underline"
            >
              打开原文
            </a>
          </div>

          <div class="markdown-body" v-html="renderMarkdown(article.content)" />
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
            <p
              v-if="extra"
              class="mt-3 text-[15px] leading-7 text-muted-foreground"
              v-html="formatOverview(extra.overview)"
            />
            <p v-else class="mt-2 text-sm text-muted-foreground">{{ extraError || '暂无内容' }}</p>
          </div>
          <div class="rounded-2xl border border-border bg-card p-4">
            <div class="flex items-center gap-2">
              <ListChecks class="h-4 w-4 text-primary" />
              <h3 class="text-sm font-semibold text-foreground">关键信息</h3>
            </div>
            <ul
              v-if="extra?.keyInformation?.length"
              class="mt-3 list-decimal pl-4 text-[15px] leading-7 text-muted-foreground"
            >
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
                <button
                  type="button"
                  class="line-clamp-1 w-full text-left underline-offset-4 hover:text-sky-600 hover:underline"
                  :class="
                    activeHeadingId === item.id
                      ? 'font-semibold text-sky-600'
                      : 'text-muted-foreground/70'
                  "
                  :title="item.text"
                  @click="scrollToHeading(item.id)"
                >
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
            <button
              v-for="item in recommendations"
              :key="item.id"
              class="mt-3 flex w-full items-start justify-between gap-3 rounded-xl border border-border px-3 py-3 text-left text-sm hover:bg-muted"
              @click="props.onOpenArticle(item.id)"
            >
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
  </div>
</template>