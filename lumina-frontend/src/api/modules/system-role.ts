/**
 * 角色管理 API
 */
import request from '../request'
import type { R, PageResult } from '@/types/api'
import type {
  RoleVO,
  CreateRoleDTO,
  UpdateRoleDTO,
  QueryRoleDTO
} from '@/types/api'

/**
 * 查询角色列表（分页）
 */
export function getRoleList(params: QueryRoleDTO) {
  return request.get<R<PageResult<RoleVO>>>('/api/v1/roles', { params })
}

/**
 * 查询所有角色（不分页）
 */
export function getAllRoles() {
  return request.get<R<RoleVO[]>>('/api/v1/roles/all')
}

/**
 * 根据ID查询角色
 */
export function getRoleById(id: number) {
  return request.get<R<RoleVO>>(`/api/v1/roles/${id}`)
}

/**
 * 创建角色
 */
export function createRole(data: CreateRoleDTO) {
  return request.post<R<RoleVO>>('/api/v1/roles', data)
}

/**
 * 更新角色
 */
export function updateRole(id: number, data: UpdateRoleDTO) {
  return request.put<R<RoleVO>>(`/api/v1/roles/${id}`, data)
}

/**
 * 删除角色
 */
export function deleteRole(id: number) {
  return request.delete<R<void>>(`/api/v1/roles/${id}`)
}

/**
 * 分配角色权限
 */
export function assignPermissions(roleId: number, permissionIds: number[]) {
  return request.post<R<void>>(`/api/v1/roles/${roleId}/permissions`, {
    permissionIds
  })
}

/**
 * 启用/禁用角色
 */
export function updateRoleStatus(id: number, status: number) {
  return request.put<R<void>>(`/api/v1/roles/${id}/status`, { status })
}
