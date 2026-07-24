/**
 * Agent 推理链追踪 API
 */
import http from '@/api/request'
import type { R, PageResult } from '@/types/api'
import type { AgentTraceVO, AgentTraceQuery } from '@/types/agent-trace'

/** 分页查询 Trace 列表 */
export function listAgentTraces(params: AgentTraceQuery) {
  return http.get<R<PageResult<AgentTraceVO>>>('/api/v1/agent-traces', { params })
}

/** 查询 Trace 详情（含完整 steps） */
export function getAgentTraceDetail(traceUuid: string) {
  return http.get<R<AgentTraceVO>>(`/api/v1/agent-traces/${traceUuid}`)
}
