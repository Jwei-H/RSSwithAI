<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ArticleCard from '../components/articles/ArticleCard.vue'
import ArticleDetailPane from '../components/articles/ArticleDetailPane.vue'
import HotEventsList from '../components/trends/HotEventsList.vue'
import RssSourceCard from '../components/discover/RssSourceCard.vue'
import SourcePreviewDialog from '../components/discover/SourcePreviewDialog.vue'
import LoadingState from '../components/common/LoadingState.vue'
import EmptyState from '../components/common/EmptyState.vue'
import { feedApi, rssApi, subscriptionApi, trendApi } from '../services/frontApi'
import type { ArticleFeed, HotEvent, RssSource } from '../types'
import { useToastStore } from '../stores/toast'
import { useUiStore } from '../stores/ui'
import { useInfiniteScroll } from '../composables/useInfiniteScroll'
import { Compass, Search, Sparkles } from 'lucide-vue-next'

const ui = useUiStore()
const toast = useToastStore()
const route = useRoute()
const router = useRouter()

const searchQuery = ref('')
const searchResults = ref<ArticleFeed[]>([])
const searchLoading = ref(false)

const hotEvents = ref<HotEvent[]>([])
const hotLoading = ref(false)

const topicInput = ref('')
const topicLoading = ref(false)

const categories = [
  { label: '全部', value: '' },
  { label: '新闻', value: 'NEWS' },
  { label: '科技', value: 'TECH' },
  { label: '社会', value: 'SOCIETY' },
  { label: '财经', value: 'FINANCE' },
  { label: '生活', value: 'LIFESTYLE' },
  { label: '其他', value: 'OTHER' }
]
const activeCategory = ref('')

const sources = ref<RssSource[]>([])
const page = ref(0)
const last = ref(false)
const sourcesLoading = ref(false)

const previewSource = ref<RssSource | null>(null)
const previewOpen = ref(false)

const listContainer = ref<HTMLElement | null>(null)

const detailOpen = computed(() => ui.detailOpen)

const loadHotEvents = async () => {
  hotLoading.value = true
  try {
    const list = await trendApi.hotEvents()
    hotEvents.value = list.slice(0, 10)
  } finally {
    hotLoading.value = false
  }
}

const loadSources = async () => {
  if (sourcesLoading.value || last.value) return
  sourcesLoading.value = true
  try {
    const res = await rssApi.list({ page: page.value, size: 12, category: activeCategory.value || undefined })
    sources.value.push(...res.content)
    last.value = res.last
    page.value += 1
  } finally {
    sourcesLoading.value = false
  }
}

const { sentinel } = useInfiniteScroll(loadSources, listContainer)

const search = async () => {
  const query = searchQuery.value.trim()
  if (!query) {
    searchResults.value = []
    return
  }
  searchLoading.value = true
  try {
    searchResults.value = await feedApi.search(query, 'ALL')
  } finally {
    searchLoading.value = false
  }
}

const onSubscribeHot = async (item: HotEvent) => {
  if (item.isSubscribed) {
    toast.push('已订阅该热点', 'success')
    return
  }
  try {
    const topic = await subscriptionApi.createTopic({ content: item.event })
    await subscriptionApi.create({ type: 'TOPIC', targetId: topic.id })
    item.isSubscribed = true
    toast.push('热点已订阅', 'success')
  } catch (error: any) {
    toast.push(error?.message || '订阅失败', 'error')
  }
}

const onCreateTopic = async () => {
  const content = topicInput.value.trim()
  if (!content) {
    toast.push('请输入主题内容', 'error')
    return
  }
  if (content.length > 30) {
    toast.push('主题长度需小于等于 30 字', 'error')
    return
  }
  topicLoading.value = true
  try {
    const topic = await subscriptionApi.createTopic({ content })
    await subscriptionApi.create({ type: 'TOPIC', targetId: topic.id })
    toast.push('主题已订阅', 'success')
    topicInput.value = ''
  } catch (error: any) {
    toast.push(error?.message || '创建主题失败', 'error')
  } finally {
    topicLoading.value = false
  }
}

const onToggleSubscribe = async (source: RssSource) => {
  try {
    if (source.isSubscribed && source.subscriptionId) {
      await subscriptionApi.remove(source.subscriptionId)
      source.isSubscribed = false
      source.subscriptionId = null
      toast.push('已取消订阅', 'success')
    } else {
      const subscription = await subscriptionApi.create({ type: 'RSS', targetId: source.id })
      source.isSubscribed = true
      source.subscriptionId = subscription.id
      toast.push('订阅成功', 'success')
    }
  } catch (error: any) {
    toast.push(error?.message || '操作失败', 'error')
  }
}

