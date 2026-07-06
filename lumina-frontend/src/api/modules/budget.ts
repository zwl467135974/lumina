/**
 * 预算管理 API
 */
import request from '../request'
import type { R } from '@/types/api'

export interface BudgetRuleVO {
  id: number
  ruleName: string
  scopeType: 'TENANT' | 'AGENT' | 'USER'
  scopeId?: number
  periodType: 'DAILY' | 'MONTHLY'
  limitAmount: number
  alertThreshold: number
  status: number
  createTime: string
}

export interface BudgetUsageVO {
  ruleId: number
  ruleName: string
  scopeType: string
  scopeId?: number
  periodType: string
  limitAmount: number
  currentUsage: number
  usagePercent: number
  alertThreshold: number
  status: number
}

export interface BudgetRuleDTO {
  ruleName: string
  scopeType: 'TENANT' | 'AGENT' | 'USER'
  scopeId?: number
  periodType: 'DAILY' | 'MONTHLY'
  limitAmount: number
  alertThreshold?: number
}

export function listBudgetRules() {
  return request.get<R<BudgetRuleVO[]>>('/api/v1/budget/rules')
}

export function createBudgetRule(data: BudgetRuleDTO) {
  return request.post<R<BudgetRuleVO>>('/api/v1/budget/rules', data)
}

export function deleteBudgetRule(id: number) {
  return request.delete<R<void>>(`/api/v1/budget/rules/${id}`)
}

export function getBudgetUsage() {
  return request.get<R<BudgetUsageVO[]>>('/api/v1/budget/usage')
}
