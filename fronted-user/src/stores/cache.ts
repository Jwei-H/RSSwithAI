import { reactive } from 'vue'
import type { ArticleFeed, HotEvent, RssSource, Subscription } from '../types'

/**
 * 缓存项接口
 */
interface CacheItem<T> {
    data: T
    timestamp: number
}

/**
 * 分页缓存项接口
        sourceArticles: new Map(),
        subscriptions: null,
        subscriptionFeeds: new Map(),
        favorites: null
        subscriptions: null,
        subscriptionFeeds: new Map(),
        favorites: null
interface PaginatedCacheItem {
    data: RssSource[]
    page: number
    last: boolean
    timestamp: number
}

interface FeedCacheItem {
    data: ArticleFeed[]
    cursor: string | null
    hasMore: boolean
    timestamp: number
}

interface FavoritesCacheData {
    items: ArticleFeed[]
    page: number
    last: boolean
}

// 单例状态
const state = reactive<{
    hotEvents: CacheItem<HotEvent[]> | null
    rssSources: Map<string, PaginatedCacheItem[]>
    sourceArticles: Map<number, CacheItem<ArticleFeed[]>>
    subscriptions: CacheItem<Subscription[]> | null
    subscriptionFeeds: Map<string, FeedCacheItem>
    favorites: CacheItem<FavoritesCacheData> | null
}>({
    hotEvents: null,
    rssSources: new Map(),
    sourceArticles: new Map()
})

/**
 * 缓存 Store (非 Pinia 实现)
 * 用于缓存热点事件和 RSS 源数据，减少 API 请求频率
 */
