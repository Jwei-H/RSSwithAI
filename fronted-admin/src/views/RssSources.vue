<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { RefreshCw, Plus, Play, Eye, Edit, Trash2, Power, PowerOff, ChevronDown } from 'lucide-vue-next'
import StatCard from '@/components/StatCard.vue'
import Badge from '@/components/Badge.vue'
import Dialog from '@/components/Dialog.vue'
import Select from '@/components/Select.vue'
import Pagination from '@/components/Pagination.vue'
import {
  getRssSourceStats,
  getRssSources,
  getRssSourceById,
  createRssSource,
  updateRssSource,
  deleteRssSource,
  enableRssSource,
  disableRssSource,
  fetchRssSource,
  fetchAllRssSources
} from '@/api/rss-sources'
import { formatDateTime } from '@/utils/date'
import type { RssSource, RssSourceStats, SourceType, SourceStatus, FetchStatus, SourceCategory } from '@/types'

// 数据
const loading = ref(false)
const stats = ref<RssSourceStats>({
  total: 0,
  statusCounts: { SUCCESS: 0, FAILED: 0, NEVER: 0, FETCHING: 0 }
})
const sources = ref<RssSource[]>([])
const pagination = ref({
  page: 0,
  size: 20,
  totalPages: 0,
  totalElements: 0
})

// 对话框
const dialogVisible = ref(false)
const detailDialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const currentSource = ref<Partial<RssSource>>({})
const formData = ref({
  name: '',
  url: '',
  type: 'ORIGIN' as SourceType,
  description: '',
  icon: '',
  fetchIntervalMinutes: 30,
  category: 'OTHER' as SourceCategory
})

const typeOptions = [
  { value: 'ORIGIN', label: '原始RSS源' },
  { value: 'RSSHUB', label: 'RSSHub源' }
]

const categoryOptions = [
  { value: 'NEWS', label: '新闻' },
  { value: 'TECH', label: '科技' },
  { value: 'SOCIETY', label: '社会' },
  { value: 'FINANCE', label: '财经' },
  { value: 'LIFESTYLE', label: '生活' },
  { value: 'OTHER', label: '其他' }
]

// 加载统计数据
const loadStats = async () => {
  try {
    stats.value = await getRssSourceStats()
  } catch (error) {
    console.error('加载统计数据失败:', error)
  }
}

// 加载列表
const loadList = async () => {
  loading.value = true
  try {
    const res = await getRssSources({
      page: pagination.value.page,
      size: pagination.value.size
    })
    sources.value = res.content
    pagination.value.totalPages = res.totalPages
    pagination.value.totalElements = res.totalElements
  } catch (error) {
    console.error('加载列表失败:', error)
  } finally {
    loading.value = false
  }
}

// 刷新
const handleRefresh = async () => {
  await Promise.all([loadStats(), loadList()])
}

// 抓取全部
const handleFetchAll = async () => {
  try {
    await fetchAllRssSources()
    alert('已开始抓取所有RSS源')
  } catch (error) {
    console.error('抓取失败:', error)
    alert('抓取失败')
  }
}

// 打开新增对话框
const handleCreate = () => {
  dialogMode.value = 'create'
  formData.value = {
    name: '',
    url: '',
    type: 'ORIGIN',
    description: '',
    icon: '',
    fetchIntervalMinutes: 30,
    category: 'OTHER'
  }
  dialogVisible.value = true
}

// 打开编辑对话框
const handleEdit = async (source: RssSource) => {
  dialogMode.value = 'edit'
  currentSource.value = source
  formData.value = {
    name: source.name,
    url: source.url,
    type: source.type,
    description: source.description || '',
    icon: source.icon || '',
    fetchIntervalMinutes: source.fetchIntervalMinutes,
    category: source.category || 'OTHER'
  }
  dialogVisible.value = true
}

// 查看详情
const handleDetail = async (source: RssSource) => {
  try {
    currentSource.value = await getRssSourceById(source.id)
    detailDialogVisible.value = true
  } catch (error) {
    console.error('加载详情失败:', error)
  }
}

