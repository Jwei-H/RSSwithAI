import router from '../router'
import { getToken, useSessionStore } from '../stores/session'
import { useToastStore } from '../stores/toast'

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''

export class ApiError extends Error {
  status: number
  payload?: unknown

  constructor(message: string, status: number, payload?: unknown) {
    super(message)
    this.status = status
    this.payload = payload
  }
}

const AUTH_ENDPOINTS = new Set(['/api/login', '/api/register', '/api/refresh'])
let lastUnauthorizedAt = 0

function handleUnauthorized(path: string) {
  if (AUTH_ENDPOINTS.has(path)) return
  const now = Date.now()
  if (now - lastUnauthorizedAt < 1200) return
  lastUnauthorizedAt = now

  const toast = useToastStore()
  toast.push('请登录后使用订阅功能', 'error')

  const current = router.currentRoute.value
  if (current.path !== '/login') {
    router
      .push({ path: '/login', query: { redirect: current.fullPath } })
      .catch(() => {
        // ignore navigation failures
      })
  }
}

function shouldPromptLogin(path: string, method: string) {
  if (AUTH_ENDPOINTS.has(path)) return false
  if (method.toUpperCase() === 'GET') return false
  return path.startsWith('/api/front/v1/') || path.startsWith('/api/user/')
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
  const method = options.method ?? 'GET'
  const headers: Record<string, string> = {
    'Content-Type': 'application/json'
  }

  if (options.headers) {
    Object.assign(headers, options.headers as Record<string, string>)
  }

  if (token) {
    headers.Authorization = `Bearer ${token}`
  }

  let response: Response
  try {
    response = await fetch(`${BASE_URL}${path}`, {
      ...options,
      headers
    })
  } catch (error: any) {
    if (!token && shouldPromptLogin(path, method)) {
      handleUnauthorized(path)
      throw new ApiError('Unauthorized', 401)
    }
    const message = error?.message || '网络异常，请稍后重试'
    throw new ApiError(message, 0)
  }

  if (response.status === 401 && retryOnUnauthorized) {
    const refreshed = await refreshToken()
    if (refreshed) {
      return apiRequest<T>(path, options, false)
    }
  }

  if (response.status === 401) {
    const session = useSessionStore()
    session.clear()
    handleUnauthorized(path)
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