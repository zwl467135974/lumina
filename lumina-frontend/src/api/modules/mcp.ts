/**
 * MCP（Model Context Protocol）管理 API
 *
 * 提供 MCP Server 连接状态与工具列表的只读查询。
 * MCP 配置通过 Nacos/YAML 管理（lumina.mcp.enabled + lumina.mcp.servers）。
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

/** 查询 MCP 全局状态与已连接 Server 列表 */
export function getMcpServers() {
  return request.get<R<McpStatusVO>>('/api/v1/mcp/servers')
}

/** 查询所有已注册的 MCP 工具 */
export function getMcpTools() {
  return request.get<R<McpToolVO[]>>('/api/v1/mcp/tools')
}
