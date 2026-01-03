<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { RefreshCw, Plus, Edit, Trash2 } from 'lucide-vue-next'
import Dialog from '@/components/Dialog.vue'
import Pagination from '@/components/Pagination.vue'
import { getModelConfigs, getModelConfigById, createModelConfig, updateModelConfig, deleteModelConfig } from '@/api/model-configs'
import { formatDateTime } from '@/utils/date'
import type { ModelConfig, ModelConfigRequest } from '@/types'

const loading = ref(false)
const configs = ref<ModelConfig[]>([])
const pagination = ref({
  page: 0,
  size: 20,
  totalPages: 0,
  totalElements: 0
})

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const currentConfig = ref<Partial<ModelConfig>>({})
const formData = ref<ModelConfigRequest>({
  name: '',
  description: '',
  modelId: '',
  temperature: 0.7,
  topP: 0.9,
  topK: undefined,
  maxTokens: undefined,
  seed: undefined
})

const loadConfigs = async () => {
  loading.value = true
  try {
    const res = await getModelConfigs({
      page: pagination.value.page,
      size: pagination.value.size
    })
    configs.value = res.content
    pagination.value.totalPages = res.totalPages
    pagination.value.totalElements = res.totalElements
  } catch (error) {
    console.error('加载配置失败:', error)
  } finally {
    loading.value = false
  }
}

const handleCreate = () => {
  dialogMode.value = 'create'
  formData.value = {
    name: '',
    description: '',
    modelId: '',
    temperature: 0.7,
    topP: 0.9,
    topK: undefined,
    maxTokens: undefined,
    seed: undefined
  }
  dialogVisible.value = true
}

const handleEdit = async (config: ModelConfig) => {
  dialogMode.value = 'edit'
  currentConfig.value = config
  formData.value = {
    name: config.name,
    description: config.description,
    modelId: config.modelId,
    temperature: config.temperature,
    topP: config.topP,
    topK: config.topK,
    maxTokens: config.maxTokens,
    seed: config.seed
  }
  dialogVisible.value = true
}

const handleSave = async () => {
  try {
    if (dialogMode.value === 'create') {
      await createModelConfig(formData.value)
    } else {
      await updateModelConfig(currentConfig.value.id!, formData.value)
    }
    dialogVisible.value = false
    loadConfigs()
  } catch (error) {
    console.error('保存失败:', error)
    alert('保存失败')
  }
}

const handleDelete = async (id: number) => {
  if (!confirm('确定要删除这个配置吗？')) return
  
  try {
    await deleteModelConfig(id)
    loadConfigs()
  } catch (error) {
    console.error('删除失败:', error)
    alert('删除失败')
  }
}

const handlePageChange = (page: number) => {
  pagination.value.page = page
  loadConfigs()
}

onMounted(() => {
  loadConfigs()
})
</script>

