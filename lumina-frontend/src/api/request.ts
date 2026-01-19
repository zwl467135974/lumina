/**
 * Axios 请求封装
 */
import axios, { type AxiosInstance, type AxiosRequestConfig, type AxiosResponse, type AxiosError } from 'axios'
import { ElMessage } from 'element-plus'
import type { R } from '@/types/api'

// 创建 Axios 实例
const service: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json;charset=UTF-8'
  }
})

// 请求拦截器
service.interceptors.request.use(
  (config: AxiosRequestConfig) => {
    // 从 localStorage 获取 token
    const token = localStorage.getItem('lumina_token')
    if (token && config.headers) {
      config.headers['Authorization'] = `Bearer ${token}`
    }
    return config
  },
  (error: AxiosError) => {
    return Promise.reject(error)
  }
)

// 响应拦截器
service.interceptors.response.use(
  (response: AxiosResponse<R<any>>) => {
    const res = response.data

    // 统一响应格式处理
    if (res.code === 200) {
      return res
    } else {
      // 业务错误处理
      ElMessage.error(res.msg || '请求失败')

      // 401 未授权，跳转登录
      if (res.code === 401) {
        localStorage.removeItem('lumina_token')
        window.location.href = '/login'
      }

      return Promise.reject(new Error(res.msg || '请求失败'))
    }
  },
  (error: AxiosError) => {
    // HTTP 错误处理
    if (error.response) {
      const status = error.response.status
      switch (status) {
        case 400:
          ElMessage.error('请求参数错误')
          break
        case 401:
          ElMessage.error('未授权，请重新登录')
          localStorage.removeItem('lumina_token')
          window.location.href = '/login'
          break
        case 403:
          ElMessage.error('拒绝访问')
          break
        case 404:
          ElMessage.error('请求的资源不存在')
          break
        case 500:
          ElMessage.error('服务器内部错误')
          break
        default:
          ElMessage.error(error.message || '网络错误')
      }
    } else {
      ElMessage.error('网络连接失败')
    }
    return Promise.reject(error)
  }
)

export default service