// 保存
const handleSave = async () => {
  try {
    if (dialogMode.value === 'create') {
      await createRssSource(formData.value)
    } else {
      await updateRssSource(currentSource.value.id!, formData.value)
    }
    dialogVisible.value = false
    await handleRefresh()
  } catch (error) {
    console.error('保存失败:', error)
    alert('保存失败')
  }
}

// 删除
const handleDelete = async (id: number) => {
  if (!confirm('确定要删除这个RSS源吗？')) return

  try {
    await deleteRssSource(id)
    await handleRefresh()
  } catch (error) {
    console.error('删除失败:', error)
    alert('删除失败')
  }
}

// 启用/禁用
const handleToggleStatus = async (source: RssSource) => {
  try {
    if (source.status === 'ENABLED') {
      await disableRssSource(source.id)
    } else {
      await enableRssSource(source.id)
    }
    await handleRefresh()
  } catch (error) {
    console.error('状态切换失败:', error)
    alert('状态切换失败')
  }
}

// 手动抓取
const handleFetch = async (id: number) => {
  try {
    await fetchRssSource(id)
    alert('已开始抓取')
  } catch (error) {
    console.error('抓取失败:', error)
    alert('抓取失败')
  }
}

// 分页
const handlePageChange = (page: number) => {
  pagination.value.page = page
  loadList()
}

// 状态显示
const getStatusBadgeType = (status: SourceStatus) => {
  return status === 'ENABLED' ? 'success' : 'default'
}

const getFetchStatusBadgeType = (status: FetchStatus) => {
  switch (status) {
    case 'SUCCESS': return 'success'
    case 'FAILED': return 'error'
    case 'FETCHING': return 'info'
    default: return 'default'
  }
}

const getStatusText = (status: SourceStatus) => {
  return status === 'ENABLED' ? '启用' : '禁用'
}

const getFetchStatusText = (status: FetchStatus) => {
  const map = {
    SUCCESS: '成功',
    FAILED: '失败',
    NEVER: '从未',
    FETCHING: '抓取中'
  }
  return map[status]
}

const getTypeText = (type: SourceType) => {
  return type === 'ORIGIN' ? '原始RSS源' : 'RSSHub源'
}

const getCategoryText = (category: SourceCategory) => {
  const map: Record<SourceCategory, string> = {
    NEWS: '新闻',
    TECH: '科技',
    SOCIETY: '社会',
    FINANCE: '财经',
    LIFESTYLE: '生活',
    OTHER: '其他'
  }
  return map[category] || '未知'
}

onMounted(() => {
  handleRefresh()
})
</script>