<template>
  <div class="space-y-6">
    <!-- 科普信息卡片 -->
    <div class="grid grid-cols-1 md:grid-cols-5 gap-4">
      <div class="bg-blue-50 border-2 border-blue-200 rounded-lg p-4">
        <h3 class="text-sm font-semibold text-blue-900 mb-1">Temperature</h3>
        <p class="text-xs text-blue-700">控制输出随机性，值越高越随机 (0.0-2.0)</p>
      </div>
      <div class="bg-green-50 border-2 border-green-200 rounded-lg p-4">
        <h3 class="text-sm font-semibold text-green-900 mb-1">Top P</h3>
        <p class="text-xs text-green-700">核采样参数，控制从概率最高的词中选择 (0.0-1.0)</p>
      </div>
      <div class="bg-purple-50 border-2 border-purple-200 rounded-lg p-4">
        <h3 class="text-sm font-semibold text-purple-900 mb-1">Top K</h3>
        <p class="text-xs text-purple-700">从概率最高的 K 个词中选择</p>
      </div>
      <div class="bg-yellow-50 border-2 border-yellow-200 rounded-lg p-4">
        <h3 class="text-sm font-semibold text-yellow-900 mb-1">Max Tokens</h3>
        <p class="text-xs text-yellow-700">限制模型输出的最大 token 数量</p>
      </div>
      <div class="bg-red-50 border-2 border-red-200 rounded-lg p-4">
        <h3 class="text-sm font-semibold text-red-900 mb-1">Seed</h3>
        <p class="text-xs text-red-700">随机种子，设置相同种子可获得确定性输出</p>
      </div>
    </div>

    <!-- 操作栏 -->
    <div class="flex items-center justify-between">
      <button
        @click="handleCreate"
        class="flex items-center space-x-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
      >
        <Plus class="w-4 h-4" />
        <span>新增配置</span>
      </button>
      <button
        @click="loadConfigs"
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
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">模型 ID</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Temperature</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Top P</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Top K</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Max Tokens</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Seed</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">创建时间</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">操作</th>
            </tr>
          </thead>
          <tbody class="bg-white divide-y divide-gray-200">
            <tr v-if="loading">
              <td colspan="11" class="px-6 py-4 text-center text-gray-500">加载中...</td>
            </tr>
            <tr v-else-if="configs.length === 0">
              <td colspan="11" class="px-6 py-4 text-center text-gray-500">暂无数据</td>
            </tr>
            <tr v-else v-for="config in configs" :key="config.id" class="hover:bg-gray-50">
              <td class="px-6 py-4 text-sm text-gray-900">{{ config.id }}</td>
              <td class="px-6 py-4 text-sm font-medium text-gray-900">{{ config.name }}</td>
              <td class="px-6 py-4 text-sm text-gray-500 max-w-xs truncate">{{ config.description || '-' }}</td>
              <td class="px-6 py-4 text-sm text-gray-500">{{ config.modelId }}</td>
              <td class="px-6 py-4 text-sm text-gray-500">{{ config.temperature ?? '-' }}</td>
              <td class="px-6 py-4 text-sm text-gray-500">{{ config.topP ?? '-' }}</td>
              <td class="px-6 py-4 text-sm text-gray-500">{{ config.topK ?? '-' }}</td>
              <td class="px-6 py-4 text-sm text-gray-500">{{ config.maxTokens ?? '-' }}</td>
              <td class="px-6 py-4 text-sm text-gray-500">{{ config.seed ?? '-' }}</td>
              <td class="px-6 py-4 text-sm text-gray-500">{{ formatDateTime(config.createdAt) }}</td>
              <td class="px-6 py-4 text-sm space-x-2">
                <button
                  @click="handleEdit(config)"
                  class="text-gray-600 hover:text-gray-800"
                  title="编辑"
                >
                  <Edit class="w-4 h-4" />
                </button>
                <button
                  @click="handleDelete(config.id)"
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
        v-if="configs.length > 0"
        :current-page="pagination.page"
        :total-pages="pagination.totalPages"
        :total-elements="pagination.totalElements"
        :page-size="pagination.size"
        @update:page="handlePageChange"
      />
    </div>

    <!-- 新增/编辑对话框 -->
    <Dialog
      v-model:visible="dialogVisible"
      :title="dialogMode === 'create' ? '新增配置' : '编辑配置'"
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
            rows="2"
            class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500"
          />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">模型 ID <span class="text-red-500">*</span></label>
          <input
            v-model="formData.modelId"
            type="text"
            required
            class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500"
          />
        </div>
        <div class="grid grid-cols-2 gap-4">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Temperature (0.0-2.0)</label>
            <input
              v-model.number="formData.temperature"
              type="number"
              step="0.1"
              min="0"
              max="2"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500"
            />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Top P (0.0-1.0)</label>
            <input
              v-model.number="formData.topP"
              type="number"
              step="0.1"
              min="0"
              max="1"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500"
            />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Top K</label>
            <input
              v-model.number="formData.topK"
              type="number"
              min="0"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500"
            />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Max Tokens</label>
            <input
              v-model.number="formData.maxTokens"
              type="number"
              min="0"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500"
            />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Seed</label>
            <input
              v-model.number="formData.seed"
              type="number"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500"
            />
          </div>
        </div>
      </div>
    </Dialog>
  </div>
</template>
