import { getToken, useSessionStore } from '../stores/session'

const BASE_URL = 'http://192.168.0.8:8080'

export class ApiError extends Error {
  status: number
  payload?: unknown

  constructor(message: string, status: number, payload?: unknown) {
    super(message)
    this.status = status
    this.payload = payload
  }
}

async function refreshToken() {
  const token = getToken()
  if (!token) return false

  const response = await fetch(`${BASE_URL}/api/refresh`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`
    }
  })

  if (!response.ok) return false
  const data = (await response.json()) as { token: string }
  const session = useSessionStore()
  session.setToken(data.token)
  return true
}

export async function apiRequest<T>(
  path: string,
  options: RequestInit = {},
  retryOnUnauthorized = true
): Promise<T> {
  const token = getToken()
  const headers: Record<string, string> = {
    'Content-Type': 'application/json'
  }

  if (options.headers) {
    Object.assign(headers, options.headers as Record<string, string>)
  }

  if (token) {
    headers.Authorization = `Bearer ${token}`
  }

  const response = await fetch(`${BASE_URL}${path}`, {
    ...options,
    headers
  })

  if (response.status === 401 && retryOnUnauthorized) {
    const refreshed = await refreshToken()
    if (refreshed) {
      return apiRequest<T>(path, options, false)
    }
  }

  if (response.status === 401) {
    const session = useSessionStore()
    session.clear()
  }

  if (!response.ok) {
    let payload: unknown = undefined
    try {
      payload = await response.json()
    } catch {
      payload = undefined
    }
    const message =
      (payload as { message?: string })?.message || response.statusText || '请求失败'
    throw new ApiError(message, response.status, payload)
  }

  if (response.status === 204) {
    return null as T
  }

  // 检查响应是否有内容
  const contentType = response.headers.get('content-type')
  if (contentType && contentType.includes('application/json')) {
    return (await response.json()) as T
  }

  // 如果没有内容或不是JSON，返回null
  return null as T
}

export function get<T>(path: string) {
  return apiRequest<T>(path)
}

export function post<T>(path: string, body?: unknown) {
  return apiRequest<T>(path, {
    method: 'POST',
    body: body ? JSON.stringify(body) : undefined
  })
}

export function put<T>(path: string, body?: unknown) {
  return apiRequest<T>(path, {
    method: 'PUT',
    body: body ? JSON.stringify(body) : undefined
  })
}

export function del<T>(path: string) {
  return apiRequest<T>(path, { method: 'DELETE' })
}