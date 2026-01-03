// 模型配置管理 API
import request from '@/utils/request'
import type { ModelConfig, ModelConfigRequest, PageResponse } from '@/types'

export function getModelConfigs(params?: { page?: number; size?: number }) {
  return request.get<any, PageResponse<ModelConfig>>('/v1/model-configs', { params })
}

export function getModelConfigById(id: number) {
  return request.get<any, ModelConfig>(`/v1/model-configs/${id}`)
}

export function createModelConfig(data: ModelConfigRequest) {
  return request.post<any, ModelConfig>('/v1/model-configs', data)
}

export function updateModelConfig(id: number, data: Partial<ModelConfigRequest>) {
  return request.put<any, ModelConfig>(`/v1/model-configs/${id}`, data)
}

export function deleteModelConfig(id: number) {
  return request.delete(`/v1/model-configs/${id}`)
}
