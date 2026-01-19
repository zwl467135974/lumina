/**
 * Agent 相关 API
 */
import request from '../request'
import type { R, PageResult } from '@/types/api'
import type { AgentVO, CreateAgentDTO, UpdateAgentDTO, QueryAgentDTO } from '@/types/api'

/**
 * 创建 Agent
 */
export function createAgent(data: CreateAgentDTO) {
  return request.post<R<AgentVO>>('/api/v1/agents', data)
}

/**
 * 获取 Agent 详情
 */
export function getAgent(id: number) {
  return request.get<R<AgentVO>>(`/api/v1/agents/${id}`)
}

/**
 * 查询 Agent 列表
 */
export function listAgents(params: QueryAgentDTO) {
  return request.get<R<PageResult<AgentVO>>>('/api/v1/agents', { params })
}

/**
 * 更新 Agent
 */
export function updateAgent(id: number, data: UpdateAgentDTO) {
  return request.put<R<AgentVO>>(`/api/v1/agents/${id}`, data)
}

/**
 * 删除 Agent
 */
export function deleteAgent(id: number) {
  return request.delete<R<void>>(`/api/v1/agents/${id}`)
}

/**
 * 执行 Agent
 */
export function executeAgent(id: number, task: string) {
  return request.post<R<string>>(`/api/v1/agents/${id}/execute`, { task })
}
