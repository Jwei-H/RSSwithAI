export type SubscriptionType = 'RSS' | 'TOPIC'
export type SourceCategory = 'NEWS' | 'TECH' | 'SOCIETY' | 'FINANCE' | 'LIFESTYLE' | 'OTHER'

export type Subscription = {
  id: number
  type: SubscriptionType
  targetId: number
  name: string | null
  icon: string | null
  link: string | null
  category: SourceCategory | null
  content: string | null
  createdAt: string
}

export type Topic = {
  id: number
  content: string
  createdAt: string
}

export type RssSource = {
  id: number
  name: string
  icon?: string | null
  link: string
  category: SourceCategory
  isSubscribed: boolean
  subscriptionId?: number | null
}

export type ArticleFeed = {
  id: number
  sourceId: number
  sourceName: string
  title: string
  coverImage?: string | null
  pubDate: string
  wordCount?: number | null
}

export type ArticleDetail = ArticleFeed & {
  link?: string | null
  description?: string | null
  content: string
  author?: string | null
  categories?: string | null
  fetchedAt?: string
  createdAt?: string
}

export type ArticleExtra = {
  id: number
  articleId: number
  overview: string
  keyInformation: string[]
  status: 'SUCCESS' | 'FAILED'
  errorMessage?: string | null
}

export type HotEvent = {
  event: string
  score: number
}

export type PagedResponse<T> = {
  content: T[]
  pageable: {
    pageNumber: number
    pageSize: number
  }
  totalElements: number
  totalPages: number
  last: boolean
}

export type FavoritePage = PagedResponse<ArticleFeed>

export type UserProfile = {
  id: number
  username: string
  avatarUrl?: string | null
}
