/**
 * Agent 定时触发器 API
 */
import request from '../request'
import type { R, PageResult } from '@/types/api'

/** 错过策略 */
export type MisfirePolicy = 'FIRE_ONCE' | 'SKIP'

/** 定时触发器 VO */
export interface AgentTriggerVO {
  id: number
  name: string
  agentId: number
  workflowId?: number
  cronExpr: string
  inputText: string
  misfirePolicy: MisfirePolicy
  enabled: number
  nextFireAt?: string
  lastFireAt?: string
  lastStatus?: string
  lastError?: string
  failCount: number
  createTime?: string
  nextFireAtDescription?: string
}

/** 创建定时触发器 DTO */
export interface CreateAgentTriggerDTO {
  name: string
  agentId: number
  workflowId?: number
  /** Spring 6 字段 cron：秒 分 时 日 月 周，如 0 0 9 * * * = 每天 9 点 */
  cronExpr: string
  inputText: string
  misfirePolicy?: MisfirePolicy
}

const BASE_URL = '/api/v1/agents/triggers'

/** 创建触发器 */
export function createTrigger(data: CreateAgentTriggerDTO) {
  return request.post<R<AgentTriggerVO>>(BASE_URL, data)
}

/** 分页查询触发器列表 */
export function listTriggers(params: { pageNum?: number; pageSize?: number }) {
  return request.get<R<PageResult<AgentTriggerVO>>>(BASE_URL, { params })
}

/** 查询触发器详情 */
export function getTrigger(id: number) {
  return request.get<R<AgentTriggerVO>>(`${BASE_URL}/${id}`)
}

/** 删除触发器 */
export function deleteTrigger(id: number) {
  return request.delete<R<void>>(`${BASE_URL}/${id}`)
}

/** 暂停触发器 */
export function pauseTrigger(id: number) {
  return request.put<R<void>>(`${BASE_URL}/${id}/pause`)
}

/** 恢复触发器 */
export function resumeTrigger(id: number) {
  return request.put<R<void>>(`${BASE_URL}/${id}/resume`)
}

/** 手动立即触发一次 */
export function triggerNow(id: number) {
  return request.post<R<{ submitted: boolean }>>(`${BASE_URL}/${id}/trigger-now`)
}
