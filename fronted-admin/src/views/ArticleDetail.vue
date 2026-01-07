<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, ExternalLink, Eye, RefreshCw } from 'lucide-vue-next'
import Badge from '@/components/Badge.vue'
import Dialog from '@/components/Dialog.vue'
import Pagination from '@/components/Pagination.vue'
import { getArticleById, getArticleExtra, regenerateArticleExtra } from '@/api/articles'
import { getAnalysisResultsByArticleId, getAnalysisResultById } from '@/api/analysis-results'
import { formatDateTime } from '@/utils/date'
import type { Article, ArticleExtra, AnalysisResult } from '@/types'
import { marked } from 'marked'
import hljs from 'highlight.js'
import 'highlight.js/styles/github.css'

const route = useRoute()
const router = useRouter()

const renderer = {
  code({ text, lang }: { text: string, lang?: string }) {
    const validLang = !!(lang && hljs.getLanguage(lang))
    const highlighted = validLang
      ? hljs.highlight(text, { language: lang as string }).value
      : hljs.highlightAuto(text).value
    return `<pre><code class="hljs ${validLang ? 'language-' + lang : ''}">${highlighted}</code></pre>`
  }
}

marked.use({ renderer })

const renderMarkdown = (text: string) => {
  if (!text) return ''
  const html = marked.parse(text)
  // 为图片添加 lazy 与 no-referrer 以减少防盗链 403
  return (html as string).replace(/<img\s/gi, '<img loading="lazy" referrerpolicy="no-referrer" ')
}
const handleBack = () => {
  const { sourceId, searchWord, page, size } = route.query as Record<string, any>
  if (sourceId != null || searchWord != null || page != null || size != null) {
    router.push({ path: '/articles', query: { sourceId, searchWord, page, size } })
  } else {
    router.back()
  }
}

const article = ref<Article | null>(null)
const articleExtra = ref<ArticleExtra | null>(null)
const analysisResults = ref<AnalysisResult[]>([])
const currentResult = ref<AnalysisResult | null>(null)
const showMarkdown = ref(true)
const regenerating = ref(false)

const pagination = ref({
  page: 0,
  size: 10,
  totalPages: 0,
  totalElements: 0
})

const resultDialogVisible = ref(false)

// 加载文章详情
const loadArticle = async () => {
  try {
    const id = Number(route.params.id)
    article.value = await getArticleById(id)
    
    // 加载增强信息
    try {
      articleExtra.value = await getArticleExtra(id)
    } catch (error) {
      console.log('该文章暂无增强信息')
    }
  } catch (error) {
    console.error('加载文章失败:', error)
    alert('加载文章失败')
    router.back()
  }
}

// 加载分析结果
const loadAnalysisResults = async () => {
  try {
    const id = Number(route.params.id)
    const res = await getAnalysisResultsByArticleId(id, {
      page: pagination.value.page,
      size: pagination.value.size
    })
    analysisResults.value = res.content
    pagination.value.totalPages = res.totalPages
    pagination.value.totalElements = res.totalElements
  } catch (error) {
    console.error('加载分析结果失败:', error)
  }
}

// 查看分析结果详情
const handleViewResult = async (result: AnalysisResult) => {
  try {
    currentResult.value = await getAnalysisResultById(result.id)
    resultDialogVisible.value = true
  } catch (error) {
    console.error('加载分析结果详情失败:', error)
  }
}

// 分页
const handlePageChange = (page: number) => {
  pagination.value.page = page
  loadAnalysisResults()
}

const handleRegenerate = async () => {
  if (!article.value) return
  regenerating.value = true
  try {
    articleExtra.value = await regenerateArticleExtra(article.value.id)
  } catch (error) {
    console.error('重新生成失败:', error)
    alert('重新生成失败')
  } finally {
    regenerating.value = false
  }
}

const getStatusBadgeType = (status: string) => {
  return status === 'SUCCESS' ? 'success' : 'error'
}

const getStatusText = (status: string) => {
  return status === 'SUCCESS' ? '成功' : '失败'
}

onMounted(() => {
  loadArticle()
  loadAnalysisResults()
})
</script>

