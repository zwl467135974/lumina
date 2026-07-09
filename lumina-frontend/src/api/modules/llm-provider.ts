/**
 * LLM 模型供应商管理 API
 */
import request from '../request'
import type { R } from '@/types/api'

export interface LlmProviderVO {
  id: number
  name: string
  provider: string
  baseUrl: string
  apiKeyMasked: string
  hasApiKey: boolean
  defaultModel: string
  defaultParams: string
  status: number
  createTime: string
  updateTime: string
  _testing?: boolean
}

export interface CreateLlmProviderDTO {
  name: string
  provider: string
  baseUrl?: string
  apiKey?: string
  defaultModel?: string
  defaultParams?: string
  status?: number
}

export interface QueryLlmProviderDTO {
  name?: string
  provider?: string
  status?: number
}

export function getLlmProviderList(params?: QueryLlmProviderDTO) {
  return request.get<R<LlmProviderVO[]>>('/api/v1/agent/llm-providers', { params })
}

export function getLlmProviderById(id: number) {
  return request.get<R<LlmProviderVO>>(`/api/v1/agent/llm-providers/${id}`)
}

export function createLlmProvider(data: CreateLlmProviderDTO) {
  return request.post<R<LlmProviderVO>>('/api/v1/agent/llm-providers', data)
}

export function updateLlmProvider(id: number, data: CreateLlmProviderDTO) {
  return request.put<R<LlmProviderVO>>(`/api/v1/agent/llm-providers/${id}`, data)
}

export function deleteLlmProvider(id: number) {
  return request.delete<R<void>>(`/api/v1/agent/llm-providers/${id}`)
}

export function testLlmProvider(id: number) {
  return request.post<R<boolean>>(`/api/v1/agent/llm-providers/${id}/test`)
}
