/**
 * 用户管理 API
 */
import request from '../request'
import type { R, PageResult } from '@/types/api'
import type {
  UserVO,
  CreateUserDTO,
  UpdateUserDTO,
  QueryUserDTO,
  ResetPasswordDTO
} from '@/types/api'

/**
 * 查询用户列表（分页）
 */
export function getUserList(params: QueryUserDTO) {
  return request.get<R<PageResult<UserVO>>>('/api/v1/users', { params })
}

/**
 * 根据ID查询用户
 */
export function getUserById(id: number) {
  return request.get<R<UserVO>>(`/api/v1/users/${id}`)
}

/**
 * 创建用户
 */
export function createUser(data: CreateUserDTO) {
  return request.post<R<UserVO>>('/api/v1/users', data)
}

/**
 * 更新用户
 */
export function updateUser(id: number, data: UpdateUserDTO) {
  return request.put<R<UserVO>>(`/api/v1/users/${id}`, data)
}

/**
 * 删除用户
 */
export function deleteUser(id: number) {
  return request.delete<R<void>>(`/api/v1/users/${id}`)
}

/**
 * 重置用户密码
 */
export function resetPassword(data: ResetPasswordDTO) {
  return request.post<R<void>>('/api/v1/users/reset-password', data)
}

/**
 * 分配用户角色
 */
export function assignRoles(userId: number, roleIds: number[]) {
  return request.post<R<void>>(`/api/v1/users/${userId}/roles`, { roleIds })
}

/**
 * 启用/禁用用户
 */
export function updateUserStatus(id: number, status: number) {
  return request.put<R<void>>(`/api/v1/users/${id}/status`, { status })
}
