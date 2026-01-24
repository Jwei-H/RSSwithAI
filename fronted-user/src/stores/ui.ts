import { reactive } from 'vue'

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
  const openDetail = (articleId: number, container?: HTMLElement | null) => {
    state.articleId = articleId
    state.fromContainer = container ?? null
    state.fromScrollTop = container?.scrollTop ?? 0
  }

  const closeDetail = () => {
    const container = state.fromContainer
    const scrollTop = state.fromScrollTop
    state.articleId = null
    state.fromContainer = null
    state.fromScrollTop = 0

    if (container) {
      requestAnimationFrame(() => {
        container.scrollTop = scrollTop
      })
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
    closeDetail
  }
}
