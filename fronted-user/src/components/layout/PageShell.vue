<script setup lang="ts">
const props = withDefaults(
  defineProps<{
    showRight?: boolean
  }>(),
  {
    showRight: true
  }
)
</script>

<template>
  <div class="flex h-screen flex-col gap-4 px-4 py-4 md:grid md:gap-6 md:px-6 md:py-6"
    :class="props.showRight ? 'md:grid-cols-[280px_1fr_320px]' : 'md:grid-cols-[280px_1fr]'">
    <!-- 桌面端侧边栏 -->
    <section class="hidden h-full flex-col gap-4 overflow-hidden md:flex">
      <slot name="sidebar" />
    </section>
    <!-- 主内容区域 -->
    <section class="flex flex-1 flex-col gap-4 overflow-y-auto">
      <slot name="main" />
    </section>
    <!-- 桌面端右侧面板 -->
    <section v-if="props.showRight" class="hidden h-full flex-col gap-4 overflow-hidden md:flex">
      <slot name="right" />
    </section>
  </div>
</template>
