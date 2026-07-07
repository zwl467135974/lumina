/**
 * 工具监控 API
 */
import request from '../request'
import type { R } from '@/types/api'

export interface ToolStats {
  toolName: string
  totalInvocations: number
  successCount: number
  failureCount: number
  successRate: number
  totalDurationMs: number
  maxDurationMs: number
  minDurationMs: number
  avgDurationMs: number
  lastInvocationTime: number
}

export interface ToolInvocation {
  toolName: string
  category: string | null
  input: string
  output: string | null
  error: string | null
  durationMs: number
  success: boolean
  timestamp: number
  conversationId: string | null
}

export interface BreakerState {
  toolName: string
  open: boolean
  consecutiveFailures: number
  openedAt: number
}

export interface ToolDefinitionVO {
  name: string
  label: string
  description: string | null
  category: string | null
}

export function getTools() {
  return request.get<R<ToolDefinitionVO[]>>('/api/v1/tools')
}

export function getToolStats() {
  return request.get<R<Record<string, ToolStats>>>('/api/v1/tools/stats')
}

export function getToolInvocations(limit = 50) {
  return request.get<R<ToolInvocation[]>>('/api/v1/tools/invocations', { params: { limit } })
}

export function getBreakerStates() {
  return request.get<R<Record<string, BreakerState>>>('/api/v1/tools/breakers')
}

export function clearToolInvocations() {
  return request.delete<R<void>>('/api/v1/tools/invocations')
}
