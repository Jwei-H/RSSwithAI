<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { RefreshCw, Plus, Eye, Trash2 } from 'lucide-vue-next'
import Badge from '@/components/Badge.vue'
import Dialog from '@/components/Dialog.vue'
import Pagination from '@/components/Pagination.vue'
import { getPromptTemplates, createPromptTemplate, deletePromptTemplate } from '@/api/prompts'
import { formatDateTime } from '@/utils/date'
import type { PromptTemplate } from '@/types'

const router = useRouter()
const loading = ref(false)
const templates = ref<PromptTemplate[]>([])
const pagination = ref({
  page: 0,
  size: 20,
  totalPages: 0,
  totalElements: 0
})

const dialogVisible = ref(false)
const formData = ref({
  name: '',
  description: ''
})

const loadTemplates = async () => {
  loading.value = true
  try {
    const res = await getPromptTemplates({
      page: pagination.value.page,
      size: pagination.value.size
    })
    templates.value = res.content
    pagination.value.totalPages = res.totalPages
    pagination.value.totalElements = res.totalElements
  } catch (error) {
    console.error('加载模板失败:', error)
  } finally {
    loading.value = false
  }
}

const handleCreate = () => {
  formData.value = { name: '', description: '' }
  dialogVisible.value = true
}

const handleSave = async () => {
  try {
    const template = await createPromptTemplate(formData.value)
    dialogVisible.value = false
    router.push(`/prompts/${template.id}`)
  } catch (error) {
    console.error('创建失败:', error)
    alert('创建失败')
  }
}

const handleDelete = async (id: number) => {
  if (!confirm('确定要删除这个模板吗？')) return
  
  try {
    await deletePromptTemplate(id)
    loadTemplates()
  } catch (error) {
    console.error('删除失败:', error)
    alert('删除失败')
  }
}

const handleViewDetail = (template: PromptTemplate) => {
  router.push(`/prompts/${template.id}`)
}

const handlePageChange = (page: number) => {
  pagination.value.page = page
  loadTemplates()
}

onMounted(() => {
  loadTemplates()
})
</script>

<template>
  <div class="space-y-6">
    <!-- 科普信息卡片 -->
    <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
      <div class="bg-blue-50 border-2 border-blue-200 rounded-lg p-4">
        <h3 class="text-sm font-semibold text-blue-900 mb-2">版本管理机制</h3>
        <p class="text-xs text-blue-700">系统采用两级结构：模板（父级）+ 版本（子级），每个模板可以有多个版本</p>
      </div>
      <div class="bg-green-50 border-2 border-green-200 rounded-lg p-4">
        <h3 class="text-sm font-semibold text-green-900 mb-2">版本锁定</h3>
        <p class="text-xs text-green-700">锁定后的版本内容不可修改，用于实验和对比分析</p>
      </div>
      <div class="bg-purple-50 border-2 border-purple-200 rounded-lg p-4">
        <h3 class="text-sm font-semibold text-purple-900 mb-2">创建新版本</h3>
        <p class="text-xs text-purple-700">只有锁定当前版本后，才能基于它创建新的可修改版本</p>
      </div>
      <div class="bg-yellow-50 border-2 border-yellow-200 rounded-lg p-4">
        <h3 class="text-sm font-semibold text-yellow-900 mb-2">变量占位符</h3>
        <p class="text-xs text-yellow-700">支持 {author}、{content}、{sourceName}、{title}、{pubDate}、{categories}</p>
      </div>
    </div>

    <!-- 操作栏 -->
    <div class="flex items-center justify-between">
      <button
        @click="handleCreate"
        class="flex items-center space-x-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
      >
        <Plus class="w-4 h-4" />
        <span>新增模板</span>
      </button>
      <button
        @click="loadTemplates"
        class="flex items-center space-x-2 px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-50"
      >
        <RefreshCw class="w-4 h-4" />
        <span>刷新列表</span>
      </button>
    </div>

    <!-- 列表 -->
    <div class="bg-white rounded-lg shadow overflow-hidden">
      <div class="overflow-x-auto">
        <table class="min-w-full divide-y divide-gray-200">
          <thead class="bg-gray-50">
            <tr>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">ID</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">名称</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">描述</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">最新版本</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">版本状态</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">创建时间</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">操作</th>
            </tr>
          </thead>
          <tbody class="bg-white divide-y divide-gray-200">
            <tr v-if="loading">
              <td colspan="7" class="px-6 py-4 text-center text-gray-500">加载中...</td>
            </tr>
            <tr v-else-if="templates.length === 0">
              <td colspan="7" class="px-6 py-4 text-center text-gray-500">暂无数据</td>
            </tr>
            <tr v-else v-for="template in templates" :key="template.id" class="hover:bg-gray-50">
              <td class="px-6 py-4 text-sm text-gray-900">{{ template.id }}</td>
              <td
                class="px-6 py-4 text-sm font-medium text-blue-600 hover:text-blue-800 cursor-pointer"
                @click="handleViewDetail(template)"
              >
                {{ template.name }}
              </td>
              <td class="px-6 py-4 text-sm text-gray-500 max-w-xs truncate">
                {{ template.description || '-' }}
              </td>
              <td class="px-6 py-4 text-sm text-gray-900">v{{ template.latestVersion }}</td>
              <td class="px-6 py-4">
                <Badge :type="template.latestVersionDetail?.immutable ? 'default' : 'warning'">
                  {{ template.latestVersionDetail?.immutable ? '已锁定' : '可修改' }}
                </Badge>
              </td>
              <td class="px-6 py-4 text-sm text-gray-500">{{ formatDateTime(template.createdAt) }}</td>
              <td class="px-6 py-4 text-sm space-x-2">
                <button
                  @click="handleViewDetail(template)"
                  class="text-blue-600 hover:text-blue-800"
                  title="查看详情"
                >
                  <Eye class="w-4 h-4" />
                </button>
                <button
                  @click="handleDelete(template.id)"
                  class="text-red-600 hover:text-red-800"
                  title="删除"
                >
                  <Trash2 class="w-4 h-4" />
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <Pagination
        v-if="templates.length > 0"
        :current-page="pagination.page"
        :total-pages="pagination.totalPages"
        :total-elements="pagination.totalElements"
        :page-size="pagination.size"
        @update:page="handlePageChange"
      />
    </div>

    <!-- 新增对话框 -->
    <Dialog
      v-model:visible="dialogVisible"
      title="新增模板"
      @confirm="handleSave"
    >
      <div class="space-y-4">
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">名称 <span class="text-red-500">*</span></label>
          <input
            v-model="formData.name"
            type="text"
            required
            class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500"
          />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">描述</label>
          <textarea
            v-model="formData.description"
            rows="3"
            class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500"
          />
        </div>
      </div>
    </Dialog>
  </div>
</template>
