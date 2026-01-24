<script setup lang="ts">
import type { ArticleExtra } from '../../types'
import { formatOverview } from '../../utils/text'

const props = defineProps<{
  extra: ArticleExtra | null
  loading: boolean
  error?: string | null
}>()
</script>

<template>
  <div class="flex flex-col gap-4 rounded-2xl border border-border bg-card p-4">
    <h4 class="text-sm font-semibold text-foreground">精华速览</h4>
    <div v-if="loading" class="text-xs text-muted-foreground">加载中...</div>
    <div v-else-if="error" class="text-xs text-rose-600">{{ error }}</div>
    <p
      v-else-if="extra"
      class="text-[15px] leading-7 text-muted-foreground"
      v-html="formatOverview(extra.overview)"
    />
    <p v-else class="text-xs text-muted-foreground">悬停文章以查看 AI 预览</p>

    <div class="border-t border-border pt-3">
      <h5 class="text-xs font-semibold text-foreground">关键信息</h5>
      <ul v-if="extra?.keyInformation?.length" class="mt-2 list-decimal pl-4 text-[15px] leading-7 text-muted-foreground">
        <li v-for="(item, index) in extra.keyInformation" :key="`${item}-${index}`">{{ item }}</li>
      </ul>
      <p v-else class="mt-2 text-xs text-muted-foreground">暂无关键信息</p>
    </div>
  </div>
</template>
