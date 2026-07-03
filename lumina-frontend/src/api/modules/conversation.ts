/**
 * 会话相关 API
 */
import request from '../request'
import type { R, PageResult } from '@/types/api'

export interface ConversationVO {
  conversationUuid: string
  agentId: number
  title: string | null
  messageCount: number
  status: number
  createTime: string
  updateTime: string
}

export interface MessageVO {
  messageId: number
  role: string
  content: string
  tokenCount: number
  durationMs: number | null
  createTime: string
  /** 关联文件 UUID 列表（JSON 字符串，后端返回） */
  fileIds?: string
  /** 图片 URL 列表（前端解析 fileIds 后填充，仅本地显示） */
  images?: string[]
}

/**
 * 创建会话
 */
export function createConversation(agentId: number, title?: string) {
  const params: Record<string, string> = { agentId: String(agentId) }
  if (title) params.title = title
  return request.post<R<ConversationVO>>('/api/v1/conversations', undefined, { params })
}

/**
 * 分页查询会话列表
 */
export function listConversations(agentId?: number, pageNum = 1, pageSize = 20) {
  return request.get<R<PageResult<ConversationVO>>>('/api/v1/conversations', {
    params: { agentId, pageNum, pageSize }
  })
}

/**
 * 获取会话详情
 */
export function getConversation(uuid: string) {
  return request.get<R<ConversationVO>>(`/api/v1/conversations/${uuid}`)
}

/**
 * 删除会话
 */
export function deleteConversation(uuid: string) {
  return request.delete<R<void>>(`/api/v1/conversations/${uuid}`)
}

/**
 * 分页查询会话历史消息
 */
export function listMessages(uuid: string, pageNum = 1, pageSize = 50) {
  return request.get<R<PageResult<MessageVO>>>(`/api/v1/conversations/${uuid}/messages`, {
    params: { pageNum, pageSize }
  })
}
