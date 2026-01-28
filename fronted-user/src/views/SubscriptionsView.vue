<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ArticleCard from '../components/articles/ArticleCard.vue'
import ArticlePreviewPanel from '../components/articles/ArticlePreviewPanel.vue'
import ArticleDetailPane from '../components/articles/ArticleDetailPane.vue'
import WordCloudCard from '../components/trends/WordCloudCard.vue'
import LoadingState from '../components/common/LoadingState.vue'
import EmptyState from '../components/common/EmptyState.vue'
import ErrorState from '../components/common/ErrorState.vue'
import { subscriptionApi, feedApi, trendApi } from '../services/frontApi'
import type { ArticleExtra, ArticleFeed, Subscription } from '../types'
import { useInfiniteScroll } from '../composables/useInfiniteScroll'
import { useDevice } from '../composables/useDevice'
import { useUiStore } from '../stores/ui'
import { useToastStore } from '../stores/toast'
import { CalendarDays, ChevronDown, Rss, Search, X } from 'lucide-vue-next'

const ui = useUiStore()
const toast = useToastStore()
const route = useRoute()
const router = useRouter()
const { isMobile } = useDevice()

const subscriptions = ref<Subscription[]>([])
const loadingSubscriptions = ref(false)
const subscriptionsError = ref('')

const activeSubscriptionId = ref<number | null>(null)

// 移动端订阅抽屉控制
const showMobileSheet = ref(false)

const listContainer = ref<HTMLElement | null>(null)
const feedList = ref<ArticleFeed[]>([])
const feedCursor = ref<string | null>(null)
const feedLoading = ref(false)
const feedError = ref('')
const hasMore = ref(true)

const searchQuery = ref('')
const searchLoading = ref(false)
const searchResults = ref<ArticleFeed[]>([])

const wordCloud = ref<{ text: string; value: number }[]>([])
const wordCloudLoading = ref(false)

const previewLoading = ref(false)
const previewExtra = ref<ArticleExtra | null>(null)
const previewError = ref<string | null>(null)
let hoverTimer: number | null = null

const detailOpen = computed(() => ui.detailOpen)

const activeSubscription = computed(() =>
  subscriptions.value.find((item) => item.id === activeSubscriptionId.value) || null
)

const activeSourceId = computed(() => {
  if (!activeSubscription.value) return undefined
  if (activeSubscription.value.type === 'RSS') return activeSubscription.value.targetId
  return undefined
})

const activeSubscriptionName = computed(() => {
  if (!activeSubscription.value) return '全部订阅'
  return activeSubscription.value.type === 'RSS'
    ? activeSubscription.value.name
    : activeSubscription.value.content
})

const loadSubscriptions = async () => {
  loadingSubscriptions.value = true
  subscriptionsError.value = ''
  try {
    subscriptions.value = await subscriptionApi.list()
  } catch (error: any) {
    subscriptionsError.value = error?.message || '订阅列表加载失败'
  } finally {
    loadingSubscriptions.value = false
  }
}

const resetFeed = () => {
  feedList.value = []
  feedCursor.value = null
  hasMore.value = true
}

const loadFeed = async () => {
  if (feedLoading.value || !hasMore.value || searchQuery.value.trim()) return
  feedLoading.value = true
  feedError.value = ''
  try {
    const list = await feedApi.feed({
      subscriptionId: activeSubscriptionId.value || undefined,
      cursor: feedCursor.value || undefined,
      size: 20
    })
    feedList.value.push(...list)
    if (list.length < 20) {
      hasMore.value = false
    }
    const last = list[list.length - 1]
    if (last) {
      feedCursor.value = `${last.pubDate},${last.id}`
    }
  } catch (error: any) {
    feedError.value = error?.message || '时间线加载失败'
  } finally {
    feedLoading.value = false
  }
}

const loadWordCloud = async () => {
  if (activeSubscription.value?.type === 'TOPIC') {
    wordCloud.value = []
    wordCloudLoading.value = false
    return
  }
  wordCloudLoading.value = true
  try {
    wordCloud.value = await trendApi.wordCloud(activeSourceId.value)
  } catch {
    wordCloud.value = []
  } finally {
    wordCloudLoading.value = false
  }
}

const search = async () => {
  const query = searchQuery.value.trim()
  if (!query) {
    searchResults.value = []
    return
  }
  searchLoading.value = true
  try {
    searchResults.value = await feedApi.search(query, 'SUBSCRIBED')
  } catch {
    searchResults.value = []
  } finally {
    searchLoading.value = false
  }
}

