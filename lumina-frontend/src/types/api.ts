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
