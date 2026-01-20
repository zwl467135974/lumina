/**
 * API 类型定义
 * 与后端接口保持一致
 */

/**
 * 统一响应格式
 */
export interface R<T> {
  code: number
  msg: string
  data: T
  timestamp: number
}

/**
 * 分页结果
 */
export interface PageResult<T> {
  list: T[]
  total: number
  pageNum: number
  pageSize: number
  pages: number
}

/**
 * Agent 相关类型
 */
export interface AgentVO {
  agentId: number
  agentName: string
  agentType: string
  description?: string
  status: number
  createTime: string
  updateTime: string
}

export interface CreateAgentDTO {
  agentName: string
  agentType: string
  description?: string
}

export interface UpdateAgentDTO {
  agentName?: string
  agentType?: string
  description?: string
}

export interface QueryAgentDTO {
  agentName?: string
  agentType?: string
  pageNum?: number
  pageSize?: number
}

/**
 * 用户相关类型
 */
export interface UserInfo {
  userId: number
  username: string
  nickname?: string
  email?: string
  avatar?: string
  roles?: string[]
  permissions?: string[]
}

export interface LoginDTO {
  username: string
  password: string
}

export interface LoginVO {
  token: string
  userInfo: UserInfo
}

export interface UserVO {
  userId: number
  username: string
  nickname?: string
  email?: string
  phone?: string
  avatar?: string
  status: number
  tenantId?: number
  tenantName?: string
  roles?: RoleVO[]
  createTime: string
  updateTime: string
}

export interface CreateUserDTO {
  username: string
  password: string
  nickname?: string
  email?: string
  phone?: string
  roleIds?: number[]
}

export interface UpdateUserDTO {
  nickname?: string
  email?: string
  phone?: string
  status?: number
  roleIds?: number[]
}

export interface QueryUserDTO {
  username?: string
  nickname?: string
  email?: string
  status?: number
  pageNum?: number
  pageSize?: number
}

export interface ResetPasswordDTO {
  userId: number
  newPassword: string
}

/**
 * 角色相关类型
 */
export interface RoleVO {
  roleId: number
  roleName: string
  roleCode: string
  description?: string
  status: number
  permissions?: PermissionVO[]
  createTime: string
  updateTime: string
}

export interface CreateRoleDTO {
  roleName: string
  roleCode: string
  description?: string
  permissionIds?: number[]
}

export interface UpdateRoleDTO {
  roleName?: string
  description?: string
  status?: number
  permissionIds?: number[]
}

export interface QueryRoleDTO {
  roleName?: string
  roleCode?: string
  status?: number
  pageNum?: number
  pageSize?: number
}

/**
 * 权限相关类型
 */
export interface PermissionVO {
  permissionId: number
  permissionName: string
  permissionCode: string
  resourcePath: string
  description?: string
  parentId?: number
  children?: PermissionVO[]
  createTime: string
  updateTime: string
}

export interface CreatePermissionDTO {
  permissionName: string
  permissionCode: string
  resourcePath: string
  description?: string
  parentId?: number
}

export interface UpdatePermissionDTO {
  permissionName?: string
  resourcePath?: string
  description?: string
  parentId?: number
}

export interface QueryPermissionDTO {
  permissionName?: string
  permissionCode?: string
  resourcePath?: string
  parentId?: number
}

/**
 * 租户相关类型
 */
export interface TenantVO {
  tenantId: number
  tenantName: string
  tenantCode: string
  contact?: string
  phone?: string
  email?: string
  status: number
  createTime: string
  updateTime: string
}

export interface CreateTenantDTO {
  tenantName: string
  tenantCode: string
  contact?: string
  phone?: string
  email?: string
}

export interface UpdateTenantDTO {
  tenantName?: string
  contact?: string
  phone?: string
  email?: string
  status?: number
}

export interface QueryTenantDTO {
  tenantName?: string
  tenantCode?: string
  status?: number
  pageNum?: number
  pageSize?: number
}
