<script setup lang="ts">
import { useToastStore } from '../../stores/toast'

const toast = useToastStore()
</script>

<template>
  <div class="pointer-events-none fixed left-1/2 top-6 z-[100] flex -translate-x-1/2 flex-col gap-3">
    <div v-for="item in toast.state.toasts" :key="item.id"
      class="pointer-events-auto rounded-2xl border border-border bg-card px-4 py-3 text-sm shadow" :class="item.type === 'success'
          ? 'text-emerald-600'
          : item.type === 'error'
            ? 'text-rose-600'
            : 'text-foreground'
        ">
      {{ item.message }}
    </div>
  </div>
</template>
