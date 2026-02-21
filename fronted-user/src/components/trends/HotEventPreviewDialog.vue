<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { ArticleFeed, HotEvent } from '../../types'
import { trendApi } from '../../services/frontApi'
import ArticleCard from '../articles/ArticleCard.vue'
import LoadingState from '../common/LoadingState.vue'
import EmptyState from '../common/EmptyState.vue'
import { useInfiniteScroll } from '../../composables/useInfiniteScroll'
import { BellPlus, Check } from 'lucide-vue-next'

const props = defineProps<{
  eventItem: HotEvent | null
  open: boolean
  onClose: () => void
  onOpenArticle: (id: number) => void
  onSubscribe: (event: HotEvent) => Promise<void> | void
}>()

const list = ref<ArticleFeed[]>([])
const cursor = ref<string | null>(null)
const loading = ref(false)
const last = ref(false)
const container = ref<HTMLElement | null>(null)

const loadMore = async () => {
  if (!props.eventItem || loading.value || last.value) return
  loading.value = true
  try {
    const items = await trendApi.hotEventArticles(props.eventItem.event, cursor.value || undefined, 10)
    list.value.push(...items)
    if (items.length < 10) {
      last.value = true
    }
    const lastItem = items[items.length - 1]
    if (lastItem) {
      cursor.value = `${lastItem.pubDate},${lastItem.id}`
    }
  } finally {
    loading.value = false
  }
}

const { sentinel } = useInfiniteScroll(loadMore, container)

const loadedEventKey = ref<string>('')

watch(
  [() => props.open, () => props.eventItem?.event],
  ([isOpen, event]) => {
    if (isOpen && event) {
      if (event !== loadedEventKey.value) {
        list.value = []
        cursor.value = null
        last.value = false
        loadMore().then(() => {
          loadedEventKey.value = event
        })
      } else if (list.value.length === 0) {
        loadMore()
      }
    }
  }
)

const subscribeDisabled = computed(() => !props.eventItem || props.eventItem.isSubscribed)

const onClickSubscribe = async () => {
  if (!props.eventItem || subscribeDisabled.value) return
  await props.onSubscribe(props.eventItem)
}
</script>

<template>
  <div v-show="open" class="fixed inset-0 z-40 flex items-center justify-center bg-black/40">
    <div class="flex h-full w-full flex-col bg-background md:h-[80vh] md:w-[80vw] md:rounded-3xl md:shadow">
      <header class="flex flex-col gap-3 border-b border-border px-4 py-3 md:flex-row md:items-center md:justify-between md:px-6 md:py-4">
        <div>
          <p class="text-sm font-semibold text-foreground">预览</p>
          <p class="mt-1 text-xs text-muted-foreground break-words md:mt-0 md:line-clamp-1" :title="eventItem?.event">{{ eventItem?.event }}</p>
        </div>
        <div class="flex w-full items-center gap-2 md:w-auto md:gap-3">
          <button
            class="inline-flex flex-1 items-center justify-center gap-1.5 rounded-lg border border-border px-3 py-1.5 text-xs md:flex-none"
            :class="subscribeDisabled ? 'bg-muted text-muted-foreground' : 'text-foreground hover:bg-muted'"
            :disabled="subscribeDisabled"
            @click="onClickSubscribe"
          >
            <Check v-if="eventItem?.isSubscribed" class="h-3.5 w-3.5" />
            <BellPlus v-else class="h-3.5 w-3.5" />
            <span>{{ eventItem?.isSubscribed ? '已订阅' : '订阅' }}</span>
          </button>
          <button
            class="inline-flex flex-1 items-center justify-center rounded-lg border border-border px-3 py-1.5 text-xs text-muted-foreground hover:text-foreground md:flex-none"
            @click="onClose"
          >
            关闭
          </button>
        </div>
      </header>
      <div ref="container" class="flex-1 overflow-y-auto p-3 scrollbar-thin md:p-6">
        <div v-if="loading && !list.length" class="space-y-3">
          <LoadingState />
        </div>
        <div v-else-if="!list.length" class="space-y-3">
          <EmptyState title="暂无文章" description="该热点事件暂未匹配到相关文章" />
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
