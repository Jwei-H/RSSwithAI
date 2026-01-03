<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Save, RotateCcw } from 'lucide-vue-next'
import { getSettings, updateSettings } from '@/api/settings'
import type { SystemSettings } from '@/types'

type SettingItem = {
  key: string
  value: string
  description?: string | null
}

type SystemSettings = Record<string, string | number>

const loading = ref(false)
const saving = ref(false)
const settings = ref<SystemSettings>({})
const originalSettings = ref<SystemSettings>({})

const coerceValue = (key: string, value: string): string | number => {
  // 这些字段在界面里用的是 v-model.number，需要转成 number 才能正确展示/编辑
  const numberKeys = new Set([
    'collector_fetch_interval',
    'collector_fetch_timeout',
    'collector_fetch_max_retries',
    'concurrent_limit'
  ])
  if (numberKeys.has(key)) {
    const n = Number(value)
    return Number.isFinite(n) ? n : value
  }
  return value
}

const normalizeKey = (key: string) => {
  // 兼容可能出现的驼峰 key，统一转成下划线
  return key.replace(/[A-Z]/g, letter => `_${letter.toLowerCase()}`)
}

const loadSettings = async () => {
  loading.value = true
  try {
    const res = await getSettings()
    const list = Array.isArray(res) ? (res as SettingItem[]) : []
    const normalized: SystemSettings = {}

    for (const item of list) {
      if (!item?.key) continue
      const k = normalizeKey(item.key)
      normalized[k] = coerceValue(k, String(item.value ?? ''))
    }

    settings.value = normalized
    originalSettings.value = { ...normalized }
    console.log('Loaded settings:', settings.value)
  } catch (error) {
    console.error('加载配置失败:', error)
  } finally {
    loading.value = false
  }
}

const handleSave = async () => {
  saving.value = true
  try {
    // 后端返回的是 { key, value } 列表，这里也按列表提交更匹配
    const payload: SettingItem[] = Object.entries(settings.value).map(([key, value]) => ({
      key,
      value: String(value ?? '')
    }))

    await updateSettings(payload)
    alert('保存成功')
    originalSettings.value = { ...settings.value }
  } catch (error) {
    console.error('保存失败:', error)
    alert('保存失败')
  } finally {
    saving.value = false
  }
}

const handleReset = () => {
  settings.value = { ...originalSettings.value }
}

onMounted(() => {
  loadSettings()
})
</script>

<template>
  <div class="space-y-6">
    <div v-if="loading" class="text-center text-gray-500">加载中...</div>

    <div v-else class="space-y-6">
      <!-- 采集器配置 -->
      <div class="bg-white rounded-lg shadow p-6">
        <h2 class="text-lg font-semibold text-gray-900 mb-4">采集器配置</h2>
        <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">抓取间隔（毫秒）</label>
            <input
              v-model.number="settings['collector_fetch_interval']"
              type="number"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500"
            />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">抓取超时（秒）</label>
            <input
              v-model.number="settings['collector_fetch_timeout']"
              type="number"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500"
            />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">最大重试次数</label>
            <input
              v-model.number="settings['collector_fetch_max_retries']"
              type="number"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500"
            />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">RSSHub 主机地址</label>
            <input
              v-model="settings['rsshub_host']"
              type="text"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500"
            />
          </div>
        </div>
      </div>

      <!-- LLM 配置 -->
      <div class="bg-white rounded-lg shadow p-6">
        <h2 class="text-lg font-semibold text-gray-900 mb-4">LLM 配置</h2>
        <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">LLM API 基础 URL</label>
            <input
              v-model="settings['llm_base_url']"
              type="text"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500"
            />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">LLM API 密钥</label>
            <input
              v-model="settings['llm_api_key']"
              type="password"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500"
            />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">语言模型 ID</label>
            <input
              v-model="settings['language_model_id']"
              type="text"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500"
            />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">向量模型 ID</label>
            <input
              v-model="settings['embedding_model_id']"
              type="text"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500"
            />
          </div>
        </div>
      </div>

      <!-- 内容增强配置 -->
      <div class="bg-white rounded-lg shadow p-6">
        <h2 class="text-lg font-semibold text-gray-900 mb-4">内容增强配置</h2>
        <div class="space-y-4">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">内容生成提示词模板</label>
            <textarea
              v-model="settings['llm_gen_prompt']"
              rows="14"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 font-mono text-sm"
            />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">模型参数配置 (JSON)</label>
            <textarea
              v-model="settings['llm_gen_model_config']"
              rows="6"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 font-mono text-sm"
            />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">并发处理限制</label>
            <input
              v-model.number="settings['concurrent_limit']"
              type="number"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500"
            />
          </div>
        </div>
      </div>

      <!-- 管理员配置 -->
      <div class="bg-white rounded-lg shadow p-6">
        <h2 class="text-lg font-semibold text-gray-900 mb-4">管理员配置</h2>
        <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">管理员用户名</label>
            <input
              v-model="settings['admin_username']"
              type="text"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500"
            />
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">管理员密码</label>
            <input
              v-model="settings['admin_password']"
              type="password"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500"
            />
            <p class="mt-1 text-xs text-gray-500">修改后需要重新登录</p>
          </div>
        </div>
      </div>

      <!-- 操作按钮 -->
      <div class="flex items-center justify-end space-x-3">
        <button
          @click="handleReset"
          class="flex items-center space-x-2 px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-50"
        >
          <RotateCcw class="w-4 h-4" />
          <span>重置配置</span>
        </button>
        <button
          @click="handleSave"
          :disabled="saving"
          class="flex items-center space-x-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50"
        >
          <Save class="w-4 h-4" />
          <span>{{ saving ? '保存中...' : '保存配置' }}</span>
        </button>
      </div>
    </div>
  </div>
</template>