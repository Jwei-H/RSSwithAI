<script setup lang="ts">
interface Props {
  title: string
  value: string | number
  type?: 'default' | 'primary' | 'success' | 'warning' | 'error'
}

const props = withDefaults(defineProps<Props>(), {
  type: 'default'
})

const colorClasses = {
  default: 'border-gray-200',
  primary: 'border-blue-200 bg-blue-50',
  success: 'border-green-200 bg-green-50',
  warning: 'border-yellow-200 bg-yellow-50',
  error: 'border-red-200 bg-red-50'
}

const valueColorClasses = {
  default: 'text-gray-900',
  primary: 'text-blue-600',
  success: 'text-green-600',
  warning: 'text-yellow-600',
  error: 'text-red-600'
}
</script>

<template>
  <div :class="['bg-white rounded-lg border-2 p-6 transition-colors', colorClasses[type]]">
    <div class="text-sm font-medium text-gray-600 mb-1">{{ title }}</div>
    <div :class="['text-3xl font-bold', valueColorClasses[type]]">{{ value }}</div>
  </div>
</template>
