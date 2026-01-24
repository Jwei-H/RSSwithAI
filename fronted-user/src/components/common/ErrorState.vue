<script setup lang="ts">
defineProps<{ title: string; onRetry?: () => void }>()
</script>

<template>
  <div class="flex flex-col items-center justify-center gap-3 rounded-2xl border border-dashed border-border bg-card/40 p-6 text-center">
    <p class="text-sm font-medium text-rose-600">{{ title }}</p>
    <button
      v-if="onRetry"
      class="rounded-lg border border-border px-3 py-1 text-xs text-muted-foreground hover:bg-muted"
      @click="onRetry"
    >
      重试
    </button>
  </div>
</template>
