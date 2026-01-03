<script setup lang="ts">
import { computed } from 'vue'
import { ChevronDown } from 'lucide-vue-next'

interface Option {
  value: string | number
  label: string
}

interface Props {
  modelValue: string | number
  options: Option[]
  placeholder?: string
  disabled?: boolean
  required?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  placeholder: '请选择',
  disabled: false,
  required: false
})

const emit = defineEmits<{
  'update:modelValue': [value: string | number]
}>()

const handleChange = (event: Event) => {
  const target = event.target as HTMLSelectElement
  const value = target.value
  const option = props.options.find(o => String(o.value) === value)
  emit('update:modelValue', option ? option.value : value)
}
</script>

<template>
  <div class="relative">
    <select
      :value="modelValue"
      :disabled="disabled"
      :required="required"
      @change="handleChange"
      class="w-full appearance-none bg-white border border-gray-300 text-gray-900 text-sm rounded-lg focus:ring-blue-500 focus:border-blue-500 block w-full p-2.5 pr-8 disabled:bg-gray-100 disabled:cursor-not-allowed"
    >
      <option v-if="placeholder" value="" disabled selected>{{ placeholder }}</option>
      <option v-for="option in options" :key="option.value" :value="option.value">
        {{ option.label }}
      </option>
    </select>
    <div class="pointer-events-none absolute inset-y-0 right-0 flex items-center px-2 text-gray-700">
      <ChevronDown class="h-4 w-4" />
    </div>
  </div>
</template>
