// 系统配置 API
import request from '@/utils/request'
import type { SystemSettings } from '@/types'

export function getSettings() {
  return request.get<any, SystemSettings>('/settings')
}

export function updateSettings(data: SystemSettings) {
  return request.post<any, SystemSettings>('/settings', data)
}
