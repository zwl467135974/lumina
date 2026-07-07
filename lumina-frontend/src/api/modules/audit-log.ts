import request from '../request'
import type { R, PageResult } from '@/types/api'

export interface AuditLogVO {
  auditId: number
  tenantId?: number
  userId?: number
  username?: string
  module: string
  action: string
  targetType?: string
  targetId?: string
  description?: string
  requestMethod?: string
  requestUrl?: string
  requestIp?: string
  status: number
  errorMsg?: string
  durationMs?: number
  createTime?: string
}

export function listAuditLogs(params: {
  module?: string
  action?: string
  userId?: number
  pageNum?: number
  pageSize?: number
}) {
  return request.get<R<PageResult<AuditLogVO>>>('/api/v1/audit-logs', { params })
}
