/**
 * 长期记忆 API
 */
import request from '../request'
import type { R } from '@/types/api'

export interface LongTermMemoryVO {
  id: number
  userId: number
  agentId: number | null
  conversationId: string | null
  memoryType: string
  content: string
  importance: number
  accessCount: number
  lastAccessed: string | null
  createTime: string
}

const BASE_URL = '/api/v1/long-term-memories'

export function listLongTermMemories(agentId?: number, limit = 100) {
  return request.get<R<LongTermMemoryVO[]>>(BASE_URL, { params: { agentId, limit } })
}

export function deleteLongTermMemory(id: number) {
  return request.delete<R<void>>(`${BASE_URL}/${id}`)
}

export function deleteAllLongTermMemories(agentId?: number) {
  return request.delete<R<void>>(BASE_URL, { params: { agentId } })
}
