<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { AlertCircle, ChevronDown, ChevronUp, Clock3, FileText, Loader2, RefreshCw, Sparkles } from 'lucide-vue-next'
import CoverImage from '../common/CoverImage.vue'
import type { TopicEventTrackingNode, TopicEventTrackingResult } from '../../types'

const props = withDefaults(defineProps<{
  topicName: string
  result: TopicEventTrackingResult | null
  streamingNodes: TopicEventTrackingNode[]
  rawPreview: string
  loading: boolean
  streaming: boolean
  error: string | null
  collapsible?: boolean
  defaultCollapsed?: boolean
}>(), {
  collapsible: false,
  defaultCollapsed: false
})

const emit = defineEmits<{
  generate: []
  openArticle: [id: number]
}>()

const expanded = ref(false)
const collapsed = ref(props.defaultCollapsed)

watch(
  () => props.topicName,
  () => {
    collapsed.value = props.defaultCollapsed
    expanded.value = false
  }
)

const displayNodes = computed(() => {
  if (props.streaming && props.streamingNodes.length) return props.streamingNodes
  return props.result?.nodes ?? []
})

const visibleNodes = computed(() => {
  if (expanded.value) return displayNodes.value
  return displayNodes.value.slice(0, 3)
})

const generatedAtText = computed(() => {
  if (!props.result?.generatedAt) return ''
  const date = new Date(props.result.generatedAt)
  if (Number.isNaN(date.getTime())) return props.result.generatedAt
  return date.toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
})

const cleanedRawPreview = computed(() => {
  const cleaned = props.rawPreview.replace(/```json|```/g, '').trim()
  return cleaned.length > 600 ? `…${cleaned.slice(-600)}` : cleaned
})
const rawPreviewLength = computed(() => props.rawPreview.length)
const hasResult = computed(() => !!props.result)
const emptyResult = computed(() => hasResult.value && displayNodes.value.length === 0)
</script>

