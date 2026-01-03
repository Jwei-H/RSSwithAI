<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { RefreshCw, Search, RotateCcw, ChevronDown } from 'lucide-vue-next'
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend
} from 'chart.js'
import { Line } from 'vue-chartjs'
import StatCard from '@/components/StatCard.vue'
import Select from '@/components/Select.vue'
import Pagination from '@/components/Pagination.vue'
import { getArticleStats, getArticles, getArticlesBySourceId } from '@/api/articles'
import { getRssSources } from '@/api/rss-sources'
import { formatDateTime } from '@/utils/date'
import type { Article, ArticleStats, RssSource } from '@/types'

ChartJS.register(
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend
)

const router = useRouter()

const loading = ref(false)
const stats = ref<ArticleStats>({
  total: 0,
  dailyCounts: []
})
const articles = ref<Article[]>([])
const sources = ref<RssSource[]>([])

const sourceOptions = computed(() => {
  return [
    { value: '', label: '全部' },
    ...sources.value.map(s => ({ value: s.id, label: s.name }))
  ]
})

const chartData = computed(() => {
  const sortedCounts = [...stats.value.dailyCounts]
    .sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime())
    .slice(-7) // Last 7 days
  
  return {
    labels: sortedCounts.map(item => item.date),
    datasets: [
      {
        label: '新增文章',
        backgroundColor: '#3b82f6',
        borderColor: '#3b82f6',
        data: sortedCounts.map(item => item.count),
        tension: 0.3
      }
    ]
  }
})

const chartOptions = {
  responsive: true,
  maintainAspectRatio: false,
  plugins: {
    legend: {
      display: false
    }
  },
  scales: {
    y: {
      beginAtZero: true,
      ticks: {
        stepSize: 1
      }
    }
  }
}

// 筛选条件
const filters = ref({
  sourceId: '',
  searchWord: ''
})

const pagination = ref({
  page: 0,
  size: 20,
  totalPages: 0,
  totalElements: 0
})

// 加载统计
const loadStats = async () => {
  try {
    stats.value = await getArticleStats()
  } catch (error) {
    console.error('加载统计失败:', error)
  }
}

// 加载RSS源列表(用于筛选)
const loadSources = async () => {
  try {
    const res = await getRssSources({ size: 1000 })
    sources.value = res.content
  } catch (error) {
    console.error('加载RSS源列表失败:', error)
  }
}

// 加载文章列表
const loadArticles = async () => {
  loading.value = true
  try {
    const params = {
      page: pagination.value.page,
      size: pagination.value.size,
      searchWord: filters.value.searchWord || undefined
    }
    
    let res
    if (filters.value.sourceId) {
      res = await getArticlesBySourceId(Number(filters.value.sourceId), params)
    } else {
      res = await getArticles(params)
    }
    
    articles.value = res.content
    pagination.value.totalPages = res.totalPages
    pagination.value.totalElements = res.totalElements
  } catch (error) {
    console.error('加载文章列表失败:', error)
  } finally {
    loading.value = false
  }
}

// 搜索
const handleSearch = () => {
  pagination.value.page = 0
  loadArticles()
}

// 重置
const handleReset = () => {
  filters.value = {
    sourceId: '',
    searchWord: ''
  }
  pagination.value.page = 0
  loadArticles()
}

// 刷新
const handleRefresh = async () => {
  await Promise.all([loadStats(), loadArticles()])
}

// 查看详情
const handleViewDetail = (article: Article) => {
  router.push(`/articles/${article.id}`)
}

// 分页
const handlePageChange = (page: number) => {
  pagination.value.page = page
  loadArticles()
}

onMounted(() => {
  loadStats()
  loadSources()
  loadArticles()
})
</script>

<template>
  <div class="space-y-6">
    <!-- 统计卡片 -->
    <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
      <StatCard title="文章总数" :value="stats.total" type="primary" />
      <div class="bg-white rounded-lg border-2 border-gray-200 p-6 h-64">
        <div class="text-sm font-medium text-gray-600 mb-3">最近7天新增趋势</div>
        <div class="h-48">
          <Line :data="chartData" :options="chartOptions" />
        </div>
      </div>
    </div>

    <!-- 筛选栏 -->
    <div class="bg-white rounded-lg shadow p-4">
      <div class="grid grid-cols-1 md:grid-cols-4 gap-4">
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">RSS源</label>
          <Select
            v-model="filters.sourceId"
            :options="sourceOptions"
          />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">搜索关键词</label>
          <input
            v-model="filters.searchWord"
            type="text"
            placeholder="搜索标题或作者"
            class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500"
            @keyup.enter="handleSearch"
          />
        </div>
        <div class="flex items-end space-x-2">
          <button
            @click="handleSearch"
            class="flex items-center space-x-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
          >
            <Search class="w-4 h-4" />
            <span>搜索</span>
          </button>
          <button
            @click="handleReset"
            class="flex items-center space-x-2 px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-50"
          >
            <RotateCcw class="w-4 h-4" />
            <span>重置</span>
          </button>
        </div>
        <div class="flex items-end justify-end">
          <button
            @click="handleRefresh"
            class="flex items-center space-x-2 px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-50"
          >
            <RefreshCw class="w-4 h-4" />
            <span>刷新</span>
          </button>
        </div>
      </div>
    </div>

    <!-- 文章列表 -->
    <div class="bg-white rounded-lg shadow overflow-hidden">
      <div class="overflow-x-auto">
        <table class="min-w-full divide-y divide-gray-200">
          <thead class="bg-gray-50">
            <tr>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase whitespace-nowrap">ID</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase whitespace-nowrap">来源</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase min-w-[200px]">标题</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase whitespace-nowrap">作者</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase whitespace-nowrap">发布日期</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase whitespace-nowrap">分类</th>
            </tr>
          </thead>
          <tbody class="bg-white divide-y divide-gray-200">
            <tr v-if="loading">
              <td colspan="7" class="px-6 py-4 text-center text-gray-500">加载中...</td>
            </tr>
            <tr v-else-if="articles.length === 0">
              <td colspan="7" class="px-6 py-4 text-center text-gray-500">暂无数据</td>
            </tr>
            <tr
              v-else
              v-for="article in articles"
              :key="article.id"
              class="hover:bg-gray-50 cursor-pointer"
              @click="handleViewDetail(article)"
            >
              <td class="px-6 py-4 text-sm text-gray-900 whitespace-nowrap">{{ article.id }}</td>
              <td class="px-6 py-4 text-sm text-gray-500 whitespace-nowrap">{{ article.sourceName }}</td>
              <td class="px-6 py-4 text-sm font-medium text-blue-600 hover:text-blue-800 max-w-md truncate">
                {{ article.title }}
              </td>
              <td class="px-6 py-4 text-sm text-gray-500 whitespace-nowrap">{{ article.author || '-' }}</td>
              <td class="px-6 py-4 text-sm text-gray-500 whitespace-nowrap">{{ formatDateTime(article.pubDate) }}</td>
              <td class="px-6 py-4 text-sm text-gray-500 whitespace-nowrap">
                {{ article.categories || '-' }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- 分页 -->
      <Pagination
        v-if="articles.length > 0"
        :current-page="pagination.page"
        :total-pages="pagination.totalPages"
        :total-elements="pagination.totalElements"
        :page-size="pagination.size"
        @update:page="handlePageChange"
      />
    </div>
  </div>
</template>
