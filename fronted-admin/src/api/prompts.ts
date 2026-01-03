// 提示词模板管理 API
import request from '@/utils/request'
import type { PromptTemplate, PromptVersion, PromptTemplateRequest, PromptVersionRequest, PageResponse } from '@/types'

export function getPromptTemplates(params?: { page?: number; size?: number }) {
  return request.get<any, PageResponse<PromptTemplate>>('/v1/prompts', { params })
}

export function getPromptTemplateById(id: number) {
  return request.get<any, PromptTemplate>(`/v1/prompts/${id}`)
}

export function createPromptTemplate(data: PromptTemplateRequest) {
  return request.post<any, PromptTemplate>('/v1/prompts', data)
}

export function deletePromptTemplate(id: number) {
  return request.delete(`/v1/prompts/${id}`)
}

export function getPromptVersion(templateId: number, versionNum: number) {
  return request.get<any, PromptVersion>(`/v1/prompts/${templateId}/versions/${versionNum}`)
}

export function updatePromptVersion(templateId: number, versionNum: number, data: PromptVersionRequest) {
  return request.put<any, PromptVersion>(`/v1/prompts/${templateId}/versions/${versionNum}`, data)
}

export function freezePromptVersion(templateId: number, versionNum: number) {
  return request.post<any, PromptVersion>(`/v1/prompts/${templateId}/versions/${versionNum}/freeze`)
}

export function createPromptVersion(templateId: number) {
  return request.post<any, PromptVersion>(`/v1/prompts/${templateId}/versions`)
}
