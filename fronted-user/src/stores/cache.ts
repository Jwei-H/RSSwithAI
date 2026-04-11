import { reactive } from 'vue'
import type { ArticleDetail, ArticleExtra, ArticleFeed, HotEvent, RssSource, Subscription } from '../types'

const APP_CACHE_STORAGE_KEY = 'rss_app_cache_v2'
const MAX_ARTICLES = 120

/**
 * 分页缓存项接口
 */
interface PaginatedCacheItem {
    data: RssSource[]
    page: number
    last: boolean
}

interface FeedCacheItem {
    data: ArticleFeed[]
    cursor: string | null
    hasMore: boolean
}

interface FavoritesCacheData {
    items: ArticleFeed[]
    page: number
    last: boolean
}

// 单例状态
const state = reactive<{
    hotEvents: HotEvent[] | null
    rssSources: Map<string, PaginatedCacheItem[]>
    sourceArticles: Map<number, ArticleFeed[]>
    subscriptions: Subscription[] | null
    subscriptionFeeds: Map<string, FeedCacheItem>
    favorites: FavoritesCacheData | null
    articleDetails: Map<number, ArticleDetail>
    articleExtras: Map<number, ArticleExtra>
    wordCloud: Map<number, { text: string; value: number }[]>
}>({
    hotEvents: null,
    rssSources: new Map(),
    sourceArticles: new Map(),
    subscriptions: null,
    subscriptionFeeds: new Map(),
    favorites: null,
    articleDetails: new Map(),
    articleExtras: new Map(),
    wordCloud: new Map()
})

interface PersistentAppState {
    hotEvents: HotEvent[] | null
    rssSources: Array<[string, PaginatedCacheItem[]]>
    sourceArticles: Array<[number, ArticleFeed[]]>
    subscriptions: Subscription[] | null
    subscriptionFeeds: Array<[string, FeedCacheItem]>
    favorites: FavoritesCacheData | null
    articleDetails: Array<[number, ArticleDetail]>
    articleExtras: Array<[number, ArticleExtra]>
    wordCloud: Array<[number, { text: string; value: number }[]]>
}

let saveTimeout: ReturnType<typeof setTimeout> | null = null

const persistCache = () => {
    if (typeof window === 'undefined') return

    try {
        const payload: PersistentAppState = {
            hotEvents: state.hotEvents,
            rssSources: Array.from(state.rssSources.entries()),
            sourceArticles: Array.from(state.sourceArticles.entries()),
            subscriptions: state.subscriptions,
            subscriptionFeeds: Array.from(state.subscriptionFeeds.entries()),
            favorites: state.favorites,
            articleDetails: Array.from(state.articleDetails.entries()),
            articleExtras: Array.from(state.articleExtras.entries()),
            wordCloud: Array.from(state.wordCloud.entries())
        }
        window.localStorage.setItem(APP_CACHE_STORAGE_KEY, JSON.stringify(payload))
    } catch {
        // 忽略持久化失败（如超容）
    }
}

const triggerSave = () => {
    if (saveTimeout) clearTimeout(saveTimeout)
    saveTimeout = setTimeout(persistCache, 500)
}

const hydrateCache = () => {
    if (typeof window === 'undefined') return

    try {
        const raw = window.localStorage.getItem(APP_CACHE_STORAGE_KEY)
        if (!raw) {
            // 清理 V1 缓存遗留
            window.localStorage.removeItem('rss_article_cache_v1')
            return
        }

        const parsed = JSON.parse(raw) as Partial<PersistentAppState>

        if (parsed.hotEvents) state.hotEvents = parsed.hotEvents
        if (parsed.subscriptions) state.subscriptions = parsed.subscriptions
        if (parsed.favorites) state.favorites = parsed.favorites

        if (Array.isArray(parsed.rssSources)) state.rssSources = new Map(parsed.rssSources)
        if (Array.isArray(parsed.sourceArticles)) state.sourceArticles = new Map(parsed.sourceArticles)
        if (Array.isArray(parsed.subscriptionFeeds)) state.subscriptionFeeds = new Map(parsed.subscriptionFeeds)
        if (Array.isArray(parsed.articleDetails)) state.articleDetails = new Map(parsed.articleDetails)
        if (Array.isArray(parsed.articleExtras)) state.articleExtras = new Map(parsed.articleExtras)
        if (Array.isArray(parsed.wordCloud)) state.wordCloud = new Map(parsed.wordCloud)
    } catch {
        window.localStorage.removeItem(APP_CACHE_STORAGE_KEY)
    }
}

hydrateCache()

/**
 * 缓存 Store (非 Pinia 实现)
 * 采用快照和 LRU 容量限制策略
 */