<template>
  <div class="space-y-6">
    <!-- 统计卡片 -->
    <div class="grid grid-cols-1 md:grid-cols-5 gap-4">
      <StatCard title="RSS源总数" :value="stats.total" type="primary" />
      <StatCard title="成功抓取" :value="stats.statusCounts.SUCCESS" type="success" />
      <StatCard title="抓取失败" :value="stats.statusCounts.FAILED" type="error" />
      <StatCard title="从未抓取" :value="stats.statusCounts.NEVER" type="default" />
      <StatCard title="正在抓取" :value="stats.statusCounts.FETCHING" type="info" />
    </div>

    <!-- 操作栏 -->
    <div class="flex items-center justify-between">
      <div class="flex items-center space-x-3">
        <button
          @click="handleCreate"
          class="flex items-center space-x-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
        >
          <Plus class="w-4 h-4" />
          <span>新增RSS源</span>
        </button>
        <button
          @click="handleFetchAll"
          class="flex items-center space-x-2 px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 transition-colors"
        >
          <Play class="w-4 h-4" />
          <span>抓取全部</span>
        </button>
      </div>
      <button
        @click="handleRefresh"
        class="flex items-center space-x-2 px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors"
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
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase whitespace-nowrap">ID</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase min-w-[150px]">名称</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase max-w-xs">URL</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase whitespace-nowrap">类型</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase whitespace-nowrap">抓取间隔</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase whitespace-nowrap">状态</th>
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase whitespace-nowrap">最后抓取状态</th>
<!--              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase whitespace-nowrap">最后抓取时间</th>-->
              <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase whitespace-nowrap">操作</th>
            </tr>
          </thead>
          <tbody class="bg-white divide-y divide-gray-200">
            <tr v-if="loading">
              <td colspan="9" class="px-6 py-4 text-center text-gray-500">加载中...</td>
            </tr>
            <tr v-else-if="sources.length === 0">
              <td colspan="9" class="px-6 py-4 text-center text-gray-500">暂无数据</td>
            </tr>
            <tr v-else v-for="source in sources" :key="source.id" class="hover:bg-gray-50">
              <td class="px-6 py-4 text-sm text-gray-900 whitespace-nowrap">{{ source.id }}</td>
              <td class="px-6 py-4 text-sm font-medium text-gray-900">{{ source.name }}</td>
              <td class="px-6 py-4 text-sm text-gray-500 max-w-xs truncate" :title="source.url">
                {{ source.url }}
              </td>
              <td class="px-6 py-4 text-sm text-gray-500 whitespace-nowrap">{{ getTypeText(source.type) }}</td>
              <td class="px-6 py-4 text-sm text-gray-500 whitespace-nowrap">{{ source.fetchIntervalMinutes }} 分钟</td>
              <td class="px-6 py-4 whitespace-nowrap">
                <Badge :type="getStatusBadgeType(source.status)">
                  {{ getStatusText(source.status) }}
                </Badge>
              </td>
              <td class="px-6 py-4 whitespace-nowrap">
                <Badge :type="getFetchStatusBadgeType(source.lastFetchStatus)">
                  {{ getFetchStatusText(source.lastFetchStatus) }}
                </Badge>
              </td>
