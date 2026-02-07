<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ArticleCard from '../components/articles/ArticleCard.vue'
import ArticleDetailPane from '../components/articles/ArticleDetailPane.vue'
import EmptyState from '../components/common/EmptyState.vue'
import { feedApi } from '../services/frontApi'
import type { ArticleFeed } from '../types'
import { useInfiniteScroll } from '../composables/useInfiniteScroll'
import { useUiStore } from '../stores/ui'
import { useHistoryStore } from '../stores/history'
import { useCacheStore } from '../stores/cache'
import { formatRelativeTime } from '../utils/time'
import { Bookmark, History, Trash2 } from 'lucide-vue-next'

const ui = useUiStore()
const historyStore = useHistoryStore()
const cache = useCacheStore()
const route = useRoute()
const router = useRouter()

// 移动端 Tab 切换
const activeTab = ref<'favorites' | 'history'>('favorites')

const listContainer = ref<HTMLElement | null>(null)
const favorites = ref<ArticleFeed[]>([])
const page = ref(0)
const last = ref(false)
const loading = ref(false)

const searchQuery = ref('')
const committedQuery = ref('')
const searchLoading = ref(false)
const searchResults = ref<ArticleFeed[]>([])

const isSameFeed = (next: ArticleFeed[], current: ArticleFeed[]) => {
  if (next.length !== current.length) return false
  return next.every((item, index) => item.id === current[index]?.id && item.pubDate === current[index]?.pubDate)
}

const applyFavoritesCache = (): boolean => {
  const cached = cache.getFavorites()
  if (!cached) return false
  favorites.value = [...cached.items]
  page.value = cached.page
  last.value = cached.last
  loading.value = false
  return true
}

const persistFavoritesCache = () => {
  cache.setFavorites({
    items: favorites.value,
    page: page.value,
    last: last.value
  })
}

const loadMore = async () => {
  if (loading.value || last.value || committedQuery.value) return
  loading.value = true
  try {
    const res = await feedApi.favorites(page.value, 10)
    favorites.value.push(...res.content)
    last.value = res.last
    page.value += 1
    persistFavoritesCache()
  } finally {
    loading.value = false
  }
}

const refreshFavorites = async (silent = false) => {
  if (committedQuery.value) return
  const showLoading = !silent && favorites.value.length === 0
  if (showLoading) loading.value = true
  try {
    const res = await feedApi.favorites(0, 10)
    const currentFirstPage = favorites.value.slice(0, res.content.length)
    const same = isSameFeed(res.content, currentFirstPage) && favorites.value.length >= res.content.length
    if (!same) {
      favorites.value = res.content
      page.value = 1
      last.value = res.last
    }
    persistFavoritesCache()
  } finally {
    if (showLoading) loading.value = false
  }
}

const { sentinel } = useInfiniteScroll(loadMore, listContainer)

const detailOpen = computed(() => ui.detailOpen)

const search = async (query: string) => {
  if (!query) {
    searchResults.value = []
    return
  }
  searchLoading.value = true
  try {
    searchResults.value = await feedApi.search(query, 'FAVORITE')
  } finally {
    searchLoading.value = false
  }
}

const onSearchSubmit = async () => {
  const query = searchQuery.value.trim()
  committedQuery.value = query

  const nextQuery = { ...route.query }
  if (query) {
    nextQuery.q = query
  } else {
    delete nextQuery.q
  }
  router.push({ path: route.path, query: nextQuery }).catch(() => {
    // 忽略导航被中止的错误
  })

  await search(query)
}

const onOpenArticle = (id: number) => {
  // 更新 URL 查询参数
  const query = { ...route.query, articleId: String(id) }
  router.push({ path: route.path, query }).catch(() => {
    // 忽略导航被中止的错误
  })

  ui.openDetail(id, listContainer.value)
}

const onClearHistory = () => {
  if (confirm('确定要清空所有浏览历史吗？')) {
    historyStore.clearAll()
  }
}


