/**
 * MCP（Model Context Protocol）管理 API
 *
 * 提供 MCP Server 连接状态、工具列表查询，以及运行时注册/删除/重连操作。
 */
import request from '../request'
import type { R } from '@/types/api'

/** MCP Server 运行时状态 */
export interface McpServerVO {
  name: string
  transport: string
  connected: boolean
  toolCount: number
  command: string | null
  url: string | null
}

/** MCP 全局状态 */
export interface McpStatusVO {
  enabled: boolean
  servers: McpServerVO[]
}

/** MCP 工具信息 */
export interface McpToolVO {
  name: string
  description: string | null
  category: string | null
  serverName: string
}

/** MCP Server 注册请求 */
export interface McpServerRegisterDTO {
  name: string
  transport: 'stdio' | 'http' | 'streamable-http'
  command?: string
  args?: string[]
  url?: string
  headers?: Record<string, string>
}

/** 查询 MCP 全局状态与已连接 Server 列表 */
export function getMcpServers() {
  return request.get<R<McpStatusVO>>('/api/v1/mcp/servers')
}

/** 查询所有已注册的 MCP 工具 */
export function getMcpTools() {
  return request.get<R<McpToolVO[]>>('/api/v1/mcp/tools')
}

/** 运行时注册 MCP Server */
export function registerMcpServer(data: McpServerRegisterDTO) {
  return request.post<R<boolean>>('/api/v1/mcp/servers', data)
}

/** 注销 MCP Server */
export function unregisterMcpServer(name: string) {
  return request.delete<R<boolean>>(`/api/v1/mcp/servers/${name}`)
}

/** 重连 MCP Server */
export function reconnectMcpServer(name: string) {
  return request.post<R<boolean>>(`/api/v1/mcp/servers/${name}/reconnect`)
}

/** 健康检查 MCP Server */
export function checkMcpServerHealth(name: string) {
  return request.get<R<boolean>>(`/api/v1/mcp/servers/${name}/health`)
}

