<script setup lang="ts">
import { X } from 'lucide-vue-next'

interface Props {
  visible: boolean
  title: string
  width?: string
}

const props = withDefaults(defineProps<Props>(), {
  width: '600px'
})

const emit = defineEmits<{
  'update:visible': [value: boolean]
  'confirm': []
}>()

const handleClose = () => {
  emit('update:visible', false)
}

const handleConfirm = () => {
  emit('confirm')
}
</script>

<template>
  <Teleport to="body">
    <Transition name="dialog">
      <div
        v-if="visible"
        class="fixed inset-0 z-50 flex items-center justify-center bg-black/50"
        @click.self="handleClose"
      >
        <div
          :style="{ width }"
          class="bg-white rounded-lg shadow-xl max-h-[90vh] flex flex-col"
        >
          <!-- 标题栏 -->
          <div class="flex items-center justify-between px-6 py-4 border-b border-gray-200">
            <h3 class="text-lg font-semibold text-gray-900">{{ title }}</h3>
            <button
              @click="handleClose"
              class="p-1 rounded-lg hover:bg-gray-100 transition-colors"
            >
              <X class="w-5 h-5" />
            </button>
          </div>

          <!-- 内容区域 -->
          <div class="flex-1 overflow-y-auto px-6 py-4">
            <slot />
          </div>

          <!-- 操作栏 -->
          <div class="flex items-center justify-end space-x-3 px-6 py-4 border-t border-gray-200">
            <button
              @click="handleClose"
              class="px-4 py-2 text-gray-700 border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors"
            >
              取消
            </button>
            <button
              @click="handleConfirm"
              class="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
            >
              确定
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.dialog-enter-active,
.dialog-leave-active {
  transition: opacity 0.3s ease;
}

.dialog-enter-from,
.dialog-leave-to {
  opacity: 0;
}

.dialog-enter-active .bg-white,
.dialog-leave-active .bg-white {
  transition: transform 0.3s ease;
}

.dialog-enter-from .bg-white,
.dialog-leave-to .bg-white {
  transform: scale(0.95);
}
</style>
