<script setup lang="ts">
import type { HotEvent } from '../../types'

defineProps<{
  items: HotEvent[]
  onSubscribe: (event: HotEvent) => void
}>()
</script>

<template>
  <div class="rounded-2xl border border-border bg-card p-4">
    <div class="flex items-center justify-between">
      <h3 class="text-sm font-semibold text-foreground">热点事件</h3>
      <span class="text-xs text-muted-foreground">Top 10</span>
    </div>
    <ul class="mt-4 max-h-64 space-y-2 overflow-y-auto pr-1 scrollbar-thin">
      <li
        v-for="(item, index) in items"
        :key="`${item.event}-${index}`"
        class="group flex items-center justify-between rounded-xl px-2 py-2 text-xs text-muted-foreground hover:bg-muted"
      >
        <div class="flex items-center gap-2">
          <span class="h-6 w-6 rounded-full bg-muted text-center leading-6 text-foreground">
            {{ index + 1 }}
          </span>
          <span class="line-clamp-2 text-foreground" :title="item.event">{{ item.event }}</span>
        </div>
        <button
          class="opacity-0 transition group-hover:opacity-100 rounded-lg border border-border px-2 py-1 text-[11px] text-muted-foreground hover:bg-card"
          @click="onSubscribe(item)"
        >
          订阅
        </button>
      </li>
    </ul>
  </div>
</template>
