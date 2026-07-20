/**
 * 用户相关 API
 */
import request from '../request'
import type { R } from '@/types/api'
import type { UserInfo, LoginDTO, LoginVO } from '@/types/api'

/**
 * 用户登录
 *
 * <p>用完整路径 /api/v1/base/auth/login（Gateway 的 /api/v1/auth/** 重写在 standalone 下不存在，
 * 直接走 /base/auth/ 同时兼容微服务 + standalone 两种部署）
 */
export function login(data: LoginDTO) {
  return request.post<R<LoginVO>>('/api/v1/base/auth/login', data)
}

/**
 * 获取当前登录用户信息
 *
 * <p>后端 AuthController 提供 /api/v1/base/auth/user-info 返回 LoginUser（含 roles/permissions）
 */
export function getUserInfo() {
  return request.get<R<UserInfo>>('/api/v1/base/auth/user-info')
}

/**
 * 用户登出
 */
export function logout() {
  return request.post<R<void>>('/api/v1/base/auth/logout')
}
