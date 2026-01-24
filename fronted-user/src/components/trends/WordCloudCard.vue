<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import 'echarts-wordcloud'

const props = defineProps<{
  data: { text: string; value: number }[]
  loading: boolean
}>()

const chartEl = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null
let resizeObserver: ResizeObserver | null = null

const render = () => {
  if (!chart) return
  if (!props.data.length) {
    chart.clear()
    return
  }
  chart.setOption({
    series: [
      {
        type: 'wordCloud',
        shape: 'circle',
        gridSize: 6,
        sizeRange: [12, 36],
        rotationRange: [-10, 10],
        textStyle: {
          color: () => {
            const colors = ['#2563eb', '#7c3aed', '#0891b2', '#f97316', '#16a34a']
            return colors[Math.floor(Math.random() * colors.length)]
          }
        },
        data: props.data
      }
    ]
  })
}

onMounted(async () => {
  await nextTick()
  if (chartEl.value) {
    chart = echarts.init(chartEl.value)
    render()
    resizeObserver = new ResizeObserver(() => chart?.resize())
    resizeObserver.observe(chartEl.value)
  }
})

onUnmounted(() => {
  resizeObserver?.disconnect()
  chart?.dispose()
  chart = null
})

watch(
  () => props.data,
  async () => {
    await nextTick()
    render()
  }
)
</script>

<template>
  <div class="rounded-2xl border border-border bg-card p-4">
    <div class="flex items-center justify-between">
      <h4 class="text-sm font-semibold text-foreground">词云</h4>
      <span class="text-xs text-muted-foreground">与当前订阅联动</span>
    </div>
    <div v-if="loading" class="mt-4 text-xs text-muted-foreground">加载中...</div>
    <div v-else-if="!data.length" class="mt-4 text-xs text-muted-foreground">暂无词云</div>
    <div v-else ref="chartEl" class="mt-4 h-48 w-full" />
  </div>
</template>
