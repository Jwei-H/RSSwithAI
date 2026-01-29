// Axios 请求封装
import axios, { AxiosError, type AxiosInstance, type InternalAxiosRequestConfig, type AxiosResponse } from 'axios'
import { getToken, removeToken } from './auth'

// 创建 axios 实例
const request: AxiosInstance = axios.create({
  baseURL: (import.meta.env.VITE_API_BASE_URL || '') + '/api/admin',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器
request.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = getToken()
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error: AxiosError) => {
    return Promise.reject(error)
  }
)

// 响应拦截器
request.interceptors.response.use(
  (response: AxiosResponse) => {
    return response.data
  },
  (error: AxiosError) => {
    if (error.response?.status === 401) {
      // Token 无效或过期，跳转到登录页
      removeToken()
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)

export default request