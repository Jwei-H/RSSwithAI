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
  <div
    class="grid h-screen gap-6 px-6 py-6"
    :class="props.showRight ? 'grid-cols-[280px_1fr_320px]' : 'grid-cols-[280px_1fr]'"
  >
    <section class="flex h-full flex-col gap-4 overflow-hidden">
      <slot name="sidebar" />
    </section>
    <section class="flex h-full flex-col gap-4 overflow-hidden">
      <slot name="main" />
    </section>
    <section v-if="props.showRight" class="flex h-full flex-col gap-4 overflow-hidden">
      <slot name="right" />
    </section>
  </div>
</template>