<template>
  <section class="rounded-3xl border border-primary/20 bg-gradient-to-br from-primary/10 via-card to-card p-4 shadow-sm md:p-5">
    <div class="flex items-start justify-between gap-3">
      <div class="min-w-0 flex-1">
        <div class="flex items-center gap-2 text-xs font-medium text-primary">
          <Sparkles class="h-4 w-4" />
          事件追踪
        </div>
        <h3 class="mt-1 line-clamp-2 text-base font-semibold text-foreground md:text-lg">
          {{ topicName }}
        </h3>
        <p class="mt-1 text-xs text-muted-foreground">
          基于相关报道生成结构化事件进展
          <span v-if="generatedAtText"> · {{ generatedAtText }}</span>
        </p>
      </div>
      <div class="flex shrink-0 items-center gap-2">
        <button
          v-if="collapsible"
          class="inline-flex items-center gap-1 rounded-full border border-border bg-card px-3 py-2 text-xs text-muted-foreground transition hover:bg-muted hover:text-foreground"
          @click="collapsed = !collapsed"
        >
          <ChevronDown v-if="collapsed" class="h-3.5 w-3.5" />
          <ChevronUp v-else class="h-3.5 w-3.5" />
          {{ collapsed ? '展开' : '收起' }}
        </button>
        <button
          class="inline-flex items-center gap-1.5 rounded-full border border-primary/30 bg-primary px-3 py-2 text-xs font-medium text-primary-foreground transition disabled:cursor-not-allowed disabled:opacity-60"
          :disabled="loading || streaming"
          @click="emit('generate')"
        >
          <Loader2 v-if="streaming" class="h-3.5 w-3.5 animate-spin" />
          <RefreshCw v-else-if="hasResult" class="h-3.5 w-3.5" />
          <Sparkles v-else class="h-3.5 w-3.5" />
          {{ hasResult ? '重新生成' : '追踪事件' }}
        </button>
      </div>
    </div>

    <template v-if="!collapsed">
    <div v-if="loading" class="mt-4 rounded-2xl border border-dashed border-border bg-background/50 p-4 text-center text-sm text-muted-foreground">
      加载事件追踪中...
    </div>

    <div v-else-if="error" class="mt-4 flex items-start gap-2 rounded-2xl border border-red-500/20 bg-red-500/10 p-3 text-sm text-red-500">
      <AlertCircle class="mt-0.5 h-4 w-4 shrink-0" />
      <span>{{ error }}</span>
    </div>

    <div v-if="streaming" class="mt-4 rounded-2xl border border-primary/20 bg-background/60 p-3">
      <div class="flex items-center justify-between gap-2 text-xs font-medium text-primary">
        <span class="inline-flex items-center gap-2">
          <Loader2 class="h-3.5 w-3.5 animate-spin" />
          AI 正在生成事件进展...
        </span>
      </div>
      <p v-if="cleanedRawPreview && !streamingNodes.length" class="mt-2 line-clamp-4 whitespace-pre-wrap text-xs leading-5 text-muted-foreground">
        {{ cleanedRawPreview }}
      </p>
    </div>

    <div v-if="!loading && !streaming && !hasResult" class="mt-4 rounded-2xl border border-dashed border-primary/20 bg-background/50 p-4 text-sm text-muted-foreground">
      还没有事件追踪结果，点击“追踪事件”后将从相关文章中提取关键进展。
    </div>

    <div v-if="emptyResult && !streaming" class="mt-4 rounded-2xl border border-dashed border-border bg-background/50 p-4 text-sm text-muted-foreground">
      当前相关文章较分散，暂未形成清晰事件线。
    </div>

    <div v-if="visibleNodes.length" class="mt-4 space-y-3">
      <article
        v-for="(node, index) in visibleNodes"
        :key="`${node.date}-${index}-${node.progress}`"
        class="relative rounded-2xl border border-border bg-background/70 p-3"
      >
        <span
          v-if="index > 0"
          class="pointer-events-none absolute -top-3 bottom-1/2 left-[56px] w-px bg-border"
        />
        <span
          v-if="index < visibleNodes.length - 1"
          class="pointer-events-none absolute -bottom-3 left-[56px] top-1/2 w-px bg-border"
        />
        <div class="relative z-10 flex items-center gap-3">
          <div class="relative flex h-12 w-[80px] shrink-0 flex-col items-center justify-center rounded-2xl border border-primary/20 bg-card px-2 text-center text-primary shadow-sm">
            <Clock3 class="mb-1 h-3.5 w-3.5 shrink-0" />
            <span class="whitespace-nowrap text-[11px] font-semibold leading-none">{{ node.date }}</span>
          </div>
          <div class="min-w-0 flex-1">
            <div class="flex items-center gap-3">
              <div class="min-w-0 flex-1">
                <h4 class="text-sm font-semibold leading-6 text-foreground">{{ node.progress }}</h4>
                <div class="mt-2 flex flex-wrap gap-2">
                  <button
                    v-for="article in node.articles"
                    :key="article.id"
                    class="inline-flex max-w-full items-center gap-1.5 rounded-full border border-border bg-card px-2.5 py-1 text-left text-[11px] text-muted-foreground transition hover:border-primary/40 hover:text-primary"
                    @click="emit('openArticle', article.id)"
                  >
                    <FileText class="h-3 w-3 shrink-0" />
                    <span class="truncate">{{ article.title }}</span>
                  </button>
                </div>
              </div>
              <CoverImage
                :src="node.coverImage"
                :alt="node.progress"
                container-class="hidden h-20 w-22 shrink-0 overflow-hidden rounded-xl border border-border sm:block"
              />
            </div>
          </div>
        </div>
      </article>
    </div>

    <button
      v-if="displayNodes.length > 3"
      class="mt-3 inline-flex items-center gap-1 rounded-full px-2 py-1 text-xs text-muted-foreground transition hover:bg-muted hover:text-foreground"
      @click="expanded = !expanded"
    >
      <ChevronUp v-if="expanded" class="h-3.5 w-3.5" />
      <ChevronDown v-else class="h-3.5 w-3.5" />
      {{ expanded ? '收起' : `展开全部 ${displayNodes.length} 条` }}
    </button>
    </template>
  </section>
</template>