const loadMore = () => {
  loadFeed()
}

const { sentinel } = useInfiniteScroll(loadMore, listContainer)

const onSelectSubscription = (id: number | null) => {
  ui.closeDetail()
  activeSubscriptionId.value = id
  showMobileSheet.value = false

  // 更新 URL 查询参数
  const query = { ...route.query }
  if (id !== null) {
    query.subscriptionId = String(id)
  } else {
    delete query.subscriptionId
  }
  router.push({ path: route.path, query }).catch(() => {
    // 忽略导航被中止的错误
  })

  resetFeed()
  loadFeed()
  loadWordCloud()
}

const onHoverArticle = (id: number) => {
  // 移动端不触发 hover 预览
  if (isMobile.value) return

  if (hoverTimer) window.clearTimeout(hoverTimer)
  hoverTimer = window.setTimeout(async () => {
    previewLoading.value = true
    previewError.value = null
    try {
      previewExtra.value = await feedApi.extra(id)
    } catch {
      previewExtra.value = null
      previewError.value = 'AI 增强信息暂不可用'
    } finally {
      previewLoading.value = false
    }
  }, 200)
}

const onLeaveArticle = () => {
  if (hoverTimer) window.clearTimeout(hoverTimer)
}

const onOpenArticle = (id: number) => {
  ui.openDetail(id, listContainer.value)
}

const onCancelSubscription = async (item: Subscription) => {
  try {
    await subscriptionApi.remove(item.id)
    toast.push('已取消订阅', 'success')
    if (activeSubscriptionId.value === item.id) {
      activeSubscriptionId.value = null
      resetFeed()
    }
    await loadSubscriptions()
    loadFeed()
    loadWordCloud()
  } catch (error: any) {
    toast.push(error?.message || '取消订阅失败', 'error')
  }
}

const feedDisplay = computed(() => {
  const result: Array<{ type: 'separator'; date: string } | { type: 'article'; item: ArticleFeed }> = []
  let lastDate = ''
  for (const item of feedList.value) {
    const date = item.pubDate.split('T')[0]
    if (date !== lastDate) {
      result.push({ type: 'separator', date })
      lastDate = date
    }
    result.push({ type: 'article', item })
  }
  return result
})

watch(activeSubscriptionId, () => {
  resetFeed()
  loadFeed()
  loadWordCloud()
})

watch(searchQuery, () => {
  if (searchQuery.value.trim()) {
    // 更新 URL 中的搜索查询参数
    const query = { ...route.query, q: searchQuery.value.trim() }
    router.push({ path: route.path, query }).catch(() => {
      // 忽略导航被中止的错误
    })
    search()
  } else {
    // 清除搜索参数
    const query = { ...route.query }
    delete query.q
    router.push({ path: route.path, query }).catch(() => {
      // 忽略导航被中止的错误
    })
    searchResults.value = []
  }
})

onMounted(async () => {
  // 从 URL 查询参数恢复状态
  const subscriptionId = route.query.subscriptionId
  const articleId = route.query.articleId
  const searchQ = route.query.q

  await loadSubscriptions()

  if (subscriptionId) {
    activeSubscriptionId.value = parseInt(String(subscriptionId), 10)
  }

  if (searchQ) {
    searchQuery.value = String(searchQ)
  }

  loadFeed()
  loadWordCloud()

  // 恢复文章详情状态
  if (articleId && parseInt(String(articleId), 10) > 0) {
    ui.restoreDetailFromUrl(parseInt(String(articleId), 10))
  }
})

// 监听路由变化，支持浏览器后退键
watch(
  () => route.query.articleId,
  () => {
    ui.syncWithRoute()
  }
)
</script>

