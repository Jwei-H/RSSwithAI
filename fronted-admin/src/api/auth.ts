// 认证相关 API
import request from '@/utils/request'
import type { LoginRequest, LoginResponse } from '@/types'

export function login(data: LoginRequest) {
  return request.post<any, LoginResponse>('/login', data)
}

export function refreshToken() {
  return request.post<any, LoginResponse>('/refresh')
}
