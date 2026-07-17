/**
 * Webhook 订阅 API
 */
import request from '../request'
import type { R } from '@/types/api'

/** 通知渠道 */
export type WebhookChannel = 'WEBHOOK' | 'WE_COM'

/** Webhook VO（secret 明文只在创建响应中返回一次） */
export interface WebhookVO {
  id: number
  name: string
  url: string
  channel: WebhookChannel
  secret?: string
  events: string
  enabled: number
  lastTriggeredAt?: string
  lastStatus?: string
  lastError?: string
  failCount: number
  createTime: string
}

/** 创建 Webhook DTO */
export interface CreateWebhookDTO {
  name: string
  url: string
  channel?: WebhookChannel
  secret?: string
  events?: string[]
}

const BASE_URL = '/api/v1/notifications/webhooks'

/** 查询当前用户的 webhook 列表 */
export function listWebhooks() {
  return request.get<R<WebhookVO[]>>(BASE_URL)
}

/** 创建 webhook（响应中的 secret 明文只显示一次） */
export function createWebhook(data: CreateWebhookDTO) {
  return request.post<R<WebhookVO>>(BASE_URL, data)
}

/** 删除 webhook */
export function deleteWebhook(id: number) {
  return request.delete<R<void>>(`${BASE_URL}/${id}`)
}

/** 发送测试消息 */
export function testWebhook(id: number) {
  return request.post<R<{ success: boolean }>>(`${BASE_URL}/${id}/test`)
}
