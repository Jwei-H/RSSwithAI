import { reactive } from 'vue'
import type { ArticleDetail, ArticleExtra, ArticleFeed, HotEvent, RssSource, Subscription } from '../types'

const ARTICLE_CACHE_STORAGE_KEY = 'rss_article_cache_v1'
const ARTICLE_CACHE_EXPIRY = 48 * 60 * 60 * 1000 // 48小时

/**
 * 缓存项接口
 */
interface CacheItem<T> {
    data: T
    timestamp: number
}

/**
 * 分页缓存项接口
 */
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

interface PersistentArticleCache {
    articleDetails: Array<[number, CacheItem<ArticleDetail>]>
    articleExtras: Array<[number, CacheItem<ArticleExtra>]>
    articleMergedContents: Array<[number, CacheItem<string>]>
}

// 单例状态
const state = reactive<{
    hotEvents: CacheItem<HotEvent[]> | null
    rssSources: Map<string, PaginatedCacheItem[]>
    sourceArticles: Map<number, CacheItem<ArticleFeed[]>>
    subscriptions: CacheItem<Subscription[]> | null
    subscriptionFeeds: Map<string, FeedCacheItem>
    favorites: CacheItem<FavoritesCacheData> | null
    articleDetails: Map<number, CacheItem<ArticleDetail>>
    articleExtras: Map<number, CacheItem<ArticleExtra>>
    articleMergedContents: Map<number, CacheItem<string>>
    wordCloud: Map<number, CacheItem<{ text: string; value: number }[]>>
}>({
    hotEvents: null,
    rssSources: new Map(),
    sourceArticles: new Map(),
    subscriptions: null,
    subscriptionFeeds: new Map(),
    favorites: null,
    articleDetails: new Map(),
    articleExtras: new Map(),
    articleMergedContents: new Map(),
    wordCloud: new Map()
})

const persistArticleCaches = () => {
    if (typeof window === 'undefined') return

    try {
        const payload: PersistentArticleCache = {
            articleDetails: Array.from(state.articleDetails.entries()),
            articleExtras: Array.from(state.articleExtras.entries()),
            articleMergedContents: Array.from(state.articleMergedContents.entries())
        }
        window.localStorage.setItem(ARTICLE_CACHE_STORAGE_KEY, JSON.stringify(payload))
    } catch {
        // 忽略持久化失败
    }
}

const hydrateArticleCaches = () => {
    if (typeof window === 'undefined') return

    try {
        const raw = window.localStorage.getItem(ARTICLE_CACHE_STORAGE_KEY)
        if (!raw) return

        const parsed = JSON.parse(raw) as Partial<PersistentArticleCache>
        const now = Date.now()

        const details = Array.isArray(parsed.articleDetails) ? parsed.articleDetails : []
        const extras = Array.isArray(parsed.articleExtras) ? parsed.articleExtras : []
        const merged = Array.isArray(parsed.articleMergedContents) ? parsed.articleMergedContents : []

        for (const [id, item] of details) {
            if (now - item.timestamp < ARTICLE_CACHE_EXPIRY) {
                state.articleDetails.set(Number(id), item)
            }
        }
        for (const [id, item] of extras) {
            if (now - item.timestamp < ARTICLE_CACHE_EXPIRY) {
                state.articleExtras.set(Number(id), item)
            }
        }
        for (const [id, item] of merged) {
            if (now - item.timestamp < ARTICLE_CACHE_EXPIRY) {
                state.articleMergedContents.set(Number(id), item)
            }
        }
    } catch {
        // 忽略反序列化失败
    }
}

