<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  type?: 'success' | 'warning' | 'error' | 'info' | 'default'
}

const props = withDefaults(defineProps<Props>(), {
  type: 'default'
})

const classes = computed(() => {
  const baseClasses = 'inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium'
  
  switch (props.type) {
    case 'success':
      return `${baseClasses} bg-green-100 text-green-800`
    case 'warning':
      return `${baseClasses} bg-yellow-100 text-yellow-800`
    case 'error':
      return `${baseClasses} bg-red-100 text-red-800`
    case 'info':
      return `${baseClasses} bg-blue-100 text-blue-800`
    default:
      return `${baseClasses} bg-gray-100 text-gray-800`
  }
})
</script>

<template>
  <span :class="classes">
    <slot />
  </span>
</template>
