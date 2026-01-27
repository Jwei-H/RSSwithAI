<script setup lang="ts">
import type { ArticleFeed } from '../../types'
import { unescapeUrl } from '../../utils/text'
import { rewriteUrl } from '../../utils/url-rewrites'
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
    class="group flex cursor-pointer gap-3 rounded-2xl border border-border bg-card p-3 transition hover:shadow md:gap-4 md:p-4"
    @mouseenter="$emit('hover', article.id)" @mouseleave="$emit('leave')" @click="$emit('open', article.id)">
    <!-- 移动端隐藏封面 -->
    <div v-if="article.coverImage"
      class="hidden h-16 w-24 shrink-0 overflow-hidden rounded-xl border border-border md:block">
      <img :src="rewriteUrl(unescapeUrl(article.coverImage))" :alt="article.title" class="h-full w-full object-cover"
        loading="lazy" referrerpolicy="no-referrer" />
    </div>
    <div class="flex min-w-0 flex-1 flex-col gap-1 md:gap-2">
      <div class="flex items-start justify-between gap-2 md:gap-4">
        <h3 class="line-clamp-2 text-sm font-semibold text-foreground">
          {{ article.title }}
        </h3>
        <span class="flex-shrink-0 text-xs text-muted-foreground">{{ formatRelativeTime(article.pubDate) }}</span>
      </div>
      <div class="flex items-center justify-between text-xs text-muted-foreground">
        <span>{{ article.sourceName }}</span>
        <span v-if="article.wordCount">{{ article.wordCount }} 字</span>
      </div>
    </div>
  </article>
</template>
