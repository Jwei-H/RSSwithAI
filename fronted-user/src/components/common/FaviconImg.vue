<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { extractDomain, getFaviconCandidates, getFaviconFallback } from '../../lib/utils'

const props = defineProps<{
  src: string | null | undefined
  alt?: string
  /** 用于计算候选列表和 fallback 的原始链接，优先于 src */
  link?: string | null
}>()

// 用于 fallback 文字头像的域名
const domain = computed(() => {
  const source = props.link || props.src || ''
  try {
    return new URL(source).hostname
  } catch {
    return source
  }
})

// 候选 URL 列表（基于 link，若无则从 src 推断）
const candidates = computed(() => {
  const base = props.link || props.src
  const list = getFaviconCandidates(base)
  // 若 src 是额外提供的（frontApi 预生成的第一个候选），且不在列表中，则插入首位
  if (props.src && list.length > 0 && !list.includes(props.src)) {
    return [props.src, ...list]
  }
  return list
})

// 当前尝试的索引
const tryIndex = ref(0)

// 当 candidates 变化时（切换订阅源），重置索引
watch(candidates, () => {
  tryIndex.value = 0
}, { immediate: false })

// 当前展示的 URL
const currentSrc = computed(() => candidates.value[tryIndex.value] ?? null)

// 是否已耗尽所有候选
const exhausted = computed(() => tryIndex.value >= candidates.value.length)

const fallback = computed(() => getFaviconFallback(domain.value))

const onError = () => {
  tryIndex.value += 1
}
</script>

<template>
  <img
    v-if="currentSrc && !exhausted"
    :key="currentSrc"
    :src="currentSrc"
    :alt="alt"
    class="h-full w-full object-cover"
    loading="lazy"
    referrerpolicy="no-referrer"
    @error="onError"
  />
  <div
    v-else
    class="flex h-full w-full items-center justify-center text-xs font-bold text-white select-none"
    :style="{ backgroundColor: fallback.bgColor }"
  >
    {{ fallback.letter }}
  </div>
</template>
