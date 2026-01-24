import { del, get, post, put } from './api'
import type {
  ArticleDetail,
  ArticleExtra,
  ArticleFeed,
  FavoritePage,
  HotEvent,
  PagedResponse,
  RssSource,
  Subscription,
  Topic,
  UserProfile
} from '../types'
import { getIconUrl } from '../lib/utils'

export const authApi = {
  register: (payload: { username: string; password: string }) =>
    post<UserProfile>('/api/register', payload),
  login: (payload: { username: string; password: string }) =>
    post<{ token: string }>('/api/login', payload),
  profile: () => get<UserProfile>('/api/user/profile'),
  updateUsername: (payload: { newUsername: string }) =>
    put<UserProfile>('/api/user/username', payload),
  updatePassword: (payload: { oldPassword: string; newPassword: string }) =>
    put<void>('/api/user/password', payload)
}

export const subscriptionApi = {
  list: async () => {
    const subscriptions = await get<Subscription[]>('/api/front/v1/subscriptions')
    // 为 RSS 类型的订阅计算图标 URL
    return subscriptions.map((sub) => ({
      ...sub,
      icon: sub.type === 'RSS' ? getIconUrl(sub.link) : null
    }))
  },
  create: (payload: { type: 'RSS' | 'TOPIC'; targetId: number }) =>
    post<Subscription>('/api/front/v1/subscriptions', payload),
  remove: (id: number) => del<void>(`/api/front/v1/subscriptions/${id}`),
  createTopic: (payload: { content: string }) => post<Topic>('/api/front/v1/topics', payload)
}

export const feedApi = {
  feed: (params: { subscriptionId?: number; cursor?: string; size?: number }) => {
    const query = new URLSearchParams()
    if (params.subscriptionId) query.set('subscriptionId', String(params.subscriptionId))
    if (params.cursor) query.set('cursor', params.cursor)
    if (params.size) query.set('size', String(params.size))
    return get<ArticleFeed[]>(`/api/front/v1/articles/feed?${query.toString()}`)
  },
  bySource: (sourceId: number, page = 0, size = 10) =>
    get<PagedResponse<ArticleFeed>>(
      `/api/front/v1/articles/source/${sourceId}?page=${page}&size=${size}`
    ),
  favorites: (page = 0, size = 10) =>
    get<FavoritePage>(
      `/api/front/v1/articles/favorites?page=${page}&size=${size}&sort=pubDate,desc`
    ),
  search: (query: string, scope: 'ALL' | 'SUBSCRIBED' | 'FAVORITE') =>
    get<ArticleFeed[]>(
      `/api/front/v1/articles/search?query=${encodeURIComponent(query)}&searchScope=${scope}`
    ),
  detail: (id: number) => get<ArticleDetail>(`/api/front/v1/articles/${id}`),
  extra: (id: number) => get<ArticleExtra>(`/api/front/v1/articles/${id}/extra`),
  recommendations: (id: number) =>
    get<ArticleFeed[]>(`/api/front/v1/articles/${id}/recommendations`),
  favorite: (id: number) => post<void>(`/api/front/v1/articles/${id}/favorite`),
  unfavorite: (id: number) => del<void>(`/api/front/v1/articles/${id}/favorite`)
}

export const rssApi = {
  list: (params: { page: number; size: number; category?: string }) => {
    const query = new URLSearchParams({
      page: String(params.page),
      size: String(params.size)
    })
    if (params.category) query.set('category', params.category)
    return get<PagedResponse<RssSource>>(`/api/front/v1/rss-sources?${query.toString()}`).then(
      (res) => ({
        ...res,
        content: res.content.map((source) => ({
          ...source,
          icon: getIconUrl(source.link)
        }))
      })
    )
  }
}

export const trendApi = {
  wordCloud: (sourceId?: number) => {
    const query = sourceId ? `?sourceId=${sourceId}` : ''
    return get<{ text: string; value: number }[]>(`/api/front/v1/trends/wordcloud${query}`)
  },
  hotEvents: () => get<HotEvent[]>('/api/front/v1/trends/hotevents')
}