export function useCacheStore() {

    // --- 热点事件 ---
    const getHotEvents = (): HotEvent[] | null => state.hotEvents
    const setHotEvents = (data: HotEvent[]): void => {
        state.hotEvents = data
        triggerSave()
    }

    // --- RSS 源文章列表 (针对 Discover 某源的预览) ---
    const getSourceArticles = (sourceId: number): ArticleFeed[] | null => state.sourceArticles.get(sourceId) || null
    const setSourceArticles = (sourceId: number, data: ArticleFeed[]): void => {
        state.sourceArticles.set(sourceId, data)
        triggerSave()
    }

    // --- 订阅列表 ---
    const getSubscriptions = (): Subscription[] | null => state.subscriptions
    const setSubscriptions = (data: Subscription[]): void => {
        state.subscriptions = data
        triggerSave()
    }

    const upsertSubscription = (item: Subscription): void => {
        if (!state.subscriptions) {
            state.subscriptions = [item]
        } else {
            const index = state.subscriptions.findIndex(sub => sub.id === item.id)
            if (index >= 0) {
                state.subscriptions[index] = item
            } else {
                state.subscriptions.unshift(item)
            }
        }
        triggerSave()
    }

    const removeSubscription = (subscriptionId: number): void => {
        if (state.subscriptions) {
            state.subscriptions = state.subscriptions.filter(item => item.id !== subscriptionId)
        }
        state.subscriptionFeeds.delete(`sub:${subscriptionId}`)
        triggerSave()
    }

    // --- 订阅时间线快照 ---
    const getSubscriptionFeed = (key: string): { items: ArticleFeed[]; cursor: string | null; hasMore: boolean } | null => {
        const cached = state.subscriptionFeeds.get(key)
        if (!cached) return null
        return {
            items: cached.data,
            cursor: cached.cursor,
            hasMore: cached.hasMore
        }
    }

    const setSubscriptionFeed = (key: string, items: ArticleFeed[], cursor: string | null, hasMore: boolean): void => {
        state.subscriptionFeeds.set(key, { data: items, cursor, hasMore })
        triggerSave()
    }

    // --- 收藏列表快照 (SWR 更新机制) ---
    const getFavorites = (): FavoritesCacheData | null => state.favorites
    const setFavorites = (data: FavoritesCacheData): void => {
        state.favorites = data
        triggerSave()
    }

    const removeFavorite = (articleId: number): void => {
        if (!state.favorites) return
        state.favorites.items = state.favorites.items.filter(item => item.id !== articleId)
        triggerSave()
    }

    const upsertFavorite = (article: ArticleFeed): void => {
        if (!state.favorites) {
            state.favorites = {
                items: [article],
                page: 1,
                last: false
            }
        } else {
            const exists = state.favorites.items.some(item => item.id === article.id)
            if (!exists) {
                state.favorites.items.unshift(article)
            }
        }
        triggerSave()
    }

    // --- LRU 辅助函数 ---
    const updateLruItem = <K, V>(map: Map<K, V>, key: K, val: V, maxItems: number) => {
        // 先删后加移至尾部最新
        map.delete(key)
        map.set(key, val)
        if (map.size > maxItems) {
            const first = map.keys().next().value
            if (first !== undefined) map.delete(first)
        }
    }

    // --- 文章详情 & AI 增强 (LRU 存储机制) ---
    const getArticleDetail = (id: number): ArticleDetail | null => state.articleDetails.get(id) || null
    const setArticleDetail = (id: number, data: ArticleDetail): void => {
        updateLruItem(state.articleDetails, id, data, MAX_ARTICLES)
        triggerSave()
    }

    const getArticleExtra = (id: number): ArticleExtra | null => state.articleExtras.get(id) || null
    const setArticleExtra = (id: number, data: ArticleExtra): void => {
        updateLruItem(state.articleExtras, id, data, MAX_ARTICLES)
        triggerSave()
    }

    // --- 词云 ---
    const getWordCloud = (sourceId: number): { text: string; value: number }[] | null => state.wordCloud.get(sourceId) || null
    const setWordCloud = (sourceId: number, data: { text: string; value: number }[]): void => {
        state.wordCloud.set(sourceId, data)
        triggerSave()
    }

    // --- RSS 发现页源列表 ---
    const getRssSources = (category: string): { sources: RssSource[], page: number, last: boolean } | null => {
        const cached = state.rssSources.get(category)
        if (!cached || cached.length === 0) return null
        
        const allSources = cached.flatMap(item => item.data)
        const lastPage = cached[cached.length - 1]
        
        if (!lastPage) return null

        return {
            sources: allSources,
            page: lastPage.page + 1,
            last: lastPage.last
        }
    }

    const addRssSources = (category: string, data: RssSource[], page: number, last: boolean): void => {
        const cached = state.rssSources.get(category) || []
        const existingPageIndex = cached.findIndex(item => item.page === page)
        
        if (existingPageIndex !== -1) {
            cached[existingPageIndex] = { data, page, last }
        } else {
            cached.push({ data, page, last })
        }
        
        state.rssSources.set(category, cached)
        triggerSave()
    }

    const clearRssSources = (category: string): void => {
        state.rssSources.delete(category)
        triggerSave()
    }

    const syncRssSourceSubscription = (sourceId: number, isSubscribed: boolean, subscriptionId: number | null): void => {
        state.rssSources.forEach((pages, category) => {
            const nextPages = pages.map((pageItem) => ({
                ...pageItem,
                data: pageItem.data.map((source) => {
                    if (source.id !== sourceId) return source
                    return { ...source, isSubscribed, subscriptionId }
                })
            }))
            state.rssSources.set(category, nextPages)
        })
        triggerSave()
    }

    // --- 全局操作 ---
    const clearAll = (): void => {
        state.hotEvents = null
        state.rssSources.clear()
        state.sourceArticles.clear()
        state.subscriptions = null
        state.subscriptionFeeds.clear()
        state.favorites = null
        state.articleDetails.clear()
        state.articleExtras.clear()
        state.wordCloud.clear()
        if (typeof window !== 'undefined') {
            window.localStorage.removeItem(APP_CACHE_STORAGE_KEY)
            window.localStorage.removeItem('rss_article_cache_v1')
        }
        triggerSave()
    }

    const forceRefresh = (): void => clearAll()

    return {
        getHotEvents, setHotEvents,
        getRssSources, addRssSources, clearRssSources,
        getSourceArticles, setSourceArticles,
        getSubscriptions, setSubscriptions, upsertSubscription, removeSubscription,
        getSubscriptionFeed, setSubscriptionFeed,
        getFavorites, setFavorites, removeFavorite, upsertFavorite,
        getArticleDetail, setArticleDetail, getArticleExtra, setArticleExtra,
        getWordCloud, setWordCloud, syncRssSourceSubscription,
        clearAll, forceRefresh
    }
}

