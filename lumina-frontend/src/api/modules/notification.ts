/**
 * 通知中心 API
 */
import { fetchEventSource } from '@microsoft/fetch-event-source'
import request from '../request'
import { getToken } from '@/utils/auth'
import type { R, PageResult } from '@/types/api'

/** 通知 VO */
export interface NotificationVO {
  id: number
  userId: number
  category: string
  title: string
  content: string
  severity: string
  refType?: string
  refId?: string
  isRead: number
  createTime: string
  readTime?: string
}

/** 查询参数 */
export interface NotificationQuery {
  category?: string
  isRead?: number
  pageNum?: number
  pageSize?: number
}

const BASE_URL = '/api/v1/notifications'

/** 分页查询通知 */
export function listNotifications(params: NotificationQuery) {
  return request.get<R<PageResult<NotificationVO>>>(BASE_URL, { params })
}

/** 获取未读计数 */
export function getUnreadCount() {
  return request.get<R<{ count: number }>>(`${BASE_URL}/unread-count`)
}

/** 标记单条已读 */
export function markAsRead(id: number) {
  return request.put<R<void>>(`${BASE_URL}/${id}/read`)
}

/** 全部已读 */
export function markAllAsRead() {
  return request.put<R<void>>(`${BASE_URL}/read-all`)
}

/** SSE 订阅通知流 */
export function streamNotifications(
  callbacks: {
    onMessage: (notification: NotificationVO) => void
    onError?: () => void
    onClose?: () => void
  }
): AbortController {
  const controller = new AbortController()
  const token = getToken() || ''

  fetchEventSource(`${BASE_URL}/stream`, {
    method: 'GET',
    headers: {
      Authorization: `Bearer ${token}`,
    },
    signal: controller.signal,
    openWhenHidden: true,
    onmessage(ev) {
      if (ev.event === 'notification' && ev.data) {
        try {
          const notification = JSON.parse(ev.data) as NotificationVO
          callbacks.onMessage(notification)
        } catch {
          // 忽略解析错误
        }
      }
    },
    onclose() {
      callbacks.onClose?.()
    },
    onerror(err) {
      callbacks.onError?.()
      throw err // 停止重连
    },
  })

  return controller
}
