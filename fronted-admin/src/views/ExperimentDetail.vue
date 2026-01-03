<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Eye } from 'lucide-vue-next'
import Badge from '@/components/Badge.vue'
import Dialog from '@/components/Dialog.vue'
import Pagination from '@/components/Pagination.vue'
import { getExperimentById } from '@/api/experiments'
import { getAnalysisResultsByExperimentId, getAnalysisResultById } from '@/api/analysis-results'
import { formatDateTime } from '@/utils/date'
import type { Experiment, AnalysisResult } from '@/types'

const route = useRoute()
const router = useRouter()

const experiment = ref<Experiment | null>(null)
const results = ref<AnalysisResult[]>([])
const currentResult = ref<AnalysisResult | null>(null)
const resultDialogVisible = ref(false)

const pagination = ref({
  page: 0,
  size: 20,
  totalPages: 0,
  totalElements: 0
})

const loadExperiment = async () => {
  try {
    const id = Number(route.params.id)
    experiment.value = await getExperimentById(id)
    
    if (experiment.value.status === 'RUNNING') {
      alert('实验正在运行中')
      router.back()
      return
    }
  } catch (error) {
    console.error('加载实验失败:', error)
    alert('加载实验失败')
    router.back()
  }
}

const loadResults = async () => {
  try {
    const id = Number(route.params.id)
    const res = await getAnalysisResultsByExperimentId(id, {
      page: pagination.value.page,
      size: pagination.value.size
    })
    results.value = res.content
    pagination.value.totalPages = res.totalPages
    pagination.value.totalElements = res.totalElements
  } catch (error) {
    console.error('加载结果失败:', error)
  }
}

const handleViewResult = async (result: AnalysisResult) => {
  try {
    currentResult.value = await getAnalysisResultById(result.id)
    resultDialogVisible.value = true
  } catch (error) {
    console.error('加载结果详情失败:', error)
  }
}

const handlePageChange = (page: number) => {
  pagination.value.page = page
  loadResults()
}

const getStatusBadgeType = (status: string) => {
  return status === 'SUCCESS' ? 'success' : 'error'
}

const getStatusText = (status: string) => {
  return status === 'SUCCESS' ? '成功' : '失败'
}

const getExpStatusBadgeType = (status: string) => {
  switch (status) {
    case 'COMPLETED': return 'success'
    case 'FAILED': return 'error'
    default: return 'info'
  }
}

const getExpStatusText = (status: string) => {
  const map: Record<string, string> = {
    RUNNING: '运行中',
    COMPLETED: '已完成',
    FAILED: '失败'
  }
  return map[status]
}

onMounted(() => {
  loadExperiment()
  loadResults()
})
</script>

