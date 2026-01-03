<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Lock, Save, Plus, Trash2 } from 'lucide-vue-next'
import Badge from '@/components/Badge.vue'
import Select from '@/components/Select.vue'
import {
  getPromptTemplateById,
  getPromptVersion,
  updatePromptVersion,
  freezePromptVersion,
  createPromptVersion,
  deletePromptTemplate
} from '@/api/prompts'
import { formatDateTime } from '@/utils/date'
import type { PromptTemplate, PromptVersion } from '@/types'

const route = useRoute()
const router = useRouter()

const template = ref<PromptTemplate | null>(null)
const currentVersion = ref<PromptVersion | null>(null)
const selectedVersionNum = ref(1)
const content = ref('')
const saving = ref(false)

const loadTemplate = async () => {
  try {
    const id = Number(route.params.id)
    template.value = await getPromptTemplateById(id)
    selectedVersionNum.value = template.value.latestVersion
    await loadVersion()
  } catch (error) {
    console.error('加载模板失败:', error)
    alert('加载模板失败')
    router.back()
  }
}

const loadVersion = async () => {
  try {
    const id = Number(route.params.id)
    currentVersion.value = await getPromptVersion(id, selectedVersionNum.value)
    content.value = currentVersion.value.content
  } catch (error) {
    console.error('加载版本失败:', error)
  }
}

const handleVersionChange = async () => {
  await loadVersion()
}

const handleSave = async () => {
  if (!currentVersion.value || currentVersion.value.immutable) return
  
  saving.value = true
  try {
    const id = Number(route.params.id)
    await updatePromptVersion(id, selectedVersionNum.value, { content: content.value })
    alert('保存成功')
    await loadVersion()
  } catch (error) {
    console.error('保存失败:', error)
    alert('保存失败')
  } finally {
    saving.value = false
  }
}

const handleFreeze = async () => {
  if (!confirm('确定要锁定当前版本吗？锁定后将无法修改。')) return
  
  try {
    const id = Number(route.params.id)
    await freezePromptVersion(id, selectedVersionNum.value)
    alert('锁定成功')
    await loadTemplate()
  } catch (error) {
    console.error('锁定失败:', error)
    alert('锁定失败')
  }
}

const handleCreateVersion = async () => {
  if (!template.value?.latestVersionDetail?.immutable) {
    alert('请先锁定当前版本')
    return
  }
  
  try {
    const id = Number(route.params.id)
    await createPromptVersion(id)
    alert('创建新版本成功')
    await loadTemplate()
  } catch (error) {
    console.error('创建版本失败:', error)
    alert('创建版本失败')
  }
}

const handleDelete = async () => {
  if (!confirm('确定要删除这个模板吗？')) return
  
  try {
    const id = Number(route.params.id)
    await deletePromptTemplate(id)
    router.push('/prompts')
  } catch (error) {
    console.error('删除失败:', error)
    alert('删除失败')
  }
}

const versionOptions = computed(() => {
  if (!template.value) return []
  const versions = []
  for (let i = template.value.latestVersion; i >= 1; i--) {
    versions.push({ value: i, label: `v${i}` })
  }
  return versions
})

const canEdit = computed(() => {
  return currentVersion.value && !currentVersion.value.immutable
})

const canCreateVersion = computed(() => {
  return template.value?.latestVersionDetail?.immutable
})

onMounted(() => {
  loadTemplate()
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

    <div v-if="template" class="space-y-6">
      <!-- 模板信息 -->
      <div class="bg-white rounded-lg shadow p-6">
        <div class="flex items-center justify-between mb-4">
          <div>
            <h1 class="text-2xl font-bold text-gray-900">{{ template.name }}</h1>
            <p v-if="template.description" class="text-gray-600 mt-1">{{ template.description }}</p>
            <div class="mt-2 text-sm text-gray-500">最新版本: v{{ template.latestVersion }}</div>
          </div>
          <div class="flex items-center space-x-2">
            <button
              v-if="canCreateVersion"
              @click="handleCreateVersion"
              class="flex items-center space-x-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
            >
              <Plus class="w-4 h-4" />
              <span>创建新版本</span>
            </button>
            <button
              @click="handleDelete"
              class="flex items-center space-x-2 px-4 py-2 bg-red-600 text-white rounded-lg hover:bg-red-700"
            >
              <Trash2 class="w-4 h-4" />
              <span>删除模板</span>
            </button>
          </div>
        </div>
      </div>

      <!-- 版本编辑区域 -->
      <div v-if="currentVersion" class="bg-white rounded-lg shadow p-6 space-y-4">
        <div class="flex items-center justify-between">
          <div class="flex items-center space-x-4">
            <label class="text-sm font-medium text-gray-700">选择版本:</label>
            <Select
              v-model="selectedVersionNum"
              :options="versionOptions"
              @update:model-value="handleVersionChange"
              class="w-32"
            />
            <Badge :type="currentVersion.immutable ? 'default' : 'warning'">
              {{ currentVersion.immutable ? '已锁定' : '可修改' }}
            </Badge>
            <span class="text-sm text-gray-500">
              创建时间: {{ formatDateTime(currentVersion.createdAt) }}
            </span>
          </div>
        </div>

        <div>
          <label class="block text-sm font-medium text-gray-700 mb-2">提示词内容</label>
          <textarea
            v-model="content"
            :readonly="!canEdit"
            rows="20"
            class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 font-mono text-sm"
            :class="{ 'bg-gray-50': !canEdit }"
          />
        </div>

        <div class="flex items-center justify-end space-x-3">
          <button
            v-if="canEdit"
            @click="handleSave"
            :disabled="saving"
            class="flex items-center space-x-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50"
          >
            <Save class="w-4 h-4" />
            <span>{{ saving ? '保存中...' : '保存' }}</span>
          </button>
          <button
            v-if="canEdit"
            @click="handleFreeze"
            class="flex items-center space-x-2 px-4 py-2 bg-gray-600 text-white rounded-lg hover:bg-gray-700"
          >
            <Lock class="w-4 h-4" />
            <span>锁定版本</span>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
