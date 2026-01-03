// 文章管理 API
import request from '@/utils/request'
import type { Article, ArticleStats, ArticleExtra, PageResponse } from '@/types'

export function getArticleStats() {
  return request.get<any, ArticleStats>('/v1/articles/stats')
}

export function getArticles(params?: { page?: number; size?: number; searchWord?: string }) {
  return request.get<any, PageResponse<Article>>('/v1/articles', { params })
}

export function getArticleById(id: number) {
  return request.get<any, Article>(`/v1/articles/${id}`)
}

export function getArticlesBySourceId(sourceId: number, params?: { page?: number; size?: number; searchWord?: string }) {
  return request.get<any, PageResponse<Article>>(`/v1/articles/source/${sourceId}`, { params })
}

export function getArticleExtra(articleId: number) {
  return request.get<any, ArticleExtra>(`/v1/articles/${articleId}/extra`)
}

export function regenerateArticleExtra(articleId: number) {
  return request.post<any, ArticleExtra>(`/v1/articles/${articleId}/extra/regenerate`)
}
