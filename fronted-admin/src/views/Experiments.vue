<script setup lang="ts">
import { ref, onMounted, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { RefreshCw, Plus, Eye, Trash2, Check, X } from 'lucide-vue-next'
import Badge from '@/components/Badge.vue'
import Dialog from '@/components/Dialog.vue'
import Select from '@/components/Select.vue'
import Pagination from '@/components/Pagination.vue'
import { getExperiments, createExperiment, deleteExperiment } from '@/api/experiments'
import { getArticles } from '@/api/articles'
import { getModelConfigs } from '@/api/model-configs'
import { getPromptTemplates, getPromptVersion } from '@/api/prompts'
import { formatDateTime } from '@/utils/date'
import type { Experiment, Article, ModelConfig, PromptTemplate, PromptVersion } from '@/types'

const router = useRouter()
const loading = ref(false)
const experiments = ref<Experiment[]>([])
const pagination = ref({
  page: 0,
  size: 20,
  totalPages: 0,
  totalElements: 0
})

const statusFilter = ref('')
const dialogVisible = ref(false)
const articleSelectDialogVisible = ref(false)

// 表单数据
const formData = ref({
  name: '',
  description: '',
  articleIds: [] as number[],
  modelConfigId: 0,
  promptTemplateId: 0,
  promptVersionNum: 0
})

// 下拉选项
const modelConfigs = ref<ModelConfig[]>([])
const promptTemplates = ref<PromptTemplate[]>([])
const promptVersions = ref<PromptVersion[]>([])

// 文章选择
const articles = ref<Article[]>([])
const articlePagination = ref({
  page: 0,
  size: 10,
  totalPages: 0,
  totalElements: 0
})
const articleSearchWord = ref('')
const selectedArticleMap = ref<Map<number, string>>(new Map())

const statusOptions = [
  { value: '', label: '全部状态' },
  { value: 'RUNNING', label: '运行中' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'FAILED', label: '失败' }
]

const modelConfigOptions = computed(() => {
  return modelConfigs.value.map(c => ({ value: c.id, label: c.name }))
})

const promptTemplateOptions = computed(() => {
  return promptTemplates.value.map(t => ({ value: t.id, label: t.name }))
})

const promptVersionOptions = computed(() => {
  return promptVersions.value.map(v => ({ value: v.version, label: `v${v.version}` }))
})

watch(() => formData.value.promptTemplateId, (newId) => {
  if (newId) {
    loadPromptVersions(newId)
  } else {
    promptVersions.value = []
    formData.value.promptVersionNum = 0
  }
})

const loadExperiments = async () => {
  loading.value = true
  try {
    const params: any = {
      page: pagination.value.page,
      size: pagination.value.size
    }
    if (statusFilter.value) {
      params.status = statusFilter.value
    }
    const res = await getExperiments(params)
    experiments.value = res.content
    pagination.value.totalPages = res.totalPages
    pagination.value.totalElements = res.totalElements
  } catch (error) {
    console.error('加载实验失败:', error)
  } finally {
    loading.value = false
  }
}

const loadOptions = async () => {
  try {
    const [configsRes, templatesRes] = await Promise.all([
      getModelConfigs({ size: 1000 }),
      getPromptTemplates({ size: 1000 })
    ])
    modelConfigs.value = configsRes.content
    promptTemplates.value = templatesRes.content
  } catch (error) {
    console.error('加载选项失败:', error)
  }
}

const loadPromptVersions = async (templateId: number) => {
  try {
    const template = promptTemplates.value.find(t => t.id === templateId)
    if (!template) return
    
    const versions: PromptVersion[] = []
    for (let i = 1; i <= template.latestVersion; i++) {
      const version = await getPromptVersion(templateId, i)
      if (version.immutable) {
        versions.push(version)
      }
    }
    promptVersions.value = versions
    if (versions.length > 0) {
      formData.value.promptVersionNum = versions[versions.length - 1].version
    }
  } catch (error) {
    console.error('加载版本失败:', error)
  }
}

const loadArticles = async () => {
  try {
    const res = await getArticles({
      page: articlePagination.value.page,
      size: articlePagination.value.size,
      searchWord: articleSearchWord.value || undefined
    })
    articles.value = res.content
    articlePagination.value.totalPages = res.totalPages
    articlePagination.value.totalElements = res.totalElements
  } catch (error) {
    console.error('加载文章失败:', error)
  }
}

const handleCreate = async () => {
  formData.value = {
    name: '',
    description: '',
    articleIds: [],
    modelConfigId: 0,
    promptTemplateId: 0,
    promptVersionNum: 0
  }
  selectedArticleMap.value = new Map()
  await loadOptions()
  dialogVisible.value = true
}

const handleSelectArticles = async () => {
  await loadArticles()
  articleSelectDialogVisible.value = true
}

const handleArticleSelect = (article: Article) => {
  if (selectedArticleMap.value.has(article.id)) {
    selectedArticleMap.value.delete(article.id)
  } else {
    selectedArticleMap.value.set(article.id, article.title)
  }
}

const handleConfirmArticles = () => {
  formData.value.articleIds = Array.from(selectedArticleMap.value.keys())
  articleSelectDialogVisible.value = false
}

const handleSave = async () => {
  if (!formData.value.name || formData.value.articleIds.length === 0 || !formData.value.modelConfigId || !formData.value.promptTemplateId) {
    alert('请填写所有必填项')
    return
  }
  
  try {
    await createExperiment(formData.value)
    dialogVisible.value = false
    loadExperiments()
  } catch (error) {
    console.error('创建实验失败:', error)
    alert('创建实验失败')
  }
}

const handleDelete = async (id: number) => {
  if (!confirm('确定要删除这个实验吗？')) return
  
  try {
    await deleteExperiment(id)
    loadExperiments()
  } catch (error) {
    console.error('删除失败:', error)
    alert('删除失败')
  }
}

const handleViewDetail = (experiment: Experiment) => {
  if (experiment.status === 'RUNNING') {
    alert('实验正在运行中，请稍后查看')
    return
  }
  router.push(`/experiments/${experiment.id}`)
}

const handlePageChange = (page: number) => {
  pagination.value.page = page
  loadExperiments()
}

const handleArticlePageChange = (page: number) => {
  articlePagination.value.page = page
  loadArticles()
}

const getStatusBadgeType = (status: string) => {
  switch (status) {
    case 'COMPLETED': return 'success'
    case 'FAILED': return 'error'
    default: return 'info'
  }
}

const getStatusText = (status: string) => {
  const map: Record<string, string> = {
    RUNNING: '运行中',
    COMPLETED: '已完成',
    FAILED: '失败'
  }
  return map[status]
}

const canDelete = (status: string) => {
  return status !== 'RUNNING'
}

onMounted(() => {
  loadExperiments()
})
</script>

<template>
  <div class="space-y-6">
    <!-- 流程卡片 -->
    <div class="grid grid-cols-1 md:grid-cols-5 gap-4">
      <div class="bg-blue-50 border-2 border-blue-200 rounded-lg p-4 text-center">
        <div class="text-2xl font-bold text-blue-600 mb-1">1</div>
        <div class="text-xs text-blue-900 font-medium">选择文章</div>
      </div>
      <div class="bg-green-50 border-2 border-green-200 rounded-lg p-4 text-center">
        <div class="text-2xl font-bold text-green-600 mb-1">2</div>
        <div class="text-xs text-green-900 font-medium">配置模型</div>
      </div>
      <div class="bg-purple-50 border-2 border-purple-200 rounded-lg p-4 text-center">
        <div class="text-2xl font-bold text-purple-600 mb-1">3</div>
        <div class="text-xs text-purple-900 font-medium">选择提示词</div>
      </div>
      <div class="bg-yellow-50 border-2 border-yellow-200 rounded-lg p-4 text-center">
        <div class="text-2xl font-bold text-yellow-600 mb-1">4</div>
        <div class="text-xs text-yellow-900 font-medium">创建实验</div>
      </div>
      <div class="bg-red-50 border-2 border-red-200 rounded-lg p-4 text-center">
        <div class="text-2xl font-bold text-red-600 mb-1">5</div>
        <div class="text-xs text-red-900 font-medium">查看结果</div>
      </div>
    </div>

    <!-- 操作栏 -->
    <div class="flex items-center justify-between">
      <div class="flex items-center space-x-3">
        <button
          @click="handleCreate"
          class="flex items-center space-x-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
        >
          <Plus class="w-4 h-4" />
          <span>新增实验</span>
        </button>
        <Select
          v-model="statusFilter"
          :options="statusOptions"
          @update:model-value="loadExperiments"
          class="w-32"
        />
      </div>
      <button
        @click="loadExperiments"
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
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">状态</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">文章数量</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">模型配置</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">提示词模板</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">创建时间</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">操作</th>
            </tr>
          </thead>
          <tbody class="bg-white divide-y divide-gray-200">
            <tr v-if="loading">
              <td colspan="9" class="px-6 py-4 text-center text-gray-500">加载中...</td>
            </tr>
            <tr v-else-if="experiments.length === 0">
              <td colspan="9" class="px-6 py-4 text-center text-gray-500">暂无数据</td>
            </tr>
            <tr v-else v-for="exp in experiments" :key="exp.id" class="hover:bg-gray-50">
              <td class="px-6 py-4 text-sm text-gray-900">{{ exp.id }}</td>
              <td
                class="px-6 py-4 text-sm font-medium cursor-pointer"
                :class="exp.status === 'RUNNING' ? 'text-gray-500' : 'text-blue-600 hover:text-blue-800'"
                @click="handleViewDetail(exp)"
              >
                {{ exp.name }}
              </td>
              <td class="px-6 py-4 text-sm text-gray-500 max-w-xs truncate">{{ exp.description || '-' }}</td>
              <td class="px-6 py-4">
                <Badge :type="getStatusBadgeType(exp.status)">{{ getStatusText(exp.status) }}</Badge>
              </td>
              <td class="px-6 py-4 text-sm text-gray-900">{{ exp.articleIds.length }}</td>
              <td class="px-6 py-4 text-sm text-gray-500">{{ exp.modelConfigName }}</td>
              <td class="px-6 py-4 text-sm text-gray-500">
                {{ exp.promptTemplateName }} v{{ exp.promptVersion }}
              </td>
              <td class="px-6 py-4 text-sm text-gray-500">{{ formatDateTime(exp.createdAt) }}</td>
              <td class="px-6 py-4 text-sm space-x-2">
                <button
                  v-if="exp.status !== 'RUNNING'"
                  @click="handleViewDetail(exp)"
                  class="text-blue-600 hover:text-blue-800"
                  title="查看详情"
                >
                  <Eye class="w-4 h-4" />
                </button>
                <button
                  v-if="canDelete(exp.status)"
                  @click="handleDelete(exp.id)"
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
        v-if="experiments.length > 0"
        :current-page="pagination.page"
        :total-pages="pagination.totalPages"
        :total-elements="pagination.totalElements"
        :page-size="pagination.size"
        @update:page="handlePageChange"
      />
    </div>

    <!-- 新增实验对话框 -->
    <Dialog
      v-model:visible="dialogVisible"
      title="新增实验"
      width="700px"
      @confirm="handleSave"
    >
      <div class="space-y-4">
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">实验名称 <span class="text-red-500">*</span></label>
          <input
            v-model="formData.name"
            type="text"
            required
            class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500"
          />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">实验描述</label>
          <textarea
            v-model="formData.description"
            rows="2"
            class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500"
          />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">选择文章 <span class="text-red-500">*</span></label>
          <button
            @click="handleSelectArticles"
            class="w-full px-3 py-2 border-2 border-dashed border-gray-300 rounded-lg hover:border-blue-500 text-gray-600 hover:text-blue-600"
          >
            {{ selectedArticleMap.size > 0 ? `已选择 ${selectedArticleMap.size} 篇文章` : '点击选择文章' }}
          </button>
          
          <div v-if="selectedArticleMap.size > 0" class="mt-2">
            <div class="flex flex-wrap gap-2">
              <div
                v-for="[id, title] in selectedArticleMap"
                :key="id"
                class="inline-flex items-center px-2 py-1 rounded bg-blue-50 text-blue-700 text-xs border border-blue-100"
              >
                <span class="max-w-[200px] truncate" :title="title">{{ title }}</span>
                <button
                  @click="selectedArticleMap.delete(id); formData.articleIds = Array.from(selectedArticleMap.keys())"
                  class="ml-1 p-0.5 rounded-full hover:bg-blue-100 text-blue-400 hover:text-blue-600"
                >
                  <X class="w-3 h-3" />
                </button>
              </div>
            </div>
          </div>
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">模型配置 <span class="text-red-500">*</span></label>
          <Select
            v-model="formData.modelConfigId"
            :options="modelConfigOptions"
            placeholder="请选择"
          />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">提示词模板 <span class="text-red-500">*</span></label>
          <Select
            v-model="formData.promptTemplateId"
            :options="promptTemplateOptions"
            placeholder="请选择"
          />
        </div>
        <div v-if="formData.promptTemplateId">
          <label class="block text-sm font-medium text-gray-700 mb-1">提示词版本 <span class="text-red-500">*</span></label>
          <Select
            v-model="formData.promptVersionNum"
            :options="promptVersionOptions"
            :placeholder="promptVersions.length === 0 ? '该模板没有已锁定版本' : '请选择'"
            :disabled="promptVersions.length === 0"
          />
        </div>
      </div>
    </Dialog>

    <!-- 文章选择对话框 -->
    <Dialog
      v-model:visible="articleSelectDialogVisible"
      title="选择文章"
      width="900px"
      @confirm="handleConfirmArticles"
    >
      <div class="space-y-4">
        <div class="flex items-center space-x-2">
          <input
            v-model="articleSearchWord"
            type="text"
            placeholder="搜索标题或作者"
            class="flex-1 px-3 py-2 border border-gray-300 rounded-lg"
            @keyup.enter="loadArticles"
          />
          <button
            @click="loadArticles"
            class="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
          >
            搜索
          </button>
        </div>
        <!-- <div class="text-sm text-gray-600">已选择: {{ selectedArticleMap.size }} 篇</div> -->
        
        <div v-if="selectedArticleMap.size > 0" class="p-4 bg-blue-50 rounded-lg border border-blue-100">
          <div class="text-sm font-medium text-blue-900 mb-2">已选文章 ({{ selectedArticleMap.size }})</div>
          <div class="flex flex-wrap gap-2 max-h-32 overflow-y-auto">
            <div
              v-for="[id, title] in selectedArticleMap"
              :key="id"
              class="inline-flex items-center px-2 py-1 rounded bg-white text-blue-700 text-xs border border-blue-200 shadow-sm"
            >
              <span class="max-w-[200px] truncate" :title="title">{{ title }}</span>
              <button
                @click="selectedArticleMap.delete(id)"
                class="ml-1 p-0.5 rounded-full hover:bg-gray-100 text-gray-400 hover:text-red-500"
              >
                <X class="w-3 h-3" />
              </button>
            </div>
          </div>
        </div>

        <div class="border border-gray-200 rounded-lg overflow-hidden max-h-96 overflow-y-auto">
          <table class="min-w-full divide-y divide-gray-200">
            <thead class="bg-gray-50 sticky top-0">
              <tr>
                <th class="px-4 py-2 text-left text-xs font-medium text-gray-500">选择</th>
                <th class="px-4 py-2 text-left text-xs font-medium text-gray-500">ID</th>
                <th class="px-4 py-2 text-left text-xs font-medium text-gray-500">标题</th>
              </tr>
            </thead>
            <tbody class="bg-white divide-y divide-gray-200">
              <tr v-for="article in articles" :key="article.id" class="hover:bg-gray-50">
                <td class="px-4 py-2">
                  <input
                    type="checkbox"
                    :checked="selectedArticleMap.has(article.id)"
                    @change="handleArticleSelect(article)"
                    class="w-4 h-4 text-blue-600 rounded"
                  />
                </td>
                <td class="px-4 py-2 text-sm text-gray-900">{{ article.id }}</td>
                <td class="px-4 py-2 text-sm text-gray-900">{{ article.title }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <Pagination
          v-if="articles.length > 0"
          :current-page="articlePagination.page"
          :total-pages="articlePagination.totalPages"
          :total-elements="articlePagination.totalElements"
          :page-size="articlePagination.size"
          @update:page="handleArticlePageChange"
        />
      </div>
    </Dialog>
  </div>
</template>
