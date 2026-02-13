<script setup lang="ts">
import { RouterView, useRoute } from 'vue-router'
import IconRail from './components/layout/IconRail.vue'
import MobileBottomNav from './components/layout/MobileBottomNav.vue'
import ArticleDetailOverlay from './components/articles/ArticleDetailOverlay.vue'
import ToastHost from './components/common/ToastHost.vue'
import { useUiStore } from './stores/ui'
import { computed, watch } from 'vue'

const ui = useUiStore()
const route = useRoute()
const showShell = computed(() => route.meta.shell !== false)
const showOverlay = computed(
  () =>
    ui.detailOpen &&
    !route.path.startsWith('/subscriptions') &&
    !route.path.startsWith('/discover') &&
    !route.path.startsWith('/favorites') &&
    !route.path.startsWith('/history')
)

watch(
  () => route.path,
  () => {
    if (ui.detailOpen) {
      ui.closeDetail()
    }
  }
)

watch(
  [() => route.path, () => ui.detailOpen],
  () => {
    if (ui.detailOpen) {
      document.title = '阅读 - RSSwithAI'
      return
    }

    if (route.path.startsWith('/subscriptions')) {
      document.title = '订阅 - RSSwithAI'
    } else if (route.path.startsWith('/discover')) {
      document.title = '发现 - RSSwithAI'
    } else if (route.path.startsWith('/favorites')) {
      document.title = '收藏 - RSSwithAI'
    } else if (route.path.startsWith('/history')) {
      document.title = '历史 - RSSwithAI'
    } else if (route.path.startsWith('/profile')) {
      document.title = '个人中心 - RSSwithAI'
    } else {
      document.title = 'RSSwithAI'
    }
  },
  { immediate: true }
)
</script>

<template>
  <div class="min-h-screen bg-muted/40 text-foreground">
    <ToastHost />
    <ArticleDetailOverlay v-if="showOverlay" />
    <div v-else class="min-h-screen">
      <div v-if="showShell" class="flex min-h-screen flex-col md:flex-row">
        <!-- 桌面端侧边导航 -->
        <IconRail class="hidden md:flex" />
        <!-- 移动端底部导航 -->
        <MobileBottomNav class="md:hidden" />
        <main class="min-h-screen flex-1 pb-16 md:pb-0">
          <RouterView v-slot="{ Component }">
            <KeepAlive>
              <component :is="Component" />
            </KeepAlive>
          </RouterView>
        </main>
      </div>
      <div v-else class="min-h-screen">
        <RouterView />
      </div>
    </div>
  </div>
</template>
