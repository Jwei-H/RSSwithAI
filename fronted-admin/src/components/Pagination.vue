<script setup lang="ts">
import { ChevronLeft, ChevronRight } from 'lucide-vue-next'

interface Props {
  currentPage: number
  totalPages: number
  totalElements: number
  pageSize: number
}

const props = defineProps<Props>()

const emit = defineEmits<{
  'update:page': [page: number]
}>()

const handlePrevious = () => {
  if (props.currentPage > 0) {
    emit('update:page', props.currentPage - 1)
  }
}

const handleNext = () => {
  if (props.currentPage < props.totalPages - 1) {
    emit('update:page', props.currentPage + 1)
  }
}

const handlePageClick = (page: number) => {
  emit('update:page', page)
}

// 生成页码数组
const getPageNumbers = () => {
  const pages: (number | string)[] = []
  const { currentPage, totalPages } = props
  
  if (totalPages <= 7) {
    for (let i = 0; i < totalPages; i++) {
      pages.push(i)
    }
  } else {
    pages.push(0)
    
    if (currentPage > 3) {
      pages.push('...')
    }
    
    for (let i = Math.max(1, currentPage - 1); i <= Math.min(totalPages - 2, currentPage + 1); i++) {
      pages.push(i)
    }
    
    if (currentPage < totalPages - 4) {
      pages.push('...')
    }
    
    pages.push(totalPages - 1)
  }
  
  return pages
}
</script>

<template>
  <div class="flex items-center justify-between border-t border-gray-200 bg-white px-4 py-3 sm:px-6">
    <div class="flex flex-1 justify-between sm:hidden">
      <button
        @click="handlePrevious"
        :disabled="currentPage === 0"
        class="relative inline-flex items-center rounded-md border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
      >
        上一页
      </button>
      <button
        @click="handleNext"
        :disabled="currentPage === totalPages - 1"
        class="relative ml-3 inline-flex items-center rounded-md border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
      >
        下一页
      </button>
    </div>
    <div class="hidden sm:flex sm:flex-1 sm:items-center sm:justify-between">
      <div>
        <p class="text-sm text-gray-700">
          显示第
          <span class="font-medium">{{ currentPage * pageSize + 1 }}</span>
          -
          <span class="font-medium">{{ Math.min((currentPage + 1) * pageSize, totalElements) }}</span>
          条，共
          <span class="font-medium">{{ totalElements }}</span>
          条记录
        </p>
      </div>
      <div>
        <nav class="isolate inline-flex -space-x-px rounded-md shadow-sm" aria-label="Pagination">
          <button
            @click="handlePrevious"
            :disabled="currentPage === 0"
            class="relative inline-flex items-center rounded-l-md px-2 py-2 text-gray-400 ring-1 ring-inset ring-gray-300 hover:bg-gray-50 focus:z-20 focus:outline-offset-0 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            <ChevronLeft class="h-5 w-5" />
          </button>
          
          <template v-for="(page, index) in getPageNumbers()" :key="index">
            <span
              v-if="page === '...'"
              class="relative inline-flex items-center px-4 py-2 text-sm font-semibold text-gray-700 ring-1 ring-inset ring-gray-300"
            >
              ...
            </span>
            <button
              v-else
              @click="handlePageClick(page as number)"
              :class="[
                'relative inline-flex items-center px-4 py-2 text-sm font-semibold ring-1 ring-inset ring-gray-300 hover:bg-gray-50 focus:z-20 focus:outline-offset-0',
                page === currentPage
                  ? 'z-10 bg-blue-600 text-white focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600'
                  : 'text-gray-900'
              ]"
            >
              {{ (page as number) + 1 }}
            </button>
          </template>
          
          <button
            @click="handleNext"
            :disabled="currentPage === totalPages - 1"
            class="relative inline-flex items-center rounded-r-md px-2 py-2 text-gray-400 ring-1 ring-inset ring-gray-300 hover:bg-gray-50 focus:z-20 focus:outline-offset-0 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            <ChevronRight class="h-5 w-5" />
          </button>
        </nav>
      </div>
    </div>
  </div>
</template>
