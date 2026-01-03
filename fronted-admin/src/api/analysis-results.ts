// 分析结果管理 API
import request from '@/utils/request'
import type { AnalysisResult, PageResponse } from '@/types'

export function getAnalysisResultById(id: number) {
  return request.get<any, AnalysisResult>(`/v1/analysis-results/${id}`)
}

export function getAnalysisResultsByArticleId(articleId: number, params?: { page?: number; size?: number }) {
  return request.get<any, PageResponse<AnalysisResult>>(`/v1/analysis-results/articles/${articleId}`, { params })
}

export function getAnalysisResultsByExperimentId(experimentId: number, params?: { page?: number; size?: number }) {
  return request.get<any, PageResponse<AnalysisResult>>(`/v1/analysis-results/experiments/${experimentId}`, { params })
}
