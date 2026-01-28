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

const ui = useUiStore()
const route = useRoute()
const router = useRouter()

const listContainer = ref<HTMLElement | null>(null)
const favorites = ref<ArticleFeed[]>([])
const page = ref(0)
const last = ref(false)
const loading = ref(false)

const searchQuery = ref('')
const searchLoading = ref(false)
const searchResults = ref<ArticleFeed[]>([])

const loadMore = async () => {
  if (loading.value || last.value || searchQuery.value.trim()) return
  loading.value = true
  try {
    const res = await feedApi.favorites(page.value, 10)
    favorites.value.push(...res.content)
    last.value = res.last
    page.value += 1
  } finally {
    loading.value = false
  }
}

const { sentinel } = useInfiniteScroll(loadMore, listContainer)

const detailOpen = computed(() => ui.detailOpen)

const search = async () => {
  const query = searchQuery.value.trim()
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

const onOpenArticle = (id: number) => {
  // 更新 URL 查询参数
  const query = { ...route.query, articleId: String(id) }
  router.push({ path: route.path, query }).catch(() => {
    // 忽略导航被中止的错误
  })

  ui.openDetail(id, listContainer.value)
}

watch(searchQuery, () => {
  if (searchQuery.value.trim()) {
    // 更新 URL 查询参数
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
  const searchQ = route.query.q
  const articleId = route.query.articleId

  if (searchQ) {
    searchQuery.value = String(searchQ)
  }

  await loadMore()

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

  <div class="flex h-screen flex-col gap-4 px-4 py-4 md:grid md:gap-6 md:px-6 md:py-6"
    :class="detailOpen ? 'md:grid-cols-[200px_1fr]' : 'md:grid-cols-[280px_1fr]'">
    <!-- 桌面端侧边栏 -->
    <section class="hidden h-full flex-col gap-4 overflow-hidden md:flex">
      <div class="rounded-2xl border border-border bg-card p-4">
        <h2 class="text-sm font-semibold text-foreground">收藏</h2>
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
          <div class="flex items-center justify-between">
            <h2 class="text-sm font-semibold text-foreground">收藏列表</h2>
          </div>
          <div class="mt-3 flex items-center gap-2 md:mt-4 md:gap-3">
            <input v-model="searchQuery" placeholder="搜索收藏"
              class="flex-1 rounded-xl border border-border px-3 py-2 text-sm" />
            <button class="rounded-xl border border-border px-3 py-2 text-xs text-muted-foreground"
              :disabled="searchLoading" @click="search">
              搜索
            </button>
          </div>
        </div>

        <div ref="listContainer" class="flex-1 space-y-3 overflow-y-auto scrollbar-thin">
          <div v-if="searchQuery" class="space-y-3">
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
        </div>
      </template>
    </section>
  </div>
</template>
