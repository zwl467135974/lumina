/**
 * Agent 相关 API
 */
import request from '../request'
import { fetchEventSource } from '@microsoft/fetch-event-source'
import { getToken } from '@/utils'
import type { R, PageResult } from '@/types/api'
import type { AgentVO, CreateAgentDTO, UpdateAgentDTO, QueryAgentDTO } from '@/types/api'

/**
 * 流式输出片段
 */
export interface StreamChunk {
  /** 片段类型：REASONING_CHUNK / ACTING_CHUNK / FINAL / ERROR 等 */
  type: string
  /** 片段文本内容 */
  content: string
  /** 是否为最后一片 */
  last: boolean
}

/**
 * 流式执行回调
 */
export interface StreamCallbacks {
  onChunk: (chunk: StreamChunk) => void
  onError?: (err: Error) => void
  onClose?: () => void
}

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

/**
 * 流式执行 Agent（SSE，逐片段回调）
 *
 * <p>因原生 EventSource 仅支持 GET 且无法携带 Authorization Header，
 * 这里使用 @microsoft/fetch-event-source 以 POST 方式订阅 SSE。
 *
 * @param id    Agent ID
 * @param task  任务描述
 * @param cb    回调（onChunk 必填，onError/onClose 可选）
 * @returns AbortController（调用 .abort() 中断流式）
 */
/**
 * 多模态执行 Agent（文本 + 图片，同步返回）
 *
 * @param id            Agent ID
 * @param task          任务描述
 * @param fileUuids     图片文件 UUID 列表
 * @param conversationId 会话 UUID（可选）
 */
export function executeMultimodalAgent(id: number, task: string, fileUuids: string[], conversationId?: string) {
  return request.post<R<string>>(`/api/v1/agents/${id}/execute/multimodal`, {
    task,
    fileUuids,
    conversationId
  }, {
    timeout: 120000
  })
}

export function streamExecuteAgent(id: number, task: string, cb: StreamCallbacks, conversationId?: string): AbortController {
  const controller = new AbortController()
  const baseURL = import.meta.env.VITE_API_BASE_URL || ''
  const token = getToken()
  let url = `${baseURL}/api/v1/agents/${id}/execute/stream?task=${encodeURIComponent(task)}`
  if (conversationId) {
    url += `&conversationId=${encodeURIComponent(conversationId)}`
  }

  fetchEventSource(url, {
    method: 'POST',
    headers: {
      Accept: 'text/event-stream',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    signal: controller.signal,
    openWhenHidden: true,
    async onopen(response) {
      if (!response.ok) {
        throw new Error(`流式连接失败: ${response.status} ${response.statusText}`)
      }
    },
    onmessage(ev) {
      let chunk: StreamChunk
      try {
        chunk = JSON.parse(ev.data) as StreamChunk
      } catch {
        chunk = { type: ev.event || 'CHUNK', content: ev.data, last: false }
      }
      cb.onChunk(chunk)
    },
    onerror(err) {
      cb.onError?.(err instanceof Error ? err : new Error(String(err)))
      throw err
    },
    onclose() {
      cb.onClose?.()
    }
  })

  return controller
}

/**
 * 流式多模态执行 Agent（SSE，文本 + 图片，逐片段回调）
 *
 * @param id            Agent ID
 * @param task          任务描述
 * @param fileUuids     图片文件 UUID 列表
 * @param cb            回调
 * @param conversationId 会话 UUID（可选）
 */
export function streamExecuteMultimodalAgent(
  id: number,
  task: string,
  fileUuids: string[],
  cb: StreamCallbacks,
  conversationId?: string
): AbortController {
  const controller = new AbortController()
  const baseURL = import.meta.env.VITE_API_BASE_URL || ''
  const token = getToken()
  const url = `${baseURL}/api/v1/agents/${id}/execute/multimodal/stream`

  fetchEventSource(url, {
    method: 'POST',
    headers: {
      Accept: 'text/event-stream',
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: JSON.stringify({ task, fileUuids, conversationId }),
    signal: controller.signal,
    openWhenHidden: true,
    async onopen(response) {
      if (!response.ok) {
        throw new Error(`流式连接失败: ${response.status} ${response.statusText}`)
      }
    },
    onmessage(ev) {
      let chunk: StreamChunk
      try {
        chunk = JSON.parse(ev.data) as StreamChunk
      } catch {
        chunk = { type: ev.event || 'CHUNK', content: ev.data, last: false }
      }
      cb.onChunk(chunk)
    },
    onerror(err) {
      cb.onError?.(err instanceof Error ? err : new Error(String(err)))
      throw err
    },
    onclose() {
      cb.onClose?.()
    }
  })

  return controller
}
