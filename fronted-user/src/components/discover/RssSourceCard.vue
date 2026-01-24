<script setup lang="ts">
import { onMounted, ref } from 'vue'
import type { ArticleFeed, RssSource } from '../../types'
import { feedApi } from '../../services/frontApi'
import { formatRelativeTime } from '../../utils/time'

const props = defineProps<{
  source: RssSource
  onToggleSubscribe: (source: RssSource) => void
  onPreview: (source: RssSource) => void
}>()

const recent = ref<ArticleFeed[]>([])
const loading = ref(false)

const loadRecent = async () => {
  loading.value = true
  try {
    const res = await feedApi.bySource(props.source.id, 0, 3)
    recent.value = res.content
  } finally {
    loading.value = false
  }
}

onMounted(loadRecent)
</script>

<template>
  <div class="rounded-2xl border border-border bg-card p-4 shadow-sm">
    <div class="flex items-start justify-between gap-3">
      <div class="flex items-center gap-3">
        <div class="h-10 w-10 overflow-hidden rounded-xl border border-border bg-muted">
          <img v-if="source.icon" :src="source.icon" :alt="source.name" class="h-full w-full object-cover" />
        </div>
        <div>
          <p class="text-sm font-semibold text-foreground">{{ source.name }}</p>
          <p class="text-xs text-muted-foreground">{{ source.category }}</p>
        </div>
      </div>
      <button
        class="rounded-lg border border-border px-3 py-1 text-xs"
        :class="source.isSubscribed ? 'bg-muted text-muted-foreground' : 'bg-primary text-primary-foreground'"
        @click="onToggleSubscribe(source)"
      >
        {{ source.isSubscribed ? '已订阅' : '订阅' }}
      </button>
    </div>

    <div class="mt-4">
      <div class="flex items-center justify-between">
        <span class="text-xs text-muted-foreground">最新文章</span>
        <button class="text-xs text-primary hover:underline" @click="onPreview(source)">预览</button>
      </div>
      <div v-if="loading" class="mt-3 text-xs text-muted-foreground">加载中...</div>
      <ul v-else class="mt-3 space-y-2">
        <li v-for="item in recent" :key="item.id" class="flex items-center justify-between gap-3">
          <span class="line-clamp-1 text-xs text-foreground">{{ item.title }}</span>
          <span class="text-[11px] text-muted-foreground">{{ formatRelativeTime(item.pubDate) }}</span>
        </li>
        <li v-if="!recent.length" class="text-xs text-muted-foreground">暂无文章</li>
      </ul>
    </div>
  </div>
</template>