hydrateArticleCaches()

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
     * 检查长期缓存是否有效 (48小时)
     * @param timestamp 缓存时间戳
     * @returns 缓存是否有效
     */
    const isLongTermCacheValid = (timestamp: number): boolean => {
        const now = Date.now()
        const cacheExpiry = ARTICLE_CACHE_EXPIRY
        return now - timestamp < cacheExpiry
    }

    /**
     * 检查中期缓存是否有效 (12小时)
     * @param timestamp 缓存时间戳
     * @returns 缓存是否有效
     */
    const isMediumTermCacheValid = (timestamp: number): boolean => {
        const now = Date.now()
        const cacheExpiry = 12 * 60 * 60 * 1000 // 12小时过期
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
     * 添加或更新单条订阅缓存
     */
    const upsertSubscription = (item: Subscription): void => {
        if (!state.subscriptions) {
            state.subscriptions = {
                data: [item],
                timestamp: Date.now()
            }
            return
        }

        const index = state.subscriptions.data.findIndex((sub) => sub.id === item.id)
        if (index >= 0) {
            state.subscriptions.data[index] = item
        } else {
            state.subscriptions.data.unshift(item)
        }
        state.subscriptions.timestamp = Date.now()
    }

    /**
     * 移除单条订阅缓存
     */
    const removeSubscription = (subscriptionId: number): void => {
        if (state.subscriptions) {
            state.subscriptions.data = state.subscriptions.data.filter((item) => item.id !== subscriptionId)
            state.subscriptions.timestamp = Date.now()
        }

        state.subscriptionFeeds.delete(`sub:${subscriptionId}`)
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
     * 从收藏缓存中移除文章
     */
    const removeFavorite = (articleId: number): void => {
        if (!state.favorites) return
        state.favorites.data.items = state.favorites.data.items.filter((item) => item.id !== articleId)
        state.favorites.timestamp = Date.now()
    }

    /**
     * 向收藏缓存中添加文章（若不存在）
     */
    const upsertFavorite = (article: ArticleFeed): void => {
        if (!state.favorites) {
            state.favorites = {
                data: {
                    items: [article],
                    page: 1,
                    last: false
                },
                timestamp: Date.now()
            }
            return
        }

        const exists = state.favorites.data.items.some((item) => item.id === article.id)
        if (!exists) {
            state.favorites.data.items.unshift(article)
        }
        state.favorites.timestamp = Date.now()
    }

    /**
     * 获取文章详情缓存
     * @param id 文章ID
     */
    const getArticleDetail = (id: number): ArticleDetail | null => {
        const cached = state.articleDetails.get(id)
        if (!cached) return null
        if (!isLongTermCacheValid(cached.timestamp)) {
            state.articleDetails.delete(id)
            persistArticleCaches()
            return null
        }
        return cached.data
    }

    /**
     * 设置文章详情缓存 (LRU max 100)
     */
    const setArticleDetail = (id: number, data: ArticleDetail): void => {
        if (state.articleDetails.has(id)) {
            state.articleDetails.delete(id)
        }
        state.articleDetails.set(id, {
            data,
            timestamp: Date.now()
        })
        if (state.articleDetails.size > 100) {
            const first = state.articleDetails.keys().next().value
            if (first !== undefined) state.articleDetails.delete(first)
        }
        persistArticleCaches()
    }

    /**
     * 获取文章AI增强信息缓存
     */
    const getArticleExtra = (id: number): ArticleExtra | null => {
        const cached = state.articleExtras.get(id)
        if (!cached) return null
        if (!isLongTermCacheValid(cached.timestamp)) {
            state.articleExtras.delete(id)
            persistArticleCaches()
            return null
        }
        return cached.data
    }

    /**
     * 设置文章AI增强信息缓存 (LRU max 100)
     */
    const setArticleExtra = (id: number, data: ArticleExtra): void => {
        if (state.articleExtras.has(id)) {
            state.articleExtras.delete(id)
        }
        state.articleExtras.set(id, {
            data,
            timestamp: Date.now()
        })
        if (state.articleExtras.size > 100) {
            const first = state.articleExtras.keys().next().value
            if (first !== undefined) state.articleExtras.delete(first)
        }
        persistArticleCaches()
    }

    /**
     * 获取拼接后的文章正文缓存
     */
    const getArticleMergedContent = (id: number): string | null => {
        const cached = state.articleMergedContents.get(id)
        if (!cached) return null
        if (!isLongTermCacheValid(cached.timestamp)) {
            state.articleMergedContents.delete(id)
            persistArticleCaches()
            return null
        }
        return cached.data
    }

    /**
     * 设置拼接后的文章正文缓存 (LRU max 100)
     */
    const setArticleMergedContent = (id: number, content: string): void => {
        if (state.articleMergedContents.has(id)) {
            state.articleMergedContents.delete(id)
        }
        state.articleMergedContents.set(id, {
            data: content,
            timestamp: Date.now()
        })
        if (state.articleMergedContents.size > 100) {
            const first = state.articleMergedContents.keys().next().value
            if (first !== undefined) state.articleMergedContents.delete(first)
        }
        persistArticleCaches()
    }

    /**
     * 获取词云缓存
     * @param sourceId 源 ID (0表示全部)
     */
    const getWordCloud = (sourceId: number): { text: string; value: number }[] | null => {
        const cached = state.wordCloud.get(sourceId)
        if (!cached) return null
        if (!isMediumTermCacheValid(cached.timestamp)) {
            state.wordCloud.delete(sourceId)
            return null
        }
        return cached.data
    }

    /**
     * 设置词云缓存
     * @param sourceId 源 ID (0表示全部)
     * @param data 词云数据
     */
    const setWordCloud = (sourceId: number, data: { text: string; value: number }[]): void => {
        state.wordCloud.set(sourceId, {
            data,
            timestamp: Date.now()
        })
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
     * 同步 RSS 源订阅状态到缓存
     */
    const syncRssSourceSubscription = (sourceId: number, isSubscribed: boolean, subscriptionId: number | null): void => {
        state.rssSources.forEach((pages, category) => {
            const nextPages = pages.map((pageItem) => ({
                ...pageItem,
                data: pageItem.data.map((source) => {
                    if (source.id !== sourceId) return source
                    return {
                        ...source,
                        isSubscribed,
                        subscriptionId
                    }
                })
            }))
            state.rssSources.set(category, nextPages)
        })
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
        state.articleDetails.clear()
        state.articleExtras.clear()
        state.articleMergedContents.clear()
        state.wordCloud.clear()
        if (typeof window !== 'undefined') {
            window.localStorage.removeItem(ARTICLE_CACHE_STORAGE_KEY)
        }
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
        upsertSubscription,
        removeSubscription,
        getSubscriptionFeed,
        setSubscriptionFeed,

        // 收藏
        getFavorites,
        setFavorites,
        removeFavorite,
        upsertFavorite,

        // 文章详情 & AI增强
        getArticleDetail,
        setArticleDetail,
        getArticleExtra,
        setArticleExtra,
        getArticleMergedContent,
        setArticleMergedContent,

        // 词云
        getWordCloud,
        setWordCloud,
        syncRssSourceSubscription,

        // 全局操作
        clearAll,
        forceRefresh
    }
}
