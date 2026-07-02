/**
 * 知识库 API
 */
import request from '../request'
import type { R, PageResult } from '@/types/api'

/**
 * 知识库文档
 */
export interface KnowledgeDocumentVO {
  documentId: number
  documentUuid: string
  tenantId?: number
  agentId?: number | null
  title?: string
  format: string
  chunkCount: number
  fileSize?: number
  status: number
  createTime: string
  updateTime?: string
}

/**
 * 检索结果片段
 */
export interface SearchResult {
  content: string
  score: number
  metadata?: Record<string, unknown>
}

/**
 * 上传文档
 *
 * @param file    文件对象（txt/md/pdf/doc/docx）
 * @param agentId 关联 Agent ID（可选，null=全局知识库）
 */
export function uploadDocument(file: File, agentId?: number) {
  const formData = new FormData()
  formData.append('file', file)
  if (agentId != null) {
    formData.append('agentId', String(agentId))
  }
  return request.post<R<string>>('/api/v1/knowledge/documents', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/**
 * 文档列表（分页）
 */
export function listDocuments(params: {
  agentId?: number
  pageNum?: number
  pageSize?: number
}) {
  return request.get<R<PageResult<KnowledgeDocumentVO>>>('/api/v1/knowledge/documents', { params })
}

/**
 * 删除文档（同时清理向量数据）
 */
export function deleteDocument(uuid: string) {
  return request.delete<R<void>>(`/api/v1/knowledge/documents/${uuid}`)
}

/**
 * 检索测试
 */
export function searchKnowledge(query: string, limit = 5) {
  return request.post<R<SearchResult[]>>('/api/v1/knowledge/search', null, {
    params: { query, limit }
  })
}
