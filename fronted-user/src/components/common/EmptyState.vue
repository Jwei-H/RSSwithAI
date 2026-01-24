<script setup lang="ts">
defineProps<{ title: string; description?: string }>()
</script>

<template>
  <div class="flex flex-col items-center justify-center gap-2 rounded-2xl border border-dashed border-border bg-card/40 p-6 text-center">
    <p class="text-sm font-medium text-foreground">{{ title }}</p>
    <p v-if="description" class="text-xs text-muted-foreground">{{ description }}</p>
  </div>
</template>
