<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
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
import { Search, Sparkles } from 'lucide-vue-next'

const ui = useUiStore()
const toast = useToastStore()

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
  try {
    const topic = await subscriptionApi.createTopic({ content: item.event })
    await subscriptionApi.create({ type: 'TOPIC', targetId: topic.id })
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
}

const onOpenArticle = (id: number) => {
  previewOpen.value = false
  ui.openDetail(id, listContainer.value)
}

watch(activeCategory, () => {
  sources.value = []
  page.value = 0
  last.value = false
  loadSources()
})

watch(searchQuery, () => {
  if (searchQuery.value.trim()) {
    search()
  }
})

onMounted(() => {
  loadHotEvents()
  loadSources()
})
</script>

<template>
  <div
    class="grid h-screen gap-6 px-6 py-6"
    :class="detailOpen ? 'grid-cols-[200px_1fr]' : 'grid-cols-[280px_1fr]'"
  >
    <section class="flex h-full flex-col gap-4 overflow-hidden">
      <div class="rounded-2xl border border-border bg-card p-4">
        <h2 class="text-sm font-semibold text-foreground">频道广场</h2>
        <p class="mt-2 text-xs text-muted-foreground">探索新 RSS 源与热点事件</p>
      </div>
      <div class="rounded-2xl border border-border bg-card p-4">
        <div class="flex items-center gap-2">
          <Sparkles class="h-4 w-4 text-primary" />
          <h3 class="text-sm font-semibold text-foreground">订阅主题</h3>
        </div>
        <input
          v-model="topicInput"
          class="mt-3 w-full rounded-xl border border-border px-3 py-2 text-sm"
          placeholder="输入 30 字以内主题"
        />
        <button
          class="mt-3 w-full rounded-xl bg-primary px-3 py-2 text-sm text-primary-foreground"
          :disabled="topicLoading"
          @click="onCreateTopic"
        >
          {{ topicLoading ? '创建中...' : '创建并订阅' }}
        </button>
      </div>
      <HotEventsList :items="hotEvents" :onSubscribe="onSubscribeHot" />
    </section>

    <section class="flex h-full flex-col gap-4 overflow-hidden">
      <ArticleDetailPane
        v-if="detailOpen"
        :articleId="ui.detailArticleId"
        :onClose="ui.closeDetail"
        :onOpenArticle="(id) => ui.openDetail(id, listContainer.value)"
      />
      <template v-else>
        <div class="rounded-2xl border border-border bg-card p-4">
          <div class="flex items-center justify-between">
            <div class="flex items-center gap-2">
              <Search class="h-4 w-4 text-primary" />
              <h2 class="text-sm font-semibold text-foreground">搜索</h2>
            </div>
            <span class="text-xs text-muted-foreground">全站范围</span>
          </div>
          <div class="mt-4 flex items-center gap-3">
            <input
              v-model="searchQuery"
              placeholder="搜索关键字"
              class="flex-1 rounded-xl border border-border px-3 py-2 text-sm"
            />
            <button
              class="rounded-xl border border-border px-3 py-2 text-xs text-muted-foreground"
              :disabled="searchLoading"
              @click="search"
            >
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
            <div class="rounded-2xl border border-border bg-card p-4">
              <h3 class="text-sm font-semibold text-foreground">RSS 源分类</h3>
              <div class="mt-3 flex flex-wrap gap-2">
                <button
                  v-for="item in categories"
                  :key="item.value"
                  class="rounded-full border border-border px-3 py-1 text-xs"
                  :class="activeCategory === item.value ? 'bg-primary text-primary-foreground' : 'bg-card'"
                  @click="activeCategory = item.value"
                >
                  {{ item.label }}
                </button>
              </div>
            </div>

            <div class="grid grid-cols-1 gap-4 xl:grid-cols-2">
              <RssSourceCard
                v-for="source in sources"
                :key="source.id"
                :source="source"
                :onToggleSubscribe="onToggleSubscribe"
                :onPreview="onPreview"
              />
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

  <SourcePreviewDialog
    :open="previewOpen"
    :source="previewSource"
    :onClose="() => (previewOpen = false)"
    :onOpenArticle="onOpenArticle"
  />
</template>

