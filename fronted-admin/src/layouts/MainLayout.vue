<script setup lang="ts">
import { RouterView } from 'vue-router'
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { removeToken } from '@/utils/auth'
import {
  Rss,
  FileText,
  Settings,
  MessageSquare,
  FlaskConical,
  Wrench,
  Menu,
  ArrowLeftToLine,
  LogOut
} from 'lucide-vue-next'

const router = useRouter()
const route = useRoute()
const sidebarOpen = ref(true)

const menuItems = [
  { path: '/rss-sources', name: 'RSS源管理', icon: Rss },
  { path: '/articles', name: '文章管理', icon: FileText },
  { path: '/model-configs', name: '模型配置', icon: Settings },
  { path: '/prompts', name: '提示词模板', icon: MessageSquare },
  { path: '/experiments', name: '实验管理', icon: FlaskConical },
  { path: '/settings', name: '系统配置', icon: Wrench }
]

const handleLogout = () => {
  removeToken()
  router.push('/login')
}
</script>

<template>
  <div class="flex h-screen bg-gray-50">
    <!-- 侧边栏 -->
    <aside :class="[
      'bg-white border-r border-gray-200 transition-all duration-300',
      sidebarOpen ? 'w-64' : 'w-20'
    ]">
      <div class="flex flex-col h-full">
        <!-- Logo 区域 -->
        <div class="flex items-center justify-between h-16 px-4 border-b border-gray-200">
          <div v-if="sidebarOpen" class="flex items-center space-x-2">
            <Rss class="w-8 h-8 text-blue-600" />
            <span class="text-xl font-bold text-gray-800">RSSwithAI</span>
          </div>
          <button @click="sidebarOpen = !sidebarOpen" class="p-2 rounded-lg hover:bg-gray-100 transition-colors">
            <Menu v-if="!sidebarOpen" class="w-5 h-5" />
            <ArrowLeftToLine v-else class="w-5 h-5" />
          </button>
        </div>

        <!-- 导航菜单 -->
        <nav class="flex-1 overflow-y-auto py-4">
          <router-link v-for="item in menuItems" :key="item.path" :to="item.path" :class="[
            'flex items-center px-4 py-3 mx-2 rounded-lg transition-colors',
            route.path === item.path || route.path.startsWith(item.path + '/')
              ? 'bg-blue-50 text-blue-600'
              : 'text-gray-700 hover:bg-gray-100'
          ]">
            <component :is="item.icon" class="w-5 h-5 flex-shrink-0" />
            <span v-if="sidebarOpen" class="ml-3">{{ item.name }}</span>
          </router-link>
        </nav>
      </div>
    </aside>

    <!-- 主内容区域 -->
    <div class="flex-1 flex flex-col overflow-hidden">
      <!-- 顶部导航栏 -->
      <header class="bg-white border-b border-gray-200 h-16">
        <div class="flex items-center justify-between h-full px-6">
          <h1 class="text-xl font-semibold text-gray-800">
            {{ route.meta.title || '管理系统' }}
          </h1>
          <button @click="handleLogout"
            class="flex items-center space-x-2 px-4 py-2 text-gray-700 hover:bg-gray-100 rounded-lg transition-colors">
            <LogOut class="w-5 h-5" />
            <span>退出登录</span>
          </button>
        </div>
      </header>

      <!-- 页面内容 -->
      <main class="flex-1 overflow-y-auto p-6">
        <RouterView />
      </main>
    </div>
  </div>
</template>