<template>
  <!-- 移动端文章详情覆盖层 -->
  <div v-if="detailOpen" class="fixed inset-0 z-[60] flex flex-col bg-background md:hidden">
    <ArticleDetailPane :articleId="ui.detailArticleId" :onClose="ui.closeDetail"
      :onOpenArticle="(id) => ui.openDetail(id, listContainer)" />
  </div>

  <!-- 移动端订阅选择抽屉 -->
  <div v-if="showMobileSheet" class="fixed inset-0 z-50 md:hidden" @click.self="showMobileSheet = false">
    <div class="absolute inset-0 bg-black/50" @click="showMobileSheet = false" />
    <div class="absolute inset-x-0 bottom-0 max-h-[70vh] rounded-t-2xl bg-card pb-safe">
      <div class="flex items-center justify-between border-b border-border px-4 py-3">
        <h3 class="text-sm font-semibold text-foreground">选择订阅</h3>
        <button class="p-1 text-muted-foreground" @click="showMobileSheet = false">
          <X class="h-5 w-5" />
        </button>
      </div>
      <div class="max-h-[calc(70vh-56px)] overflow-y-auto p-4">
        <button class="mb-2 w-full rounded-xl border border-border px-3 py-3 text-left text-sm"
          :class="!activeSubscriptionId ? 'bg-primary text-primary-foreground' : 'bg-card'"
          @click="onSelectSubscription(null)">
          全部订阅
        </button>
        <div v-if="loadingSubscriptions" class="py-4">
          <LoadingState />
        </div>
        <div v-else class="space-y-2">
          <div v-for="item in subscriptions" :key="item.id"
            class="flex w-full items-center justify-between rounded-xl border border-border px-3 py-3 text-left text-sm"
            :class="activeSubscriptionId === item.id ? 'bg-muted' : 'bg-card'" role="button" tabindex="0"
            @click="onSelectSubscription(item.id)">
            <div class="flex min-w-0 flex-1 items-center gap-2">
              <div v-if="item.type === 'RSS' && item.icon" class="h-6 w-6 flex-shrink-0">
                <img :src="item.icon" :alt="item.name" class="h-full w-full rounded object-cover"
                  @error="(e) => ((e.target as HTMLImageElement).style.display = 'none')" />
              </div>
              <div class="min-w-0 flex-1">
                <p class="text-foreground" :class="item.type === 'TOPIC' ? 'line-clamp-2' : 'line-clamp-1'">
                  {{ item.type === 'RSS' ? item.name : item.content }}
                </p>
                <p class="text-xs text-muted-foreground">
                  {{ item.type === 'RSS' ? item.category : 'TOPIC' }}
                </p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>

  <!-- 主布局 -->
  <div class="flex h-screen flex-col gap-4 px-4 py-4 md:grid md:gap-6 md:px-6 md:py-6" :class="{
    'md:grid-cols-[200px_0_1fr]': detailOpen,
    'md:grid-cols-[280px_1fr_320px]': !detailOpen
  }">
    <!-- 桌面端侧边栏 -->
    <section class="hidden h-full flex-col gap-4 overflow-hidden md:flex">
      <div class="rounded-2xl border border-border bg-card p-4">
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-2">
            <Rss class="h-4 w-4 text-primary" />
            <h2 class="text-sm font-semibold text-foreground">订阅</h2>
          </div>
        </div>
        <button class="mt-4 w-full rounded-xl border border-border px-3 py-2 text-sm"
          :class="!activeSubscriptionId ? 'bg-primary text-primary-foreground' : 'bg-card'"
          @click="onSelectSubscription(null)">
          全部订阅
        </button>
      </div>

      <div class="flex-1 overflow-y-auto scrollbar-thin">
        <div class="rounded-2xl border border-border bg-card p-4">
          <div class="flex items-center justify-between">
            <h3 class="text-sm font-semibold text-foreground">我的订阅</h3>
            <span class="text-xs text-muted-foreground">{{ subscriptions.length }}</span>
          </div>
          <div v-if="loadingSubscriptions" class="mt-4">
            <LoadingState />
          </div>
          <ErrorState v-else-if="subscriptionsError" :title="subscriptionsError" :onRetry="loadSubscriptions" />
          <div v-else class="mt-4 space-y-2">
            <div v-for="item in subscriptions" :key="item.id"
              class="flex w-full items-center justify-between rounded-xl border border-border px-3 py-2 text-left text-xs"
              :class="activeSubscriptionId === item.id ? 'bg-muted' : 'bg-card'" role="button" tabindex="0"
              @click="onSelectSubscription(item.id)">
              <div class="flex min-w-0 flex-1 items-center gap-2">
                <div v-if="item.type === 'RSS' && item.icon" class="h-5 w-5 flex-shrink-0">
                  <img :src="item.icon" :alt="item.name" class="h-full w-full rounded object-cover"
                    @error="(e) => ((e.target as HTMLImageElement).style.display = 'none')" />
                </div>
                <div class="min-w-0 flex-1">
                  <p class="text-foreground" :class="item.type === 'TOPIC' ? 'line-clamp-2' : 'line-clamp-1'">
                    {{ item.type === 'RSS' ? item.name : item.content }}
                  </p>
                  <p class="text-[11px] text-muted-foreground">
                    {{ item.type === 'RSS' ? item.category : 'TOPIC' }}
                  </p>
                </div>
              </div>
              <button class="text-[11px] text-muted-foreground hover:text-foreground"
                @click.stop="onCancelSubscription(item)">
                取消
              </button>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- 时间线区域（移动端和桌面端共用） -->
    <section class="flex flex-1 flex-col gap-4 overflow-hidden transition"
      :class="detailOpen ? 'opacity-0 pointer-events-none md:opacity-0' : 'opacity-100'">
      <div class="rounded-2xl border border-border bg-card p-3 md:p-4">
        <!-- 移动端订阅切换按钮 -->
        <button
          class="mb-3 flex w-full items-center justify-between rounded-xl border border-border px-3 py-2 text-left md:hidden"
          @click="showMobileSheet = true">
          <span class="text-sm text-foreground">{{ activeSubscriptionName }}</span>
          <ChevronDown class="h-4 w-4 text-muted-foreground" />
        </button>

        <div class="flex items-center justify-between">
          <div class="flex items-center gap-2">
            <CalendarDays class="h-4 w-4 text-primary" />
            <h2 class="text-sm font-semibold text-foreground">时间线</h2>
          </div>
          <span class="hidden text-xs text-muted-foreground md:inline">
            {{ activeSubscriptionName }}
          </span>
        </div>
        <div class="mt-3 flex items-center gap-2 md:mt-4 md:gap-3">
          <input v-model="searchQuery" placeholder="在订阅中搜索"
            class="flex-1 rounded-xl border border-border px-3 py-2 text-sm" />
          <button
            class="inline-flex items-center gap-2 rounded-xl border border-border px-3 py-2 text-xs text-muted-foreground"
            :disabled="searchLoading" @click="search">
            <Search class="h-3.5 w-3.5" />
            <span class="hidden sm:inline">搜索</span>
          </button>
        </div>
      </div>

      <div ref="listContainer" class="flex-1 space-y-3 overflow-y-auto scrollbar-thin">
        <LoadingState v-if="feedLoading && !feedList.length && !searchQuery" />
        <EmptyState v-else-if="!feedList.length && !feedLoading && !searchQuery" title="暂无内容"
          description="订阅 RSS 或创建主题以生成时间线" />
        <ErrorState v-else-if="feedError" :title="feedError" :onRetry="loadFeed" />

        <div v-if="searchQuery" class="space-y-3">
          <ArticleCard v-for="item in searchResults" :key="item.id" :article="item" @open="onOpenArticle"
            @hover="onHoverArticle" @leave="onLeaveArticle" />
          <EmptyState v-if="!searchResults.length && !searchLoading" title="没有搜索结果" description="尝试换个关键词" />
        </div>

        <div v-else class="space-y-3">
          <template v-for="(entry, index) in feedDisplay" :key="`feed-${index}`">
            <div v-if="entry.type === 'separator'" class="flex items-center gap-4 py-2 text-xs text-muted-foreground">
              <span class="h-px flex-1 bg-border" />
              <span class="rounded-full bg-muted px-3 py-1">{{ entry.date }}</span>
              <span class="h-px flex-1 bg-border" />
            </div>
            <ArticleCard v-else :article="entry.item" @open="onOpenArticle" @hover="onHoverArticle"
              @leave="onLeaveArticle" />
          </template>
        </div>

        <div ref="sentinel" class="h-6" />
        <div v-if="feedLoading && feedList.length" class="text-center text-xs text-muted-foreground">
          加载更多中...
        </div>
        <div v-if="!hasMore && feedList.length" class="text-center text-xs text-muted-foreground">
          已到达底部
        </div>
      </div>
    </section>

    <!-- 右侧面板（仅桌面端显示） -->
    <section class="hidden h-full flex-col gap-4 overflow-hidden md:flex">
      <ArticleDetailPane v-if="detailOpen" :articleId="ui.detailArticleId" :onClose="ui.closeDetail"
        :onOpenArticle="(id) => ui.openDetail(id, listContainer)" />
      <template v-else>
        <WordCloudCard :data="wordCloud" :loading="wordCloudLoading" />
        <ArticlePreviewPanel :extra="previewExtra" :loading="previewLoading" :error="previewError" />
      </template>
    </section>
  </div>
</template>