onMounted(async () => {
  // 从 URL 查询参数恢复状态
  const searchQ = route.query.q
  const articleId = route.query.articleId
  const tab = route.query.tab

  if (tab === 'history') {
    activeTab.value = 'history'
  }

  if (searchQ) {
    searchQuery.value = String(searchQ)
    committedQuery.value = String(searchQ)
    await search(committedQuery.value)
  }

  const usedFavoritesCache = applyFavoritesCache()
  await refreshFavorites(usedFavoritesCache)

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

// 监听 tab 切换，更新 URL
watch(activeTab, (newTab) => {
  const query = { ...route.query }
  if (newTab === 'history') {
    query.tab = 'history'
  } else {
    delete query.tab
  }
  router.push({ path: route.path, query }).catch(() => { })
})
</script>

<template>
  <!-- 移动端文章详情覆盖层 -->
  <div v-if="detailOpen" class="fixed inset-0 z-[60] flex flex-col bg-background md:hidden">
    <ArticleDetailPane :articleId="ui.detailArticleId" :onClose="ui.closeDetail"
      :onOpenArticle="(id) => ui.openDetail(id, listContainer)" />
  </div>

  <div class="flex h-screen flex-col gap-4 px-4 py-4 md:grid md:gap-6 md:px-6 md:py-6"
    :class="detailOpen ? 'md:grid-cols-[200px_1fr]' : 'md:grid-cols-[280px_1fr]'">
    <!-- 桌面端侧边栏 -->
    <section class="hidden h-full flex-col gap-4 overflow-hidden md:flex">
      <div class="rounded-2xl border border-border bg-card p-4">
        <div class="flex items-center gap-2">
          <Bookmark class="h-4 w-4 text-primary" />
          <h2 class="text-sm font-semibold text-foreground">收藏</h2>
        </div>
        <p class="mt-2 text-xs text-muted-foreground">保存的重要文章都在这里</p>
      </div>
      <div class="rounded-2xl border border-border bg-card p-4">
        <h3 class="text-sm font-semibold text-foreground">提示</h3>
        <ul class="mt-3 list-decimal pl-4 text-xs text-muted-foreground">
          <li>点击文章进入详情即可收藏</li>
          <li>收藏列表支持关键词搜索</li>
          <li>内容按发布时间倒序排列</li>
        </ul>
      </div>
    </section>

    <!-- 主内容区域 -->
    <section class="flex flex-1 flex-col gap-4 overflow-hidden">
      <!-- 桌面端文章详情 -->
      <ArticleDetailPane v-if="detailOpen" class="hidden md:flex" :articleId="ui.detailArticleId"
        :onClose="ui.closeDetail" :onOpenArticle="(id) => ui.openDetail(id, listContainer)" />
      <template v-if="!detailOpen">
        <div class="rounded-2xl border border-border bg-card p-3 md:p-4">
          <!-- 移动端 Tab 切换 -->
          <div class="mb-3 flex gap-2 md:hidden">
            <button class="flex-1 rounded-xl border px-3 py-2 text-sm transition"
              :class="activeTab === 'favorites' ? 'border-primary bg-primary text-primary-foreground' : 'border-border text-muted-foreground'"
              @click="activeTab = 'favorites'">
              <Bookmark class="mr-1.5 inline h-4 w-4" />
              收藏
            </button>
            <button class="flex-1 rounded-xl border px-3 py-2 text-sm transition"
              :class="activeTab === 'history' ? 'border-primary bg-primary text-primary-foreground' : 'border-border text-muted-foreground'"
              @click="activeTab = 'history'">
              <History class="mr-1.5 inline h-4 w-4" />
              历史
            </button>
          </div>

          <!-- 桌面端标题 -->
          <div class="flex items-center justify-between">
            <div class="flex items-center gap-2">
              <Bookmark class="hidden h-4 w-4 text-primary md:block" />
              <h2 class="text-sm font-semibold text-foreground">
                {{ activeTab === 'favorites' ? '收藏列表' : '浏览历史' }}
              </h2>
            </div>
            <span v-if="activeTab === 'history'" class="text-xs text-muted-foreground">
              {{ historyStore.historyList.value.length }} 条记录
            </span>
          </div>

          <!-- 收藏搜索框 -->
          <div v-if="activeTab === 'favorites'" class="mt-3 flex items-center gap-2 md:mt-4 md:gap-3">
            <input v-model="searchQuery" placeholder="搜索收藏"
              class="flex-1 rounded-xl border border-border px-3 py-2 text-sm" @keydown.enter="onSearchSubmit" />
            <button class="rounded-xl border border-border px-3 py-2 text-xs text-muted-foreground"
              :disabled="searchLoading" @click="onSearchSubmit">
              搜索
            </button>
          </div>

          <!-- 历史操作栏 -->
          <div v-if="activeTab === 'history' && historyStore.historyList.value.length > 0" class="mt-3">
            <button
              class="flex items-center gap-1.5 rounded-xl border border-border px-3 py-2 text-xs text-muted-foreground hover:text-foreground hover:bg-muted"
              @click="onClearHistory">
              <Trash2 class="h-3.5 w-3.5" />
              清空历史
            </button>
          </div>
        </div>

        <div ref="listContainer" class="flex-1 space-y-3 overflow-y-auto scrollbar-thin">
          <!-- 收藏列表 -->
          <template v-if="activeTab === 'favorites'">
            <div v-if="committedQuery" class="space-y-3">
              <ArticleCard v-for="item in searchResults" :key="item.id" :article="item" @open="onOpenArticle" />
              <EmptyState v-if="!searchResults.length && !searchLoading" title="暂无搜索结果" />
            </div>

            <div v-else class="space-y-3">
              <ArticleCard v-for="item in favorites" :key="item.id" :article="item" @open="onOpenArticle" />
              <EmptyState v-if="!favorites.length && !loading" title="暂无收藏" description="点击文章即可收藏" />
            </div>

            <div ref="sentinel" class="h-6" />
            <div v-if="loading && favorites.length" class="text-center text-xs text-muted-foreground">
              加载更多收藏...
            </div>
            <div v-if="last && favorites.length" class="text-center text-xs text-muted-foreground">
              已经到底了
            </div>
          </template>

          <!-- 历史列表 -->
          <template v-if="activeTab === 'history'">
            <EmptyState v-if="!historyStore.historyList.value.length" title="暂无阅读历史" description="阅读文章后会自动记录" />
            <div v-else class="space-y-3">
              <div v-for="item in historyStore.historyList.value" :key="item.articleId"
                class="group flex cursor-pointer gap-3 rounded-2xl border border-border bg-card p-3 transition hover:shadow md:gap-4 md:p-4"
                @click="onOpenArticle(item.articleId)">
                <div v-if="item.coverImage"
                  class="h-14 w-20 shrink-0 overflow-hidden rounded-xl border border-border md:h-16 md:w-24">
                  <img :src="item.coverImage" :alt="item.title" class="h-full w-full object-cover" loading="lazy"
                    referrerpolicy="no-referrer" />
                </div>
                <div class="flex min-w-0 flex-1 flex-col gap-1 md:gap-2">
                  <div class="flex items-start justify-between gap-2 md:gap-4">
                    <h3 class="line-clamp-2 text-sm font-semibold text-foreground">{{ item.title }}</h3>
                    <span class="flex-shrink-0 text-xs text-muted-foreground">{{ formatRelativeTime(item.readAt)
                      }}</span>
                  </div>
                  <div class="flex items-center justify-between text-xs text-muted-foreground">
                    <span>{{ item.sourceName }}</span>
                    <div class="flex items-center gap-2">
                      <div class="h-1.5 w-16 rounded-full bg-muted overflow-hidden">
                        <div class="h-full bg-primary transition-all"
                          :style="{ width: `${item.readProgress * 100}%` }" />
                      </div>
                      <span>{{ Math.round(item.readProgress * 100) }}%</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </template>
        </div>
      </template>
    </section>
  </div>
</template>