<template>
  <div class="space-y-6">
    <!-- 返回按钮 -->
    <button
      @click="router.back()"
      class="flex items-center space-x-2 text-gray-600 hover:text-gray-900"
    >
      <ArrowLeft class="w-4 h-4" />
      <span>返回</span>
    </button>

    <div v-if="experiment" class="space-y-6">
      <!-- 实验信息 -->
      <div class="bg-white rounded-lg shadow p-6 space-y-4">
        <div class="flex items-center justify-between">
          <h1 class="text-2xl font-bold text-gray-900">{{ experiment.name }}</h1>
          <Badge :type="getExpStatusBadgeType(experiment.status)">
            {{ getExpStatusText(experiment.status) }}
          </Badge>
        </div>
        
        <div class="grid grid-cols-2 gap-4 pt-4 border-t border-gray-200">
          <div>
            <div class="text-sm font-medium text-gray-500">实验描述</div>
            <div class="mt-1 text-sm text-gray-900">{{ experiment.description || '-' }}</div>
          </div>
          <div>
            <div class="text-sm font-medium text-gray-500">创建时间</div>
            <div class="mt-1 text-sm text-gray-900">{{ formatDateTime(experiment.createdAt) }}</div>
          </div>
          <div>
            <div class="text-sm font-medium text-gray-500">模型配置</div>
            <div class="mt-1 text-sm text-gray-900">{{ experiment.modelConfigName }}</div>
          </div>
          <div>
            <div class="text-sm font-medium text-gray-500">提示词模板</div>
            <div class="mt-1 text-sm text-gray-900">
              {{ experiment.promptTemplateName }} v{{ experiment.promptVersionNum }}
            </div>
          </div>
          <div>
            <div class="text-sm font-medium text-gray-500">文章数量</div>
            <div class="mt-1 text-sm text-gray-900">{{ experiment.articleIds.length }} 篇</div>
          </div>
        </div>
      </div>

      <!-- 分析结果 -->
      <div class="bg-white rounded-lg shadow">
        <div class="px-6 py-4 border-b border-gray-200">
          <h2 class="text-lg font-semibold text-gray-900">分析结果</h2>
        </div>
        
        <div class="overflow-x-auto">
          <table class="min-w-full divide-y divide-gray-200">
            <thead class="bg-gray-50">
              <tr>
                <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">ID</th>
                <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">文章标题</th>
                <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">状态</th>
                <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">创建时间</th>
                <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">操作</th>
              </tr>
            </thead>
            <tbody class="bg-white divide-y divide-gray-200">
              <tr v-if="results.length === 0">
                <td colspan="5" class="px-6 py-4 text-center text-gray-500">暂无分析结果</td>
              </tr>
              <tr v-else v-for="result in results" :key="result.id" class="hover:bg-gray-50">
                <td class="px-6 py-4 text-sm text-gray-900">{{ result.id }}</td>
                <td class="px-6 py-4 text-sm text-gray-900 max-w-md truncate">{{ result.articleTitle }}</td>
                <td class="px-6 py-4">
                  <Badge :type="getStatusBadgeType(result.status)">
                    {{ getStatusText(result.status) }}
                  </Badge>
                </td>
                <td class="px-6 py-4 text-sm text-gray-500">{{ formatDateTime(result.createdAt) }}</td>
                <td class="px-6 py-4">
                  <button
                    @click="handleViewResult(result)"
                    class="text-blue-600 hover:text-blue-800"
                  >
                    <Eye class="w-4 h-4" />
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <Pagination
          v-if="results.length > 0"
          :current-page="pagination.page"
          :total-pages="pagination.totalPages"
          :total-elements="pagination.totalElements"
          :page-size="pagination.size"
          @update:page="handlePageChange"
        />
      </div>
    </div>

    <!-- 分析结果详情对话框 -->
    <Dialog
      v-model:visible="resultDialogVisible"
      title="分析结果详情"
      width="800px"
      @confirm="resultDialogVisible = false"
    >
      <template #default>
        <div v-if="currentResult" class="space-y-6">
          <!-- 基本信息 -->
          <div>
            <h3 class="text-sm font-medium text-gray-900 mb-3">基本信息</h3>
            <div class="grid grid-cols-2 gap-4">
              <div>
                <div class="text-sm font-medium text-gray-500">实验名称</div>
                <div class="mt-1 text-sm text-gray-900">{{ currentResult.experimentName }}</div>
              </div>
              <div>
                <div class="text-sm font-medium text-gray-500">文章标题</div>
                <div class="mt-1 text-sm text-gray-900">{{ currentResult.articleTitle }}</div>
              </div>
              <div>
                <div class="text-sm font-medium text-gray-500">状态</div>
                <div class="mt-1">
                  <Badge :type="getStatusBadgeType(currentResult.status)">
                    {{ getStatusText(currentResult.status) }}
                  </Badge>
                </div>
              </div>
              <div>
                <div class="text-sm font-medium text-gray-500">创建时间</div>
                <div class="mt-1 text-sm text-gray-900">{{ formatDateTime(currentResult.createdAt) }}</div>
              </div>
            </div>
          </div>

          <!-- 配置信息 -->
          <div>
            <h3 class="text-sm font-medium text-gray-900 mb-3">配置信息</h3>
            <div class="space-y-3">
              <div>
                <div class="text-sm font-medium text-gray-500 mb-1">模型配置</div>
                <pre class="text-xs bg-gray-50 p-3 rounded-lg overflow-x-auto"><code>{{ JSON.parse(currentResult.modelConfigJson) }}</code></pre>
              </div>
              <div>
                <div class="text-sm font-medium text-gray-500 mb-1">提示词内容</div>
                <pre class="text-sm bg-gray-50 p-3 rounded-lg overflow-x-auto whitespace-pre-wrap">{{ currentResult.promptContent }}</pre>
              </div>
            </div>
          </div>

          <!-- 分析结果 -->
          <div>
            <h3 class="text-sm font-medium text-gray-900 mb-3">分析结果</h3>
            <div v-if="currentResult.status === 'SUCCESS'" class="space-y-3">
              <div>
                <div class="text-sm font-medium text-gray-500 mb-1">分析内容</div>
                <pre class="text-sm bg-gray-50 p-3 rounded-lg overflow-x-auto whitespace-pre-wrap">{{ currentResult.analysisResult }}</pre>
              </div>
              <div class="grid grid-cols-3 gap-4">
                <div>
                  <div class="text-sm font-medium text-gray-500">输入 Tokens</div>
                  <div class="mt-1 text-sm text-gray-900">{{ currentResult.inputTokens }}</div>
                </div>
                <div>
                  <div class="text-sm font-medium text-gray-500">输出 Tokens</div>
                  <div class="mt-1 text-sm text-gray-900">{{ currentResult.outputTokens }}</div>
                </div>
                <div>
                  <div class="text-sm font-medium text-gray-500">执行耗时</div>
                  <div class="mt-1 text-sm text-gray-900">{{ currentResult.executionTimeMs }} ms</div>
                </div>
              </div>
            </div>
            <div v-else-if="currentResult.errorMessage" class="text-sm text-red-600">
              错误：{{ currentResult.errorMessage }}
            </div>
          </div>
        </div>
      </template>
    </Dialog>
  </div>
</template>
