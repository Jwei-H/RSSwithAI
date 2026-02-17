<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ArticleDetailPane from '../components/articles/ArticleDetailPane.vue'
import EmptyState from '../components/common/EmptyState.vue'
import { useUiStore } from '../stores/ui'
import { useHistoryStore } from '../stores/history'
import { formatRelativeTime } from '../utils/time'
import { rewriteUrl } from '../utils/url-rewrites'
import { unescapeUrl } from '../utils/text'
import { History, Trash2, Lightbulb } from 'lucide-vue-next'

const ui = useUiStore()
const historyStore = useHistoryStore()
const route = useRoute()
const router = useRouter()

const listContainer = ref<HTMLElement | null>(null)

const detailOpen = computed(() => ui.detailOpen)

const onOpenArticle = (id: number) => {
  // 更新 URL 查询参数
  const query = { ...route.query, articleId: String(id) }
  router.push({ path: route.path, query }).catch(() => {
    // 忽略导航被中止的错误
  })

  ui.openDetail(id, listContainer.value)
}

const onClearHistory = () => {
  if (confirm('确定要清空所有浏览历史吗？')) {
    historyStore.clearAll()
  }
}

onMounted(() => {
  // 恢复文章详情状态
  const articleId = route.query.articleId
  if (articleId && parseInt(String(articleId), 10) > 0) {
    ui.restoreDetailFromUrl(parseInt(String(articleId), 10))
  }
})

// 监听路由变化，支持浏览器后退键
watch(
  () => route.query.articleId,
  () => {
    ui.syncWithRoute()
  }
)
</script>

<template>
  <!-- 移动端文章详情覆盖层 -->
  <div v-if="detailOpen" class="fixed inset-0 z-[60] flex flex-col bg-background md:hidden">
    <ArticleDetailPane :articleId="ui.detailArticleId" :onClose="ui.closeDetail"
      :onOpenArticle="(id) => ui.openDetail(id, listContainer)" />
  </div>

  <div class="flex h-full flex-col gap-4 overflow-hidden px-4 py-4 md:grid md:h-screen md:gap-6 md:px-6 md:py-6"
    :class="detailOpen ? 'md:grid-cols-[200px_1fr]' : 'md:grid-cols-[280px_1fr]'">
    <!-- 桌面端侧边栏 -->
    <section class="hidden h-full flex-col gap-4 overflow-hidden md:flex">
      <div class="rounded-2xl border border-border bg-card p-4">
        <div class="flex items-center gap-2">
          <History class="h-4 w-4 text-primary" />
          <h2 class="text-sm font-semibold text-foreground">浏览历史</h2>
        </div>
        <p class="mt-2 text-xs text-muted-foreground">记录最近 30 天的阅读</p>
      </div>
      <div class="rounded-2xl border border-border bg-card p-4">
        <div class="flex items-center gap-2">
          <Lightbulb class="h-4 w-4 text-primary" />
          <h3 class="text-sm font-semibold text-foreground">提示</h3>
        </div>
        <ul class="mt-3 list-decimal pl-4 text-xs text-muted-foreground">
          <li>历史记录仅保存在本地</li>
          <li>阅读进度会自动记录</li>
          <li>30 天后自动清理</li>
        </ul>
        <button v-if="historyStore.historyList.value.length > 0"
          class="mt-4 w-full flex items-center justify-center gap-2 rounded-xl border border-border px-3 py-2 text-xs text-muted-foreground hover:text-foreground hover:bg-muted"
          @click="onClearHistory">
          <Trash2 class="h-3.5 w-3.5" />
          清空历史
        </button>
      </div>
    </section>

    <!-- 主内容区域 -->
    <section class="flex flex-1 flex-col gap-4 overflow-hidden">
      <!-- 桌面端文章详情 -->
      <ArticleDetailPane v-if="detailOpen" class="hidden md:flex" :articleId="ui.detailArticleId"
        :onClose="ui.closeDetail" :onOpenArticle="(id) => ui.openDetail(id, listContainer)" />
      <template v-if="!detailOpen">
        <div class="rounded-2xl border border-border bg-card p-3 md:p-4">
          <div class="flex items-center justify-between">
            <div class="flex items-center gap-2">
              <History class="h-4 w-4 text-primary" />
              <h2 class="text-sm font-semibold text-foreground">阅读历史</h2>
            </div>
            <span class="text-xs text-muted-foreground">
              {{ historyStore.historyList.value.length }} 条记录
            </span>
          </div>
          <!-- 移动端清空历史按钮 -->
          <div v-if="historyStore.historyList.value.length > 0" class="mt-3 md:hidden">
            <button
              class="flex items-center gap-1.5 rounded-xl border border-border px-3 py-2 text-xs text-muted-foreground hover:text-foreground hover:bg-muted"
              @click="onClearHistory">
              <Trash2 class="h-3.5 w-3.5" />
              清空历史
            </button>
          </div>
        </div>

        <div ref="listContainer" class="flex-1 space-y-3 overflow-y-auto scrollbar-thin">
          <EmptyState v-if="!historyStore.historyList.value.length" title="暂无阅读历史" description="阅读文章后会自动记录" />
          <div v-else class="space-y-3">
            <div v-for="item in historyStore.historyList.value" :key="item.articleId"
              class="group flex cursor-pointer gap-3 rounded-2xl border border-border bg-card p-3 transition hover:shadow md:gap-4 md:p-4"
              @click="onOpenArticle(item.articleId)">
              <div v-if="item.coverImage"
                class="h-14 w-20 shrink-0 overflow-hidden rounded-xl border border-border md:h-16 md:w-24">
                <img :src="rewriteUrl(unescapeUrl(item.coverImage))" :alt="item.title"
                  class="h-full w-full object-cover" loading="lazy" referrerpolicy="no-referrer" />
              </div>
              <div class="flex min-w-0 flex-1 flex-col gap-1 md:gap-2">
                <div class="flex items-start justify-between gap-2 md:gap-4">
                  <h3 class="line-clamp-2 text-sm font-semibold text-foreground">{{ item.title }}</h3>
                  <span class="flex-shrink-0 text-xs text-muted-foreground">{{ formatRelativeTime(item.readAt) }}</span>
                </div>
                <div class="flex items-center justify-between text-xs text-muted-foreground">
                  <span>{{ item.sourceName }}</span>
                  <div class="flex items-center gap-2">
                    <div class="h-1.5 w-16 rounded-full bg-muted overflow-hidden">
                      <div class="h-full bg-primary transition-all" :style="{ width: `${item.readProgress * 100}%` }" />
                    </div>
                    <span>{{ Math.round(item.readProgress * 100) }}%</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </template>
    </section>
  </div>
</template>