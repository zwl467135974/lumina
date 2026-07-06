/**
 * 工作流 API
 */
import request from '../request'
import { fetchEventSource } from '@microsoft/fetch-event-source'
import { getToken } from '@/utils'
import type { R } from '@/types/api'

/** 工作流定义 */
export interface WorkflowDefinitionVO {
  id: number
  name: string
  description?: string
  definitionYaml: string
  version: number
  status: number
  tenantId: number
  createTime: string
  updateTime: string
}

/** 工作流实例 */
export interface WorkflowInstanceVO {
  id: number
  definitionId: number
  definitionName: string
  definitionVersion: number
  status: string
  input?: string
  output?: string
  errorMessage?: string
  currentNodeId?: string
  createTime: string
  updateTime: string
}

/** 执行日志 */
export interface WorkflowExecutionLogVO {
  id: number
  instanceId: number
  nodeId: string
  nodeType: string
  nodeName?: string
  status: string
  input?: string
  output?: string
  durationMs?: number
  errorMessage?: string
  createTime: string
}

/** 工作流模板 */
export interface WorkflowTemplateVO {
  name: string
  description?: string
  definitionYaml: string
}

/** 创建/更新 DTO */
export interface WorkflowDTO {
  name: string
  description?: string
  definitionYaml: string
}

/** 执行 DTO */
export interface ExecuteWorkflowDTO {
  inputs?: Record<string, unknown>
}

export function listWorkflows(params?: { name?: string; status?: number; pageNum?: number; pageSize?: number }) {
  return request.get<R<WorkflowDefinitionVO[]>>('/api/v1/workflows', { params })
}

export function getWorkflow(id: number) {
  return request.get<R<WorkflowDefinitionVO>>(`/api/v1/workflows/${id}`)
}

export function createWorkflow(data: WorkflowDTO) {
  return request.post<R<WorkflowDefinitionVO>>('/api/v1/workflows', data)
}

export function updateWorkflow(id: number, data: WorkflowDTO) {
  return request.put<R<WorkflowDefinitionVO>>(`/api/v1/workflows/${id}`, data)
}

export function deleteWorkflow(id: number) {
  return request.delete<R<void>>(`/api/v1/workflows/${id}`)
}

export function publishWorkflow(id: number) {
  return request.post<R<void>>(`/api/v1/workflows/${id}/publish`)
}

export function executeWorkflow(id: number, data: ExecuteWorkflowDTO) {
  return request.post<R<WorkflowInstanceVO>>(`/api/v1/workflows/${id}/execute`, data, { timeout: 120000 })
}

export interface WorkflowStreamEvent {
  event: string
  instanceId?: number
  nodeId?: string
  nodeName?: string
  result?: string
  durationMs?: number
  error?: string
  status?: string
}

export function streamExecuteWorkflow(
  id: number,
  data: ExecuteWorkflowDTO,
  cb: {
    onEvent: (event: WorkflowStreamEvent) => void
    onError?: (err: Error) => void
    onClose?: () => void
  }
): AbortController {
  const controller = new AbortController()
  const baseURL = import.meta.env.VITE_API_BASE_URL || ''
  const token = getToken()
  const url = `${baseURL}/api/v1/workflows/${id}/execute/stream`

  fetchEventSource(url, {
    method: 'POST',
    headers: {
      Accept: 'text/event-stream',
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: JSON.stringify(data),
    signal: controller.signal,
    openWhenHidden: true,
    async onopen(response) {
      if (!response.ok) {
        throw new Error(`SSE 连接失败: ${response.status} ${response.statusText}`)
      }
    },
    onmessage(ev) {
      let event: WorkflowStreamEvent
      try {
        event = JSON.parse(ev.data) as WorkflowStreamEvent
      } catch {
        event = { event: ev.event || 'UNKNOWN' }
      }
      cb.onEvent(event)

      if (event.event === 'WORKFLOW_COMPLETED' || event.event === 'WORKFLOW_FAILED') {
        controller.abort()
        cb.onClose?.()
      }
    },
    onerror(err) {
      cb.onError?.(err instanceof Error ? err : new Error(String(err)))
    },
    onclose() {
      cb.onClose?.()
    }
  })

  return controller
}

export function listInstances(params?: { definitionId?: number; status?: string; pageNum?: number; pageSize?: number }) {
  return request.get<R<WorkflowInstanceVO[]>>('/api/v1/workflows/instances', { params })
}

export function getInstanceLogs(instanceId: number) {
  return request.get<R<WorkflowExecutionLogVO[]>>(`/api/v1/workflows/instances/${instanceId}/logs`)
}

export function getWorkflowTemplates() {
  return request.get<R<WorkflowTemplateVO[]>>('/api/v1/workflows/templates')
}
