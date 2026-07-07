import request from '../request'
import type { R } from '@/types/api'

export interface KnowledgeBaseVO {
  id: number
  name: string
  description?: string
  visibility: 'PRIVATE' | 'TEAM' | 'PUBLIC'
  tenantId?: number
  createTime?: string
}

export interface KnowledgeBaseDTO {
  name: string
  description?: string
  visibility?: string
}

export function createKnowledgeBase(data: KnowledgeBaseDTO) {
  return request.post<R<KnowledgeBaseVO>>('/api/v1/knowledge-bases', data)
}

export function listKnowledgeBases(params?: { name?: string }) {
  return request.get<R<KnowledgeBaseVO[]>>('/api/v1/knowledge-bases', { params })
}

export function deleteKnowledgeBase(id: number) {
  return request.delete<R<void>>(`/api/v1/knowledge-bases/${id}`)
}

export function mountKnowledgeBase(agentId: number, kbId: number) {
  return request.post<R<void>>(`/api/v1/knowledge-bases/${kbId}/agents/${agentId}/mount`)
}

export function unmountKnowledgeBase(agentId: number, kbId: number) {
  return request.delete<R<void>>(`/api/v1/knowledge-bases/${kbId}/agents/${agentId}/mount`)
}

export function getAgentKnowledgeBases(agentId: number) {
  return request.get<R<KnowledgeBaseVO[]>>(`/api/v1/knowledge-bases/agents/${agentId}`)
}