const onPreview = (source: RssSource) => {
  previewSource.value = source
  previewOpen.value = true

  // 更新 URL 查询参数
  const query = { ...route.query, previewSourceId: String(source.id) }
  router.push({ path: route.path, query }).catch(() => {
    // 忽略导航被中止的错误
  })
}

const onOpenArticle = (id: number) => {
  previewOpen.value = false

  // 更新 URL 查询参数
  const query = { ...route.query, articleId: String(id) }
  delete query.previewSourceId
  router.push({ path: route.path, query }).catch(() => {
    // 忽略导航被中止的错误
  })

  ui.openDetail(id, listContainer.value)
}

watch(activeCategory, () => {
  sources.value = []
  page.value = 0
  last.value = false

  // 更新 URL 查询参数
  const query = { ...route.query }
  if (activeCategory.value) {
    query.category = activeCategory.value
  } else {
    delete query.category
  }
  router.push({ path: route.path, query }).catch(() => {
    // 忽略导航被中止的错误
  })

  loadSources()
})

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
  }
})

onMounted(() => {
  // 从 URL 查询参数恢复状态
  const category = route.query.category
  const searchQ = route.query.q
  const previewSourceId = route.query.previewSourceId
  const articleId = route.query.articleId

  if (category) {
    activeCategory.value = String(category)
  }

  if (searchQ) {
    searchQuery.value = String(searchQ)
  }

  loadHotEvents()
  loadSources()

  // 恢复预览源状态和文章详情状态
  if (previewSourceId && parseInt(String(previewSourceId), 10) > 0) {
    const sourceId = parseInt(String(previewSourceId), 10)
    // 这里在加载源之后设置预览
    setTimeout(() => {
      const source = sources.value.find(s => s.id === sourceId)
      if (source) {
        onPreview(source)
      }
    }, 500)
  }

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

// 监听 previewSourceId 变化，支持后退键关闭预览对话框
watch(
  () => route.query.previewSourceId,
  (newVal) => {
    if (!newVal && previewOpen.value) {
      previewOpen.value = false
      previewSource.value = null
    }
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
        <div class="flex items-center gap-2">
          <Compass class="h-4 w-4 text-primary" />
          <h2 class="text-sm font-semibold text-foreground">频道广场</h2>
        </div>
        <p class="mt-2 text-xs text-muted-foreground">探索新 RSS 源与热点事件</p>
      </div>
      <div class="rounded-2xl border border-border bg-card p-4">
        <div class="flex items-center gap-2">
          <Sparkles class="h-4 w-4 text-primary" />
          <h3 class="text-sm font-semibold text-foreground">订阅主题</h3>
        </div>
        <input v-model="topicInput" class="mt-3 w-full rounded-xl border border-border px-3 py-2 text-sm"
          placeholder="输入 30 字以内主题" />
        <button class="mt-3 w-full rounded-xl bg-primary px-3 py-2 text-sm text-primary-foreground"
          :disabled="topicLoading" @click="onCreateTopic">
          {{ topicLoading ? '创建中...' : '创建并订阅' }}
        </button>
      </div>
      <HotEventsList :items="hotEvents" :onSubscribe="onSubscribeHot" />
    </section>

    <!-- 主内容区域 -->
    <section class="flex flex-1 flex-col gap-4 overflow-hidden">
      <!-- 桌面端文章详情 -->
      <ArticleDetailPane v-if="detailOpen" class="hidden md:flex" :articleId="ui.detailArticleId"
        :onClose="ui.closeDetail" :onOpenArticle="(id) => ui.openDetail(id, listContainer)" />
      <template v-if="!detailOpen">
        <!-- 移动端：页面标题和主题创建入口 -->
        <div class="rounded-2xl border border-border bg-card p-3 md:hidden">
          <div class="flex items-center gap-2">
            <Compass class="h-4 w-4 text-primary" />
            <h2 class="text-sm font-semibold text-foreground">频道广场</h2>
          </div>
          <div class="mt-3 flex items-center gap-2">
            <input v-model="topicInput" class="flex-1 rounded-xl border border-border px-3 py-2 text-sm"
              placeholder="输入主题订阅" />
            <button class="rounded-xl bg-primary px-3 py-2 text-sm text-primary-foreground" :disabled="topicLoading"
              @click="onCreateTopic">
              {{ topicLoading ? '...' : '订阅' }}
            </button>
          </div>
        </div>

        <!-- 搜索栏 -->
        <div class="rounded-2xl border border-border bg-card p-3 md:p-4">
          <div class="flex items-center justify-between">
            <div class="flex items-center gap-2">
              <Search class="h-4 w-4 text-primary" />
              <h2 class="text-sm font-semibold text-foreground">搜索</h2>
            </div>
            <span class="hidden text-xs text-muted-foreground md:inline">全站范围</span>
          </div>
          <div class="mt-3 flex items-center gap-2 md:mt-4 md:gap-3">
            <input v-model="searchQuery" placeholder="搜索关键字"
              class="flex-1 rounded-xl border border-border px-3 py-2 text-sm" />
            <button class="rounded-xl border border-border px-3 py-2 text-xs text-muted-foreground"
              :disabled="searchLoading" @click="search">
              搜索
            </button>
          </div>
        </div>

        <div ref="listContainer" class="flex-1 overflow-y-auto scrollbar-thin">
          <div v-if="searchQuery" class="space-y-3">
            <ArticleCard v-for="item in searchResults" :key="item.id" :article="item" @open="onOpenArticle" />
            <EmptyState v-if="!searchResults.length && !searchLoading" title="暂无搜索结果" />
          </div>

          <div v-else class="space-y-4">
            <!-- 移动端热点事件（可折叠，默认展开） -->
            <details open class="rounded-2xl border border-border bg-card md:hidden">
              <summary class="flex cursor-pointer items-center gap-2 p-4 text-sm font-semibold text-foreground">
                <Sparkles class="h-4 w-4 text-primary" />
                热点事件
                <span class="ml-auto text-xs font-normal text-muted-foreground">
                  {{ hotLoading ? '加载中...' : `Top ${hotEvents.length}` }}
                </span>
              </summary>
              <ul class="border-t border-border p-3 space-y-2">
                <!-- 加载中 -->
                <li v-if="hotLoading" class="text-center text-sm text-muted-foreground py-4">
                  加载中...
                </li>
                <!-- 有数据时循环显示 -->
                <template v-else-if="hotEvents.length">
                  <li v-for="(item, index) in hotEvents" :key="`mobile-hot-${index}`"
                    class="flex items-center justify-between rounded-xl py-2 text-xs">
                    <div class="flex items-center gap-2">
                      <span class="h-6 w-6 flex-shrink-0 rounded-full bg-muted text-center leading-6 text-foreground">
                        {{ index + 1 }}
                      </span>
                      <span class="line-clamp-2 text-sm text-foreground">{{ item.event }}</span>
                    </div>
                    <button class="ml-2 flex-shrink-0 rounded-lg border border-border px-2 py-1 text-[11px]"
                      :class="item.isSubscribed ? 'bg-muted text-muted-foreground' : 'text-muted-foreground'"
                      :disabled="item.isSubscribed" @click="onSubscribeHot(item)">
                      {{ item.isSubscribed ? '✓' : '订阅' }}
                    </button>
                  </li>
                </template>
                <!-- 无数据 -->
                <li v-else class="text-center text-sm text-muted-foreground py-4">
                  暂无热点事件
                </li>
              </ul>
            </details>

            <!-- RSS 源分类 -->
            <div class="rounded-2xl border border-border bg-card p-3 md:p-4">
              <h3 class="text-sm font-semibold text-foreground">RSS 源</h3>
              <div class="mt-3 flex flex-wrap gap-2">
                <button v-for="item in categories" :key="item.value"
                  class="rounded-full border border-border px-3 py-1 text-xs"
                  :class="activeCategory === item.value ? 'bg-primary text-primary-foreground' : 'bg-card'"
                  @click="activeCategory = item.value">
                  {{ item.label }}
                </button>
              </div>
            </div>

            <!-- RSS 源卡片网格 -->
            <div class="grid grid-cols-1 gap-4 md:grid-cols-1 xl:grid-cols-2">
              <RssSourceCard v-for="source in sources" :key="source.id" :source="source"
                :onToggleSubscribe="onToggleSubscribe" :onPreview="onPreview" />
            </div>

            <LoadingState v-if="sourcesLoading && !sources.length" />
            <div ref="sentinel" class="h-6" />
            <div v-if="sourcesLoading && sources.length" class="text-center text-xs text-muted-foreground">
              加载更多 RSS 源...
            </div>
            <div v-if="last && sources.length" class="text-center text-xs text-muted-foreground">
              已经到底了
            </div>
          </div>
        </div>
      </template>
    </section>
  </div>

  <SourcePreviewDialog :open="previewOpen" :source="previewSource" :onClose="() => (previewOpen = false)"
    :onOpenArticle="onOpenArticle" />
</template>
