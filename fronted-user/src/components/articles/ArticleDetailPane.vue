<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { feedApi } from '../../services/frontApi'
import { formatRelativeTime } from '../../utils/time'
import { renderMarkdown } from '../../utils/markdown'
import { formatOverview } from '../../utils/text'
import type { ArticleDetail, ArticleExtra, ArticleFeed } from '../../types'
import { useToastStore } from '../../stores/toast'

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

const leftWidth = ref(60)
const isDragging = ref(false)

const dividerStyle = computed(() => ({
  width: `${100 - leftWidth.value}%`
}))

const load = async () => {
  if (!props.articleId) return
  loading.value = true
  extraError.value = null
  favorite.value = false
  recommendations.value = []
  try {
    article.value = await feedApi.detail(props.articleId)
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
  } catch {
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

onMounted(() => {
  load()
  window.addEventListener('mousemove', onMouseMove)
  window.addEventListener('mouseup', onMouseUp)
})

onUnmounted(() => {
  window.removeEventListener('mousemove', onMouseMove)
  window.removeEventListener('mouseup', onMouseUp)
})
</script>

<template>
  <div class="flex h-full flex-col">
    <header class="flex items-center justify-between border-b border-border px-8 py-4">
      <button
        class="rounded-lg border border-border px-3 py-1 text-sm text-muted-foreground hover:bg-muted"
        @click="onClose"
      >
        返回
      </button>
      <div class="flex items-center gap-3">
        <button
          class="rounded-lg border border-border px-3 py-1 text-xs"
          :class="favorite ? 'bg-primary text-primary-foreground' : 'text-muted-foreground hover:bg-muted'"
          @click="toggleFavorite"
        >
          {{ favorite ? '已收藏' : '收藏' }}
        </button>
        <div class="text-sm text-muted-foreground">文章详情</div>
      </div>
    </header>

    <div class="flex flex-1 overflow-hidden">
      <section
        class="h-full overflow-y-auto border-r border-border px-8 py-6 scrollbar-thin"
        :style="{ width: `${leftWidth}%` }"
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

      <section class="h-full flex-1 overflow-y-auto px-8 py-6 scrollbar-thin" :style="dividerStyle">
        <div class="space-y-4">
          <div class="rounded-2xl border border-border bg-card p-4">
            <h3 class="text-sm font-semibold text-foreground">精华速览</h3>
            <p
              v-if="extra"
              class="mt-3 text-[15px] leading-7 text-muted-foreground"
              v-html="formatOverview(extra.overview)"
            />
            <p v-else class="mt-2 text-sm text-muted-foreground">{{ extraError || '暂无内容' }}</p>
          </div>
          <div class="rounded-2xl border border-border bg-card p-4">
            <h3 class="text-sm font-semibold text-foreground">关键信息</h3>
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

          <div class="rounded-2xl border border-border bg-card p-4">
            <h3 class="text-sm font-semibold text-foreground">相似推荐</h3>
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