export function useCacheStore() {
    /**
     * 检查缓存是否有效
     * @param timestamp 缓存时间戳
     * @returns 缓存是否有效
     */
    const isCacheValid = (timestamp: number): boolean => {
        const now = Date.now()
        const cacheExpiry = 5 * 60 * 1000 // 5分钟过期
        return now - timestamp < cacheExpiry
    }

    /**
     * 获取热点事件缓存
     * @returns 热点事件数组或 null
     */
    const getHotEvents = (): HotEvent[] | null => {
        if (!state.hotEvents) return null
        if (!isCacheValid(state.hotEvents.timestamp)) {
            state.hotEvents = null
            return null
        }
        return state.hotEvents.data
    }

    /**
     * 设置热点事件缓存
     * @param data 热点事件数组
     */
    const setHotEvents = (data: HotEvent[]): void => {
        state.hotEvents = {
            data,
            timestamp: Date.now()
        }
    }

    /**
     * 获取源最新文章缓存
     * @param sourceId 源 ID
     * @returns 文章列表或 null
     */
    const getSourceArticles = (sourceId: number): ArticleFeed[] | null => {
        const cached = state.sourceArticles.get(sourceId)
        if (!cached) return null

        if (!isCacheValid(cached.timestamp)) {
            state.sourceArticles.delete(sourceId)
            return null
        }
        return cached.data
    }

    /**
     * 设置源最新文章缓存
     * @param sourceId 源 ID
     * @param data 文章列表
     */
    const setSourceArticles = (sourceId: number, data: ArticleFeed[]): void => {
        state.sourceArticles.set(sourceId, {
            data,
            timestamp: Date.now()
        })
    }

    /**
     * 获取订阅列表缓存
     * @returns 订阅列表或 null
     */
    const getSubscriptions = (): Subscription[] | null => {
        if (!state.subscriptions) return null
        if (!isCacheValid(state.subscriptions.timestamp)) {
            state.subscriptions = null
            return null
        }
        return state.subscriptions.data
    }

    /**
     * 设置订阅列表缓存
     * @param data 订阅列表
     */
    const setSubscriptions = (data: Subscription[]): void => {
        state.subscriptions = {
            data,
            timestamp: Date.now()
        }
    }

    /**
     * 获取订阅时间线缓存
     * @param key 订阅缓存键
     * @returns 时间线缓存或 null
     */
    const getSubscriptionFeed = (key: string): { items: ArticleFeed[]; cursor: string | null; hasMore: boolean } | null => {
        const cached = state.subscriptionFeeds.get(key)
        if (!cached) return null
        if (!isCacheValid(cached.timestamp)) {
            state.subscriptionFeeds.delete(key)
            return null
        }
        return {
            items: cached.data,
            cursor: cached.cursor,
            hasMore: cached.hasMore
        }
    }

    /**
     * 设置订阅时间线缓存
     * @param key 订阅缓存键
     * @param items 时间线列表
     * @param cursor 游标
     * @param hasMore 是否还有更多
     */
    const setSubscriptionFeed = (key: string, items: ArticleFeed[], cursor: string | null, hasMore: boolean): void => {
        state.subscriptionFeeds.set(key, {
            data: items,
            cursor,
            hasMore,
            timestamp: Date.now()
        })
    }

    /**
     * 获取收藏列表缓存
     * @returns 收藏列表缓存或 null
     */
    const getFavorites = (): FavoritesCacheData | null => {
        if (!state.favorites) return null
        if (!isCacheValid(state.favorites.timestamp)) {
            state.favorites = null
            return null
        }
        return state.favorites.data
    }

    /**
     * 设置收藏列表缓存
     * @param data 收藏列表缓存
     */
    const setFavorites = (data: FavoritesCacheData): void => {
        state.favorites = {
            data,
            timestamp: Date.now()
        }
    }

    /**
     * 获取 RSS 源缓存
     * @param category 分类
     * @returns RSS 源数组或 null
     */
    const getRssSources = (category: string): { sources: RssSource[], page: number, last: boolean } | null => {
        const cached = state.rssSources.get(category)
        if (!cached || cached.length === 0) return null

        // 检查第一页的缓存是否有效
        const firstPage = cached[0]
        if (!firstPage || !isCacheValid(firstPage.timestamp)) {
            state.rssSources.delete(category)
            return null
        }

        // 合并所有页的数据
        const allSources = cached.flatMap(item => item.data)
        const lastPage = cached[cached.length - 1]

        if (!lastPage) return null

        return {
            sources: allSources,
            page: lastPage.page + 1,
            last: lastPage.last
        }
    }

    /**
     * 添加 RSS 源缓存（追加分页数据）
     * @param category 分类
     * @param data RSS 源数组
     * @param page 页码
     * @param last 是否最后一页
     */
    const addRssSources = (category: string, data: RssSource[], page: number, last: boolean): void => {
        const cached = state.rssSources.get(category) || []

        // 检查是否已存在该页
        const existingPageIndex = cached.findIndex(item => item.page === page)
        if (existingPageIndex !== -1) {
            // 更新已存在的页
            cached[existingPageIndex] = {
                data,
                page,
                last,
                timestamp: Date.now()
            }
        } else {
            // 添加新页
            cached.push({
                data,
                page,
                last,
                timestamp: Date.now()
            })
        }

        state.rssSources.set(category, cached)
    }

    /**
     * 清除指定分类的 RSS 源缓存
     * @param category 分类
     */
    const clearRssSources = (category: string): void => {
        state.rssSources.delete(category)
    }

    /**
     * 清除所有缓存
     */
    const clearAll = (): void => {
        state.hotEvents = null
        state.rssSources.clear()
        state.sourceArticles.clear()
        state.subscriptions = null
        state.subscriptionFeeds.clear()
        state.favorites = null
    }

    /**
     * 强制刷新（清除所有缓存）
     */
    const forceRefresh = (): void => {
        clearAll()
    }

    return {
        // 热点事件
        getHotEvents,
        setHotEvents,

        // RSS 源
        getRssSources,
        addRssSources,
        clearRssSources,

        // 源文章
        getSourceArticles,
        setSourceArticles,

        // 订阅
        getSubscriptions,
        setSubscriptions,
        getSubscriptionFeed,
        setSubscriptionFeed,

        // 收藏
        getFavorites,
        setFavorites,

        // 全局操作
        clearAll,
        forceRefresh
    }
}