<!--              <td class="px-6 py-4 text-sm text-gray-500 whitespace-nowrap">-->
<!--                {{ formatDateTime(source.lastFetchTime) }}-->
<!--              </td>-->
              <td class="px-6 py-4 text-sm space-x-2 whitespace-nowrap">
                <button
                  @click="handleDetail(source)"
                  class="text-blue-600 hover:text-blue-800"
                  title="详情"
                >
                  <Eye class="w-4 h-4" />
                </button>
                <button
                  @click="handleEdit(source)"
                  class="text-gray-600 hover:text-gray-800"
                  title="编辑"
                >
                  <Edit class="w-4 h-4" />
                </button>
                <button
                  @click="handleDelete(source.id)"
                  class="text-red-600 hover:text-red-800"
                  title="删除"
                >
                  <Trash2 class="w-4 h-4" />
                </button>
                <button
                  @click="handleToggleStatus(source)"
                  :class="source.status === 'ENABLED' ? 'text-orange-600 hover:text-orange-800' : 'text-green-600 hover:text-green-800'"
                  :title="source.status === 'ENABLED' ? '禁用' : '启用'"
                >
                  <PowerOff v-if="source.status === 'ENABLED'" class="w-4 h-4" />
                  <Power v-else class="w-4 h-4" />
                </button>
                <button
                  @click="handleFetch(source.id)"
                  class="text-purple-600 hover:text-purple-800"
                  title="手动抓取"
                >
                  <Play class="w-4 h-4" />
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- 分页 -->
      <Pagination
        v-if="sources.length > 0"
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
      :title="dialogMode === 'create' ? '新增RSS源' : '编辑RSS源'"
      @confirm="handleSave"
    >
      <div class="space-y-4">
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">名称 <span class="text-red-500">*</span></label>
          <input
            v-model="formData.name"
            type="text"
            required
            class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
          />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">URL <span class="text-red-500">*</span></label>
          <input
            v-model="formData.url"
            type="text"
            required
            class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
          />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">类型 <span class="text-red-500">*</span></label>
          <Select
            v-model="formData.type"
            :options="typeOptions"
          />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">分类</label>
          <Select
            v-model="formData.category"
            :options="categoryOptions"
          />
        </div>
        <div>
          <input
            v-model="formData.icon"
            type="text"
            class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
          />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">描述</label>
          <textarea
            v-model="formData.description"
            rows="3"
            class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
          />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">抓取间隔（分钟）</label>
          <input
            v-model.number="formData.fetchIntervalMinutes"
            type="number"
            min="1"
            class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
          />
        </div>
      </div>
    </Dialog>

    <!-- 详情对话框 -->
    <Dialog
      v-model:visible="detailDialogVisible"
      title="RSS源详情"
      width="700px"
      @confirm="detailDialogVisible = false"
    >
      <template #default>
        <div v-if="currentSource" class="space-y-4">
          <div class="grid grid-cols-2 gap-4">
            <div>
              <div class="text-sm font-medium text-gray-500">ID</div>
              <div class="mt-1 text-sm text-gray-900">{{ currentSource.id }}</div>
            </div>
            <div>
              <div class="text-sm font-medium text-gray-500">名称</div>
              <div class="mt-1 text-sm text-gray-900">{{ currentSource.name }}</div>
            </div>
            <div class="col-span-2">
              <div class="text-sm font-medium text-gray-500">URL</div>
              <div class="mt-1 text-sm text-gray-900 break-all">{{ currentSource.url }}</div>
            </div>
            <div class="col-span-2" v-if="currentSource.icon">
              <div class="text-sm font-medium text-gray-500">图标</div>
              <div class="mt-1 text-sm text-gray-900 break-all">{{ currentSource.icon }}</div>
            </div>
            <div>
              <div class="text-sm font-medium text-gray-500">类型</div>
              <div class="mt-1 text-sm text-gray-900">{{ getTypeText(currentSource.type!) }}</div>
            </div>
            <div>
              <div class="text-sm font-medium text-gray-500">分类</div>
              <div class="mt-1 text-sm text-gray-900">{{ getCategoryText(currentSource.category!) }}</div>
            </div>
            <div>
              <div class="mt-1 text-sm text-gray-900">{{ currentSource.fetchIntervalMinutes }} 分钟</div>
            </div>
            <div>
              <div class="text-sm font-medium text-gray-500">状态</div>
              <div class="mt-1">
                <Badge :type="getStatusBadgeType(currentSource.status!)">
                  {{ getStatusText(currentSource.status!) }}
                </Badge>
              </div>
            </div>
            <div>
              <div class="text-sm font-medium text-gray-500">最后抓取状态</div>
              <div class="mt-1">
                <Badge :type="getFetchStatusBadgeType(currentSource.lastFetchStatus!)">
                  {{ getFetchStatusText(currentSource.lastFetchStatus!) }}
                </Badge>
              </div>
            </div>
            <div>
              <div class="text-sm font-medium text-gray-500">最后抓取时间</div>
              <div class="mt-1 text-sm text-gray-900">{{ formatDateTime(currentSource.lastFetchTime) }}</div>
            </div>
            <div>
              <div class="text-sm font-medium text-gray-500">失败次数</div>
              <div class="mt-1 text-sm text-gray-900">{{ currentSource.failureCount }}</div>
            </div>
            <div v-if="currentSource.lastFetchError" class="col-span-2">
              <div class="text-sm font-medium text-gray-500">最后错误信息</div>
              <div class="mt-1 text-sm text-red-600 break-all">{{ currentSource.lastFetchError }}</div>
            </div>
            <div class="col-span-2" v-if="currentSource.description">
              <div class="text-sm font-medium text-gray-500">描述</div>
              <div class="mt-1 text-sm text-gray-900">{{ currentSource.description }}</div>
            </div>
            <div>
              <div class="text-sm font-medium text-gray-500">创建时间</div>
              <div class="mt-1 text-sm text-gray-900">{{ formatDateTime(currentSource.createdAt) }}</div>
            </div>
            <div>
              <div class="text-sm font-medium text-gray-500">更新时间</div>
              <div class="mt-1 text-sm text-gray-900">{{ formatDateTime(currentSource.updatedAt) }}</div>
            </div>
          </div>
        </div>
      </template>
    </Dialog>
  </div>
</template>