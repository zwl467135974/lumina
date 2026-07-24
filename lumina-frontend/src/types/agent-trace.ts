/**
 * Agent 推理链追踪类型定义
 */

/** Trace 列表项 */
export interface AgentTraceVO {
  traceId: number
  traceUuid: string
  taskUuid: string | null
  conversationUuid: string | null
  agentId: number | null
  agentName: string | null
  agentType: string | null
  inputText: string | null
  outputText: string | null
  status: string
  promptTokens: number
  completionTokens: number
  totalTokens: number
  durationMs: number | null
  startedAt: string | null
  finishedAt: string | null
  steps: TraceStep[] | null
  createTime: string | null
}

/** 推理链步骤 */
export interface TraceStep {
  seq: number
  type: string
  name: string | null
  input: string | null
  output: string | null
  promptTokens: number | null
  completionTokens: number | null
  durationMs: number
}

/** 查询参数 */
export interface AgentTraceQuery {
  agentId?: number
  status?: string
  pageNum: number
  pageSize: number
}

/** 步骤类型标签映射 */
export const STEP_TYPE_TAG: Record<string, { label: string; color: string }> = {
  REASONING: { label: '推理', color: 'primary' },
  TOOL_CALL: { label: '工具', color: 'warning' },
  RETRIEVAL: { label: '检索', color: 'success' },
  MEMORY_INJECTION: { label: '记忆', color: 'info' },
  SUMMARIZE: { label: '汇总', color: 'info' }
}

/** 状态标签映射 */
export const TRACE_STATUS_TAG: Record<string, { label: string; color: string }> = {
  SUCCESS: { label: '成功', color: 'success' },
  RUNNING: { label: '执行中', color: 'warning' },
  FAILED: { label: '失败', color: 'danger' }
}
