/**
 * Prompt 管理 API
 */
import request from '../request'
import type { R } from '@/types/api'

export interface PromptVO {
  id: number
  name: string
  version: number
  content: string
  description?: string
  variables?: string
  status: number
  isActive: number
  tenantId: number
  createTime: string
  updateTime: string
}

export interface PromptDTO {
  name: string
  content?: string
  description?: string
  variables?: string
}

export function listPrompts(params?: { name?: string; pageNum?: number; pageSize?: number }) {
  return request.get<R<PromptVO[]>>('/api/v1/prompts', { params })
}

export function getPromptVersions(name: string) {
  return request.get<R<PromptVO[]>>(`/api/v1/prompts/${name}/versions`)
}

export function getActivePrompt(name: string) {
  return request.get<R<PromptVO>>(`/api/v1/prompts/${name}/active`)
}

export function createPrompt(data: PromptDTO) {
  return request.post<R<PromptVO>>('/api/v1/prompts', data)
}

export function updatePrompt(id: number, data: PromptDTO) {
  return request.put<R<PromptVO>>(`/api/v1/prompts/${id}`, data)
}

export function publishPrompt(id: number) {
  return request.post<R<PromptVO>>(`/api/v1/prompts/${id}/publish`)
}

export function newPromptVersion(id: number, data: PromptDTO) {
  return request.post<R<PromptVO>>(`/api/v1/prompts/${id}/new-version`, data)
}

export function deletePrompt(id: number) {
  return request.delete<R<void>>(`/api/v1/prompts/${id}`)
}