<template>
  <div class="space-y-6">
    <!-- 返回按钮 -->
    <button
      @click="handleBack"
      class="flex items-center space-x-2 text-gray-600 hover:text-gray-900"
    >
      <ArrowLeft class="w-4 h-4" />
      <span>返回</span>
    </button>

    <div v-if="article" class="grid grid-cols-1 lg:grid-cols-2 gap-6">
      <!-- 文章信息 -->
      <div class="bg-white rounded-lg shadow p-6 space-y-4 h-fit">
        <h1 class="text-2xl font-bold text-gray-900">{{ article.title }}</h1>
        
        <div class="flex items-center space-x-6 text-sm text-gray-600">
          <div>来源：{{ article.sourceName }}</div>
          <div v-if="article.author">作者：{{ article.author }}</div>
          <div>发布时间：{{ formatDateTime(article.pubDate) }}</div>
        </div>

        <div v-if="article.categories" class="flex items-center space-x-2">
          <span class="text-sm text-gray-600">分类：</span>
          <Badge v-for="(cat, index) in article.categories.split(',')" :key="index">
            {{ cat.trim() }}
          </Badge>
        </div>

        <a
          :href="article.link"
          target="_blank"
          class="inline-flex items-center space-x-1 text-blue-600 hover:text-blue-800"
        >
          <ExternalLink class="w-4 h-4" />
          <span>查看原文</span>
        </a>

        <div v-if="article.description" class="pt-4 border-t border-gray-200">
          <h3 class="text-sm font-medium text-gray-700 mb-2">摘要</h3>
          <p class="text-gray-600">{{ article.description }}</p>
        </div>

        <div v-if="article.content" class="pt-4 border-t border-gray-200">
          <div class="flex items-center justify-between mb-2">
            <h3 class="text-sm font-medium text-gray-700">内容</h3>
            <button
              @click="showMarkdown = !showMarkdown"
              class="text-sm text-blue-600 hover:text-blue-800"
            >
              {{ showMarkdown ? '查看源码' : '查看渲染' }}
            </button>
          </div>
          <div
            v-if="showMarkdown"
            class="prose prose-sm max-w-none text-gray-600"
            v-html="renderMarkdown(article.content)"
          />
          <pre v-else class="text-sm bg-gray-50 p-4 rounded-lg overflow-x-auto"><code>{{ article.content }}</code></pre>
        </div>
      </div>

      <!-- 文章增强信息 -->
      <div class="space-y-6">
        <div v-if="articleExtra" class="bg-white rounded-lg shadow p-6 space-y-4">
          <div class="flex items-center justify-between">
            <h2 class="text-lg font-semibold text-gray-900">AI 增强信息</h2>
            <div class="flex items-center space-x-2">
              <Badge :type="getStatusBadgeType(articleExtra.status)">
                {{ getStatusText(articleExtra.status) }}
              </Badge>
              <button
                v-if="articleExtra.status === 'FAILED'"
                @click="handleRegenerate"
                :disabled="regenerating"
                class="p-1 text-blue-600 hover:text-blue-800 disabled:opacity-50 disabled:cursor-not-allowed"
                title="重新生成"
              >
                <RefreshCw class="w-4 h-4" :class="{ 'animate-spin': regenerating }" />
              </button>
            </div>
          </div>

          <div v-if="articleExtra.status === 'SUCCESS'" class="space-y-4">
            <div v-if="articleExtra.overview">
              <h3 class="text-sm font-medium text-gray-700 mb-2">概览</h3>
              <div class="prose prose-sm max-w-none text-gray-600" v-html="renderMarkdown(articleExtra.overview)"></div>
            </div>

            <div v-if="articleExtra.keyInformation && articleExtra.keyInformation.length > 0">
              <h3 class="text-sm font-medium text-gray-700 mb-2">关键信息</h3>
              <ul class="list-disc list-inside space-y-1 text-gray-600">
                <li v-for="(info, index) in articleExtra.keyInformation" :key="index">
                  {{ info }}
                </li>
              </ul>
            </div>

            <div v-if="articleExtra.tags && articleExtra.tags.length > 0">
              <h3 class="text-sm font-medium text-gray-700 mb-2">标签</h3>
              <div class="flex flex-wrap gap-2">
                <Badge v-for="(tag, index) in articleExtra.tags" :key="index" type="info">
                  {{ tag }}
                </Badge>
              </div>
            </div>
          </div>

          <div v-else-if="articleExtra.errorMessage" class="text-sm text-red-600">
            错误：{{ articleExtra.errorMessage }}
          </div>
        </div>

        <!-- 分析结果列表 -->
        <div class="bg-white rounded-lg shadow p-6 space-y-4">
          <div class="flex items-center justify-between">
            <h2 class="text-lg font-semibold text-gray-900">分析结果</h2>
          </div>
          
          <div class="overflow-x-auto">
            <table class="min-w-full divide-y divide-gray-200">
              <thead class="bg-gray-50">
                <tr>
                  <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase whitespace-nowrap">ID</th>
                  <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase whitespace-nowrap">实验名称</th>
                  <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase whitespace-nowrap">状态</th>
                  <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase whitespace-nowrap">创建时间</th>
                  <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase whitespace-nowrap">操作</th>
                </tr>
              </thead>
              <tbody class="bg-white divide-y divide-gray-200">
                <tr v-if="analysisResults.length === 0">
                  <td colspan="5" class="px-6 py-4 text-center text-gray-500">暂无分析结果</td>
                </tr>
                <tr v-else v-for="result in analysisResults" :key="result.id" class="hover:bg-gray-50">
                  <td class="px-6 py-4 text-sm text-gray-900 whitespace-nowrap">{{ result.id }}</td>
                  <td class="px-6 py-4 text-sm text-gray-900 whitespace-nowrap">{{ result.experimentName }}</td>
                  <td class="px-6 py-4 whitespace-nowrap">
                    <Badge :type="getStatusBadgeType(result.status)">
                      {{ getStatusText(result.status) }}
                    </Badge>
                  </td>
                  <td class="px-6 py-4 text-sm text-gray-500 whitespace-nowrap">{{ formatDateTime(result.createdAt) }}</td>
                  <td class="px-6 py-4 whitespace-nowrap">
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
            v-if="analysisResults.length > 0"
            :current-page="pagination.page"
            :total-pages="pagination.totalPages"
            :total-elements="pagination.totalElements"
            :page-size="pagination.size"
            @update:page="handlePageChange"
          />
        </div>
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

          <!-- 分析结果 -->
          <div v-if="currentResult.status === 'SUCCESS'">
            <h3 class="text-sm font-medium text-gray-900 mb-3">分析结果</h3>
            <div class="prose prose-sm max-w-none text-gray-600" v-html="renderMarkdown(currentResult.result)"></div>
          </div>

          <!-- 错误信息 -->
          <div v-else-if="currentResult.errorMessage">
            <h3 class="text-sm font-medium text-gray-900 mb-3">错误信息</h3>
            <div class="text-sm text-red-600 bg-red-50 p-4 rounded-lg">
              {{ currentResult.errorMessage }}
            </div>
          </div>
        </div>
      </template>
      <template #footer>
        <button
          @click="resultDialogVisible = false"
          class="px-4 py-2 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded-md hover:bg-gray-50"
        >
          关闭
        </button>
      </template>
    </Dialog>
  </div>
</template>
