/**
 * 权限管理 API
 */
import request from '../request'
import type { R } from '@/types/api'
import type {
  PermissionVO,
  CreatePermissionDTO,
  UpdatePermissionDTO,
  QueryPermissionDTO
} from '@/types/api'

/**
 * 查询权限树
 */
export function getPermissionTree(params?: QueryPermissionDTO) {
  return request.get<R<PermissionVO[]>>('/api/v1/permissions/tree', { params })
}

/**
 * 查询所有权限（不分页）
 */
export function getAllPermissions(params?: QueryPermissionDTO) {
  return request.get<R<PermissionVO[]>>('/api/v1/permissions', { params })
}

/**
 * 根据ID查询权限
 */
export function getPermissionById(id: number) {
  return request.get<R<PermissionVO>>(`/api/v1/permissions/${id}`)
}

/**
 * 创建权限
 */
export function createPermission(data: CreatePermissionDTO) {
  return request.post<R<PermissionVO>>('/api/v1/permissions', data)
}

/**
 * 更新权限
 */
export function updatePermission(id: number, data: UpdatePermissionDTO) {
  return request.put<R<PermissionVO>>(`/api/v1/permissions/${id}`, data)
}

/**
 * 删除权限
 */
export function deletePermission(id: number) {
  return request.delete<R<void>>(`/api/v1/permissions/${id}`)
}
