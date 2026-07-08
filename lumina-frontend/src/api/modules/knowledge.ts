/**
 * 知识库 API
 */
import request from '../request'
import type { R, PageResult } from '@/types/api'

export interface KnowledgeDocumentVO {
  documentId: number
  documentUuid: string
  tenantId?: number
  agentId?: number | null
  kbId?: number | null
  title?: string
  format: string
  language?: string
  embeddingModel?: string
  chunkCount: number
  fileSize?: number
  status: number
  createTime: string
  updateTime?: string
}

export interface SearchResult {
  content: string
  score: number
  metadata?: Record<string, unknown>
}

export function uploadDocument(file: File, agentId?: number, kbId?: number) {
  const formData = new FormData()
  formData.append('file', file)
  if (agentId != null) formData.append('agentId', String(agentId))
  if (kbId != null) formData.append('kbId', String(kbId))
  return request.post<R<string>>('/api/v1/knowledge/documents', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function listDocuments(params: {
  agentId?: number
  kbId?: number
  pageNum?: number
  pageSize?: number
}) {
  return request.get<R<PageResult<KnowledgeDocumentVO>>>('/api/v1/knowledge/documents', { params })
}

export function deleteDocument(uuid: string) {
  return request.delete<R<void>>(`/api/v1/knowledge/documents/${uuid}`)
}

export function searchKnowledge(query: string, limit = 5) {
  return request.post<R<SearchResult[]>>('/api/v1/knowledge/search', null, {
    params: { query, limit }
  })
}
