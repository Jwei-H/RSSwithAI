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

const initChartIfNeeded = () => {
  if (!chartEl.value) return
  if (chart && chart.getDom() !== chartEl.value) {
    chart.dispose()
    chart = null
    resizeObserver?.disconnect()
    resizeObserver = null
  }
  if (chart) return
  chart = echarts.init(chartEl.value)
  resizeObserver = new ResizeObserver(() => chart?.resize())
  resizeObserver.observe(chartEl.value)
}

const render = () => {
  if (!chart) return
  if (!props.data.length) {
    chart.clear()
    return
  }
  const normalizedData = props.data
    .filter((item) => item.text && item.value > 0)
    .map((item) => ({ name: item.text, value: item.value }))
  if (!normalizedData.length) {
    chart.clear()
    return
  }
  const fixedSlots = [10, 9, 8, 7, 6, 5, 4, 3, 3, 2, 2, 1]
  const smoothed = [...normalizedData]
    .sort((a, b) => b.value - a.value)
    .slice(0, fixedSlots.length)
    .map((item, index) => ({
      ...item,
      value: fixedSlots[index]
    }))
  chart.setOption({
    series: [
      {
        type: 'wordCloud',
        left: 0,
        right: 0,
        width: '100%',
        height: '100%',
        gridSize: 4,
        sizeRange: [12, 26],
        rotationRange: [0, 0],
        rotationStep: 0,
        textStyle: {
          fontStyle: 'normal',
          color: () => {
            const colors = ['#2563eb', '#7c3aed', '#0891b2', '#f97316', '#16a34a']
            return colors[Math.floor(Math.random() * colors.length)]
          }
        },
        data: smoothed
      }
    ]
  })
}

onMounted(async () => {
  await nextTick()
  initChartIfNeeded()
  render()
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
    initChartIfNeeded()
    render()
  }
)
</script>

<template>
  <div class="rounded-2xl border border-border bg-card p-4">
    <h4 class="text-sm font-semibold text-foreground">词云</h4>
    <div v-if="loading" class="mt-4 text-xs text-muted-foreground">加载中...</div>
    <div v-else-if="!data.length" class="mt-4 text-xs text-muted-foreground">暂无词云</div>
    <div v-else ref="chartEl" class="mt-4 h-24 w-full" />
  </div>
</template>
