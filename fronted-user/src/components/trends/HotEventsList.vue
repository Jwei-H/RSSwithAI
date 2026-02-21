<script setup lang="ts">
import type { HotEvent } from '../../types'
import { Flame } from 'lucide-vue-next'

defineProps<{
  items: HotEvent[]
  onSubscribe: (event: HotEvent) => void
}>()
</script>

<template>
  <div class="rounded-2xl border border-border bg-card p-3">
    <div class="flex items-center justify-between">
      <div class="flex items-center gap-2">
        <Flame class="h-4 w-4 text-primary" />
        <h3 class="text-sm font-semibold text-foreground">热点事件</h3>
      </div>
      <span class="text-xs text-muted-foreground">Top 10</span>
    </div>
    <ul class="mt-3 max-h-96 space-y-2 overflow-y-auto pr-1 scrollbar-thin">
      <li
        v-for="(item, index) in items"
        :key="`${item.event}-${index}`"
        class="group flex w-full items-center justify-between rounded-xl py-2 text-xs text-muted-foreground hover:bg-muted"
      >
        <div class="flex items-center gap-2">
          <span class="h-6 w-6 rounded-full bg-muted text-center leading-6 text-foreground">
            {{ index + 1 }}
          </span>
          <span class="line-clamp-3 text-sm text-foreground" :title="item.event">{{ item.event }}</span>
        </div>
        <button
          class="rounded-lg border border-border px-2 py-1 text-[11px] transition opacity-0 group-hover:opacity-100"
          :class="item.isSubscribed ? 'bg-muted text-muted-foreground' : 'text-muted-foreground hover:bg-card'"
          :disabled="item.isSubscribed"
          @click="onSubscribe(item)"
        >
          <svg
            v-if="item.isSubscribed"
            xmlns="http://www.w3.org/2000/svg"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
            class="h-3.5 w-3.5"
          >
            <path d="M20 6 9 17l-5-5" />
          </svg>
          <span v-else>订阅</span>
        </button>
      </li>
    </ul>
  </div>
</template>
