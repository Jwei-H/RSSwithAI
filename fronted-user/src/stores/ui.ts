import { reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'

type DetailState = {
  articleId: number | null
  fromContainer: HTMLElement | null
  fromScrollTop: number
}

const state = reactive<DetailState>({
  articleId: null,
  fromContainer: null,
  fromScrollTop: 0
})

export function useUiStore() {
  const router = useRouter()
  const route = useRoute()

  const openDetail = (articleId: number, container?: HTMLElement | null) => {
    state.articleId = articleId
    state.fromContainer = container ?? null
    state.fromScrollTop = container?.scrollTop ?? 0

    // 更新 URL 查询参数
    const query = { ...route.query, articleId: String(articleId) }
    router.push({ path: route.path, query }).catch(() => {
      // 忽略导航被中止的错误
    })
  }

  const closeDetail = () => {
    const container = state.fromContainer
    const scrollTop = state.fromScrollTop
    state.articleId = null
    state.fromContainer = null
    state.fromScrollTop = 0

    // 清除 URL 中的 articleId 参数
    const query = { ...route.query }
    delete query.articleId
    router.push({ path: route.path, query }).catch(() => {
      // 忽略导航被中止的错误
    })

    if (container) {
      requestAnimationFrame(() => {
        container.scrollTop = scrollTop
      })
    }
  }

  // 从 URL 查询参数恢复状态
  const restoreDetailFromUrl = (articleId: number | null) => {
    if (articleId && articleId > 0) {
      state.articleId = articleId
    }
  }

  // 监听路由变化，同步关闭状态（用于浏览器后退键）
  const syncWithRoute = () => {
    const articleId = route.query.articleId
    if (!articleId && state.articleId !== null) {
      // URL 中没有 articleId 但状态中有，说明用户点击了后退
      state.articleId = null
      state.fromContainer = null
      state.fromScrollTop = 0
    } else if (articleId && state.articleId === null) {
      // URL 中有 articleId 但状态中没有，说明用户点击了前进
      state.articleId = parseInt(String(articleId), 10)
    }
  }

  return {
    state,
    get detailOpen() {
      return state.articleId !== null
    },
    get detailArticleId() {
      return state.articleId
    },
    openDetail,
    closeDetail,
    restoreDetailFromUrl,
    syncWithRoute
  }
}


