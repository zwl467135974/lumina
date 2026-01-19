/**
 * 用户相关 API
 */
import request from '../request'
import type { R } from '@/types/api'
import type { UserInfo, LoginDTO, LoginVO } from '@/types/api'

/**
 * 用户登录
 */
export function login(data: LoginDTO) {
  return request.post<R<LoginVO>>('/api/v1/auth/login', data)
}

/**
 * 获取用户信息
 */
export function getUserInfo() {
  return request.get<R<UserInfo>>('/api/v1/user/info')
}

/**
 * 用户登出
 */
export function logout() {
  return request.post<R<void>>('/api/v1/auth/logout')
}
