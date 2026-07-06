/**
 * 成本管理 API
 */
import request from '../request'
import type { R } from '@/types/api'

export interface CostSummary {
  taskCount: number
  totalPromptTokens: number
  totalCompletionTokens: number
  totalTokens: number
  totalCost: number
  currency: string
  topAgents: Array<{
    agentId: number
    tokens: number
    cost: number
  }>
}

export function getCostSummary() {
  return request.get<R<CostSummary>>('/api/v1/cost/summary')
}
