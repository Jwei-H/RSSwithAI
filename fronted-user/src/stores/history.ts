import { reactive, computed } from 'vue'

const STORAGE_KEY = 'rss_reading_history'
const EXPIRY_DAYS = 30

export interface ReadingHistoryItem {
  articleId: number
  title: string
  sourceName: string
  coverImage?: string | null
  pubDate: string
  readAt: string
  readProgress: number
}

interface HistoryState {
  items: Map<number, ReadingHistoryItem>
}

const state = reactive<HistoryState>({
  items: new Map()
})

// 从 localStorage 加载
function loadFromStorage(): void {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return
    const arr: ReadingHistoryItem[] = JSON.parse(raw)
    state.items = new Map(arr.map(item => [item.articleId, item]))
    cleanExpired()
  } catch {
    state.items = new Map()
  }
}

// 保存到 localStorage
function saveToStorage(): void {
  try {
    const arr = Array.from(state.items.values())
    localStorage.setItem(STORAGE_KEY, JSON.stringify(arr))
  } catch {
    // ignore
  }
}

// 清理过期记录
function cleanExpired(): void {
  const now = Date.now()
  const expiryMs = EXPIRY_DAYS * 24 * 60 * 60 * 1000
  let changed = false
  for (const [id, item] of state.items) {
    const readTime = new Date(item.readAt).getTime()
    if (now - readTime > expiryMs) {
      state.items.delete(id)
      changed = true
    }
  }
  if (changed) saveToStorage()
}

// 初始化
loadFromStorage()

export function useHistoryStore() {
  const historyList = computed(() => {
    const arr = Array.from(state.items.values())
    arr.sort((a, b) => new Date(b.readAt).getTime() - new Date(a.readAt).getTime())
    return arr
  })

  const addReading = (data: {
    articleId: number
    title: string
    sourceName: string
    coverImage?: string | null
    pubDate: string
  }) => {
    const existing = state.items.get(data.articleId)
    const item: ReadingHistoryItem = {
      ...data,
      readAt: new Date().toISOString(),
      readProgress: existing?.readProgress ?? 0
    }
    state.items.set(data.articleId, item)
    saveToStorage()
  }

  const updateProgress = (articleId: number, progress: number) => {
    const item = state.items.get(articleId)
    if (!item) return
    // 只更新更大的进度值
    if (progress > item.readProgress) {
      item.readProgress = Math.min(1, progress)
      item.readAt = new Date().toISOString()
      saveToStorage()
    }
  }

  const isRead = (articleId: number): boolean => {
    return state.items.has(articleId)
  }

  const getProgress = (articleId: number): number => {
    return state.items.get(articleId)?.readProgress ?? 0
  }

  const getItem = (articleId: number): ReadingHistoryItem | undefined => {
    return state.items.get(articleId)
  }

  const clearAll = () => {
    state.items.clear()
    saveToStorage()
  }

  return {
    historyList,
    addReading,
    updateProgress,
    isRead,
    getProgress,
    getItem,
    clearAll
  }
}
