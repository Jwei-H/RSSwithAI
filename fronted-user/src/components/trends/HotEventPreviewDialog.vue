<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import type { ArticleFeed, HotEvent, TopicEventTrackingNode, TopicEventTrackingResult } from '../../types'
import { topicEventTrackingApi, trendApi } from '../../services/frontApi'
import ArticleCard from '../articles/ArticleCard.vue'
import TopicEventTrackerCard from '../subscriptions/TopicEventTrackerCard.vue'
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

const eventTrackingResult = ref<TopicEventTrackingResult | null>(null)
const eventTrackingLoading = ref(false)
const eventTrackingStreaming = ref(false)
const eventTrackingError = ref<string | null>(null)
const eventTrackingRaw = ref('')
const eventTrackingStreamingNodes = ref<TopicEventTrackingNode[]>([])
let eventTrackingAbortController: AbortController | null = null

const loadMore = async () => {
  if (!props.eventItem || loading.value || last.value) return
  loading.value = true
  try {
    const items = await trendApi.hotEventArticles(props.eventItem.event, props.eventItem.topicId, cursor.value || undefined, 10)
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

const abortEventTrackingStream = () => {
  if (eventTrackingAbortController) {
    eventTrackingAbortController.abort()
    eventTrackingAbortController = null
  }
}

const resetEventTrackingState = () => {
  abortEventTrackingStream()
  eventTrackingResult.value = null
  eventTrackingLoading.value = false
  eventTrackingStreaming.value = false
  eventTrackingError.value = null
  eventTrackingRaw.value = ''
  eventTrackingStreamingNodes.value = []
}

const normalizeEventTrackingRaw = (raw: string) => {
  const cleaned = raw.replace(/```json|```/g, '').trim()
  const start = cleaned.indexOf('{')
  const end = cleaned.lastIndexOf('}')
  if (start < 0 || end <= start) return cleaned
  return cleaned.slice(start, end + 1)
}

const normalizeStreamingNode = (node: any): TopicEventTrackingNode | null => {
  const normalized = {
    date: typeof node?.date === 'string' ? node.date : '',
    progress: typeof node?.progress === 'string' ? node.progress : '',
    coverImage: typeof node?.coverImage === 'string' ? node.coverImage : null,
    articles: Array.isArray(node?.articles)
      ? node.articles
          .filter((article: any) => Number(article?.id) > 0 && typeof article?.title === 'string')
          .slice(0, 3)
          .map((article: any) => ({ id: Number(article.id), title: article.title }))
      : []
  }
  return normalized.date && normalized.progress && normalized.articles.length ? normalized : null
}

const parsePartialStreamingNodes = (raw: string): TopicEventTrackingNode[] => {
  const cleaned = raw.replace(/```json|```/g, '')
  const nodesIndex = cleaned.indexOf('"nodes"')
  const arrayStart = nodesIndex >= 0 ? cleaned.indexOf('[', nodesIndex) : -1
  if (arrayStart < 0) return []

  const nodes: TopicEventTrackingNode[] = []
  let depth = 0
  let start = -1
  let inString = false
  let escaped = false

  for (let i = arrayStart + 1; i < cleaned.length; i += 1) {
    const char = cleaned[i]
    if (inString) {
      if (escaped) {
        escaped = false
      } else if (char === '\\') {
        escaped = true
      } else if (char === '"') {
        inString = false
      }
      continue
    }

    if (char === '"') {
      inString = true
    } else if (char === '{') {
      if (depth === 0) start = i
      depth += 1
    } else if (char === '}') {
      depth -= 1
      if (depth === 0 && start >= 0) {
        try {
          const node = normalizeStreamingNode(JSON.parse(cleaned.slice(start, i + 1)))
          if (node) nodes.push(node)
        } catch {
          // ignore incomplete node
        }
        start = -1
      }
    } else if (char === ']' && depth === 0) {
      break
    }
  }
  return nodes
}

const parseStreamingNodes = (raw: string): TopicEventTrackingNode[] => {
  try {
    const parsed = JSON.parse(normalizeEventTrackingRaw(raw)) as { nodes?: unknown }
    if (!Array.isArray(parsed.nodes)) return []
    return parsed.nodes
      .map(normalizeStreamingNode)
      .filter((node): node is TopicEventTrackingNode => node !== null)
  } catch {
    return parsePartialStreamingNodes(raw)
  }
}

const loadEventTrackingLatest = async () => {
  const topicId = props.eventItem?.topicId
  if (!props.open || !topicId) {
    resetEventTrackingState()
    return
  }

  eventTrackingLoading.value = true
  eventTrackingError.value = null
  try {
    const response = await topicEventTrackingApi.latestByTopic(topicId)
    if (props.eventItem?.topicId !== topicId) return
    eventTrackingResult.value = response.status === 'SUCCESS' ? response.result : null
    if (response.status === 'INVALID') {
      eventTrackingError.value = response.message || '事件追踪结果解析失败'
    }
  } catch (error: any) {
    if (props.eventItem?.topicId === topicId) {
      eventTrackingError.value = error?.message || '事件追踪加载失败'
    }
  } finally {
    if (props.eventItem?.topicId === topicId) {
      eventTrackingLoading.value = false
    }
  }
}

const generateEventTracking = () => {
  const topicId = props.eventItem?.topicId
  if (!topicId || eventTrackingStreaming.value) return

  abortEventTrackingStream()
  eventTrackingRaw.value = ''
  eventTrackingStreamingNodes.value = []
  eventTrackingError.value = null
  eventTrackingStreaming.value = true
  eventTrackingAbortController = new AbortController()

  topicEventTrackingApi.generateTopicStream(
    topicId,
    {
      onChunk: ({ text }) => {
        if (props.eventItem?.topicId !== topicId || !text) return
        eventTrackingRaw.value += text
        eventTrackingStreamingNodes.value = parseStreamingNodes(eventTrackingRaw.value)
      },
      onDone: ({ saved, message }) => {
        if (props.eventItem?.topicId !== topicId) return
        eventTrackingStreaming.value = false
        if (saved) {
          void loadEventTrackingLatest().then(() => {
            if (props.eventItem?.topicId === topicId) {
              eventTrackingRaw.value = ''
              eventTrackingStreamingNodes.value = []
            }
          })
        } else {
          eventTrackingError.value = message || '生成失败，请稍后重试'
        }
      },
      onError: ({ message }) => {
        if (props.eventItem?.topicId !== topicId) return
        eventTrackingError.value = message || '生成失败，请稍后重试'
      }
    },
    eventTrackingAbortController.signal
  )
    .catch((error: any) => {
      if (error?.name === 'AbortError') return
      if (props.eventItem?.topicId === topicId) {
        eventTrackingError.value = error?.message || '网络中断，请稍后重试'
      }
    })
    .finally(() => {
      if (props.eventItem?.topicId === topicId) {
        eventTrackingStreaming.value = false
        eventTrackingAbortController = null
      }
    })
}

const loadedEventKey = ref<string>('')
const currentEventKey = computed(() => {
  if (!props.eventItem) return ''
  return `${props.eventItem.topicId ?? 'no-topic'}:${props.eventItem.event}`
})

watch(
  [() => props.open, currentEventKey],
  ([isOpen, eventKey]) => {
    if (isOpen && props.eventItem && eventKey) {
      if (eventKey !== loadedEventKey.value) {
        list.value = []
        cursor.value = null
        last.value = false
        loadMore().then(() => {
          loadedEventKey.value = eventKey
        })
      } else if (list.value.length === 0) {
        loadMore()
      }
      loadEventTrackingLatest()
    } else if (!isOpen) {
      abortEventTrackingStream()
      eventTrackingStreaming.value = false
    }
  }
)

onBeforeUnmount(() => {
  abortEventTrackingStream()
})

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
        <TopicEventTrackerCard
          v-if="eventItem?.topicId"
          class="mb-3 md:mb-4"
          :topicName="eventItem.event"
          :result="eventTrackingResult"
          :streamingNodes="eventTrackingStreamingNodes"
          :rawPreview="eventTrackingRaw"
          :loading="eventTrackingLoading"
          :streaming="eventTrackingStreaming"
          :error="eventTrackingError"
          :collapsible="true"
          :defaultCollapsed="true"
          @generate="generateEventTracking"
          @openArticle="onOpenArticle"
        />

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
