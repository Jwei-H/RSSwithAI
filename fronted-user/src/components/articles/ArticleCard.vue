<script setup lang="ts">
import type { ArticleFeed } from '../../types'
import { unescapeUrl } from '../../utils/text'
import { formatRelativeTime } from '../../utils/time'

defineProps<{
  article: ArticleFeed
}>()

defineEmits<{
  (e: 'open', id: number): void
  (e: 'hover', id: number): void
  (e: 'leave'): void
}>()
</script>

<template>
  <article
    class="group flex cursor-pointer gap-4 rounded-2xl border border-border bg-card p-4 transition hover:shadow"
    @mouseenter="$emit('hover', article.id)"
    @mouseleave="$emit('leave')"
    @click="$emit('open', article.id)"
  >
    <div v-if="article.coverImage" class="h-16 w-24 shrink-0 overflow-hidden rounded-xl border border-border">
      <img :src="unescapeUrl(article.coverImage)" :alt="article.title" class="h-full w-full object-cover" loading="lazy" referrerpolicy="no-referrer" />
    </div>
    <div class="flex min-w-0 flex-1 flex-col gap-2">
      <div class="flex items-start justify-between gap-4">
        <h3 class="line-clamp-2 text-sm font-semibold text-foreground">
          {{ article.title }}
        </h3>
        <span class="text-xs text-muted-foreground">{{ formatRelativeTime(article.pubDate) }}</span>
      </div>
      <p class="text-xs text-muted-foreground">{{ article.sourceName }}</p>
      <div class="flex items-center justify-between text-xs text-muted-foreground">
        <span>来源于 {{ article.sourceName }}</span>
        <span v-if="article.wordCount">{{ article.wordCount }} 字</span>
      </div>
    </div>
  </article>
</template>
