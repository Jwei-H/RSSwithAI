<script setup lang="ts">
import { ref, watch } from 'vue'
import type { ArticleFeed, RssSource } from '../../types'
import { feedApi } from '../../services/frontApi'
import ArticleCard from '../articles/ArticleCard.vue'
import LoadingState from '../common/LoadingState.vue'
import EmptyState from '../common/EmptyState.vue'
import { useInfiniteScroll } from '../../composables/useInfiniteScroll'

const props = defineProps<{
  source: RssSource | null
  open: boolean
  onClose: () => void
  onOpenArticle: (id: number) => void
}>()

const list = ref<ArticleFeed[]>([])
const page = ref(0)
const loading = ref(false)
const last = ref(false)
const container = ref<HTMLElement | null>(null)

const loadMore = async () => {
  if (!props.source || loading.value || last.value) return
  loading.value = true
  try {
    const res = await feedApi.bySource(props.source.id, page.value, 10)
    list.value.push(...res.content)
    last.value = res.last
    page.value += 1
  } finally {
    loading.value = false
  }
}

const { sentinel } = useInfiniteScroll(loadMore, container)

const loadedSourceId = ref<number | null>(null)

watch(
  [() => props.open, () => props.source],
  ([isOpen, newSource]) => {
    if (isOpen && newSource) {
      // 如果源改变了（与上次加载的不同），或者列表为空，则加载
      if (newSource.id !== loadedSourceId.value) {
        list.value = []
        page.value = 0
        last.value = false
        loadMore().then(() => {
          loadedSourceId.value = newSource.id
        })
      } else if (list.value.length === 0) {
        loadMore()
      }
    }
  }
)
</script>

<template>
  <div v-show="open" class="fixed inset-0 z-40 flex items-center justify-center bg-black/40">
    <!-- 移动端全屏，桌面端居中弹窗 -->
    <div class="flex h-full w-full flex-col bg-background md:h-[80vh] md:w-[80vw] md:rounded-3xl md:shadow">
      <header class="flex items-center justify-between border-b border-border px-4 py-3 md:px-6 md:py-4">
        <div class="flex items-center gap-3">
          <div class="h-8 w-8 overflow-hidden rounded-xl border border-border bg-muted md:h-10 md:w-10">
            <img v-if="source?.icon" :src="source.icon" :alt="source?.name" class="h-full w-full object-cover" />
          </div>
          <div>
            <p class="text-sm font-semibold text-foreground">{{ source?.name }}</p>
            <p class="text-xs text-muted-foreground">{{ source?.category }}</p>
          </div>
        </div>
        <button class="text-xs text-muted-foreground hover:text-foreground" @click="onClose">关闭</button>
      </header>
      <div ref="container" class="flex-1 overflow-y-auto p-3 scrollbar-thin md:p-6">
        <div v-if="loading && !list.length" class="space-y-3">
          <LoadingState />
        </div>
        <div v-else-if="!list.length" class="space-y-3">
          <EmptyState title="暂无文章" description="此 RSS 源暂未抓取到内容" />
        </div>
        <div class="space-y-3">
          <ArticleCard v-for="item in list" :key="item.id" :article="item" @open="onOpenArticle" />
        </div>
        <div ref="sentinel" class="h-6" />
        <div v-if="loading && list.length" class="mt-4 text-xs text-muted-foreground">加载更多中...</div>
        <div v-if="last && list.length" class="mt-4 text-center text-xs text-muted-foreground">没有更多了</div>
      </div>
    </div>
  </div>
</template>
