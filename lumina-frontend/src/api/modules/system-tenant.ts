/**
 * 租户管理 API
 */
import request from '../request'
import type { R, PageResult } from '@/types/api'
import type {
  TenantVO,
  CreateTenantDTO,
  UpdateTenantDTO,
  QueryTenantDTO
} from '@/types/api'

/**
 * 查询租户列表（分页）
 */
export function getTenantList(params: QueryTenantDTO) {
  return request.get<R<PageResult<TenantVO>>>('/api/v1/tenants', { params })
}

/**
 * 根据ID查询租户
 */
export function getTenantById(id: number) {
  return request.get<R<TenantVO>>(`/api/v1/tenants/${id}`)
}

/**
 * 创建租户
 */
export function createTenant(data: CreateTenantDTO) {
  return request.post<R<TenantVO>>('/api/v1/tenants', data)
}

/**
 * 更新租户
 */
export function updateTenant(id: number, data: UpdateTenantDTO) {
  return request.put<R<TenantVO>>(`/api/v1/tenants/${id}`, data)
}

/**
 * 删除租户
 */
export function deleteTenant(id: number) {
  return request.delete<R<void>>(`/api/v1/tenants/${id}`)
}

/**
 * 启用/禁用租户
 */
export function updateTenantStatus(id: number, status: number) {
  return request.put<R<void>>(`/api/v1/tenants/${id}/status`, { status })
}
