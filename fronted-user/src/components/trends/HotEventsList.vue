<script setup lang="ts">
import { computed, ref } from 'vue'
import type { HotEvent } from '../../types'
import { ChevronRight, Flame } from 'lucide-vue-next'

const DEFAULT_VISIBLE_COUNT = 15

const props = defineProps<{
  items: HotEvent[]
  onMore: (event: HotEvent) => void
}>()

const showAll = ref(false)

const canToggleAll = computed(() => props.items.length > DEFAULT_VISIBLE_COUNT)
const visibleCount = computed(() =>
  showAll.value ? props.items.length : Math.min(DEFAULT_VISIBLE_COUNT, props.items.length)
)
const visibleItems = computed(() => props.items.slice(0, visibleCount.value))
const topLabel = computed(() => {
  if (!canToggleAll.value) return `Top ${visibleCount.value}`
  return showAll.value ? `Top ${visibleCount.value}` : `Top ${visibleCount.value}`
})

const onToggleTop = () => {
  if (!canToggleAll.value) return
  showAll.value = !showAll.value
}
</script>

<template>
  <div class="rounded-2xl border border-border bg-card p-3">
    <div class="flex items-center justify-between">
      <div class="flex items-center gap-2">
        <Flame class="h-4 w-4 text-primary" />
        <h3 class="text-sm font-semibold text-foreground">热点事件</h3>
      </div>
      <button
        class="rounded-lg border px-2 py-1 text-xs text-muted-foreground transition"
        :class="
          canToggleAll
            ? 'cursor-pointer border-border bg-muted/40 hover:bg-muted'
            : 'cursor-default border-transparent'
        "
        :title="canToggleAll ? '点击切换显示数量' : undefined"
        @click="onToggleTop"
      >
        {{ topLabel }}
      </button>
    </div>
    <ul class="mt-3 max-h-96 space-y-2 overflow-y-auto pr-1 scrollbar-thin">
      <li
        v-for="(item, index) in visibleItems"
        :key="`${item.event}-${index}`"
        class="group flex w-full cursor-pointer items-center justify-between rounded-xl py-2 text-xs text-muted-foreground transition hover:bg-muted"
        @click="onMore(item)"
      >
        <div class="flex items-center gap-2">
          <span class="h-6 w-6 rounded-full bg-muted text-center leading-6 text-foreground">
            {{ index + 1 }}
          </span>
          <span class="line-clamp-3 text-sm text-foreground" :title="item.event">{{ item.event }}</span>
        </div>
        <ChevronRight class="h-4 w-4 flex-shrink-0 opacity-50 transition group-hover:opacity-100" />
      </li>
    </ul>
  </div>
</template>
