// 实验管理 API
import request from '@/utils/request'
import type { Experiment, ExperimentRequest, PageResponse } from '@/types'

export function getExperiments(params?: { page?: number; size?: number; status?: string }) {
  return request.get<any, PageResponse<Experiment>>('/v1/experiments', { params })
}

export function getExperimentById(id: number) {
  return request.get<any, Experiment>(`/v1/experiments/${id}`)
}

export function createExperiment(data: ExperimentRequest) {
  return request.post<any, Experiment>('/v1/experiments', data)
}

export function deleteExperiment(id: number) {
  return request.delete(`/v1/experiments/${id}`)
}
