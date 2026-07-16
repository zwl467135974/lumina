/**
 * A/B 测试 API
 */
import request from '../request'
import type { R } from '@/types/api'

/** 变体配置 */
export interface AbVariant {
  id?: number
  name: string
  weight: number
  llmConfig?: string
  promptName?: string
  description?: string
}

/** 变体报告 */
export interface VariantReport {
  variantName: string
  exposures: number
  successRate: number
  avgLatencyMs: number
  avgTokens: number
}

/** 实验报告 */
export interface AbExperimentReport {
  totalExposures: number
  variants: VariantReport[]
}

/** 实验VO */
export interface AbExperimentVO {
  id: number
  name: string
  description?: string
  agentId: number
  trafficPercent: number
  status: string
  startTime?: string
  endTime?: string
  createTime?: string
  variants: AbVariant[]
  report?: AbExperimentReport
}

/** 创建实验DTO */
export interface CreateAbExperimentDTO {
  name: string
  description?: string
  agentId: number
  trafficPercent?: number
  startTime?: string
  endTime?: string
  variants: AbVariant[]
}

const BASE_URL = '/api/v1/ab-tests'

export function listAbExperiments(agentId?: number, status?: string) {
  return request.get<R<AbExperimentVO[]>>(BASE_URL, { params: { agentId, status } })
}

export function getAbExperiment(id: number) {
  return request.get<R<AbExperimentVO>>(`${BASE_URL}/${id}`)
}

export function createAbExperiment(data: CreateAbExperimentDTO) {
  return request.post<R<AbExperimentVO>>(BASE_URL, data)
}

export function startAbExperiment(id: number) {
  return request.put<R<void>>(`${BASE_URL}/${id}/start`)
}

export function pauseAbExperiment(id: number) {
  return request.put<R<void>>(`${BASE_URL}/${id}/pause`)
}

export function completeAbExperiment(id: number) {
  return request.put<R<void>>(`${BASE_URL}/${id}/complete`)
}

export function deleteAbExperiment(id: number) {
  return request.delete<R<void>>(`${BASE_URL}/${id}`)
}
