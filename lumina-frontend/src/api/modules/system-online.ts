/**
 * 在线用户 API
 */
import request from '../request'
import type { R } from '@/types/api'

export interface OnlineUserVO {
  userId: number
  username: string
  loginTime: string
}

export function getOnlineUsers(username?: string) {
  return request.get<R<OnlineUserVO[]>>('/api/v1/base/users/online', { params: { username } })
}

export function forceLogout(userId: number) {
  return request.delete<R<void>>(`/api/v1/base/users/online/${userId}`)
}
