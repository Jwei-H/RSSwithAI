// RSS 源管理 API
import request from '@/utils/request'
import type { RssSource, RssSourceStats, RssSourceRequest, PageResponse } from '@/types'

export function getRssSourceStats() {
  return request.get<any, RssSourceStats>('/v1/rss-sources/stats')
}

export function getRssSources(params?: { page?: number; size?: number }) {
  return request.get<any, PageResponse<RssSource>>('/v1/rss-sources', { params })
}

export function getRssSourceById(id: number) {
  return request.get<any, RssSource>(`/v1/rss-sources/${id}`)
}

export function createRssSource(data: RssSourceRequest) {
  return request.post<any, RssSource>('/v1/rss-sources', data)
}

export function updateRssSource(id: number, data: Partial<RssSourceRequest>) {
  return request.put<any, RssSource>(`/v1/rss-sources/${id}`, data)
}

export function deleteRssSource(id: number) {
  return request.delete(`/v1/rss-sources/${id}`)
}

export function enableRssSource(id: number) {
  return request.post<any, RssSource>(`/v1/rss-sources/${id}/enable`)
}

export function disableRssSource(id: number) {
  return request.post<any, RssSource>(`/v1/rss-sources/${id}/disable`)
}

export function fetchRssSource(id: number) {
  return request.post(`/v1/rss-sources/${id}/fetch`)
}

export function fetchAllRssSources() {
  return request.post('/v1/rss-sources/fetch-all')
}
