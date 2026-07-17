/**
 * API Token 管理 API
 */
import request from '../request'
import type { R } from '@/types/api'

/**
 * API Token VO（token 明文只在创建响应中返回一次）
 */
export interface ApiTokenVO {
  id: number
  name: string
  token?: string
  scopes: string
  status: number
  lastUsedAt?: string
  expiresAt?: string
  createTime: string
}

/**
 * 创建 API Token DTO
 */
export interface CreateApiTokenDTO {
  name: string
  expiresInDays?: number
  scopes?: string
}

/**
 * 查询当前用户的 API Token 列表
 */
export function getApiTokenList() {
  return request.get<R<ApiTokenVO[]>>('/api/v1/base/api-tokens')
}

/**
 * 创建 API Token（响应中的 token 明文只显示一次）
 */
export function createApiToken(data: CreateApiTokenDTO) {
  return request.post<R<ApiTokenVO>>('/api/v1/base/api-tokens', data)
}

/**
 * 撤销 API Token（撤销后立即失效）
 */
export function revokeApiToken(id: number) {
  return request.delete<R<void>>(`/api/v1/base/api-tokens/${id}`)
}
