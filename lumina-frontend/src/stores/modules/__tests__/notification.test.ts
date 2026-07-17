/**
 * notification store 测试
 */
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

vi.mock('@/api/modules/notification', () => ({
  listNotifications: vi.fn(),
  getUnreadCount: vi.fn(),
  markAsRead: vi.fn(),
  markAllAsRead: vi.fn(),
  streamNotifications: vi.fn(() => ({ abort: () => {} })),
}))

import {
  listNotifications,
  getUnreadCount,
  markAsRead,
  markAllAsRead,
} from '@/api/modules/notification'
import { useNotificationStore } from '../notification'

describe('notification store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  describe('fetchUnreadCount', () => {
    it('updates unreadCount from API response', async () => {
      ;(getUnreadCount as any).mockResolvedValue({ data: { count: 5 } })
      const store = useNotificationStore()

      await store.fetchUnreadCount()

      expect(store.unreadCount).toBe(5)
    })

    it('handles API error silently', async () => {
      ;(getUnreadCount as any).mockRejectedValue(new Error('network'))
      const store = useNotificationStore()

      await store.fetchUnreadCount()

      expect(store.unreadCount).toBe(0)
    })
  })

  describe('fetchList', () => {
    it('populates recentList from API', async () => {
      const mockList = [
        { id: 1, title: '通知1', isRead: 0, category: 'TASK', content: '', severity: 'INFO', createTime: '', userId: 1 },
        { id: 2, title: '通知2', isRead: 1, category: 'BUDGET', content: '', severity: 'WARN', createTime: '', userId: 1 },
      ]
      ;(listNotifications as any).mockResolvedValue({ data: { list: mockList, total: 2 } })
      const store = useNotificationStore()

      await store.fetchList()

      expect(store.recentList).toHaveLength(2)
      expect(store.loading).toBe(false)
    })

    it('sets empty list on error', async () => {
      ;(listNotifications as any).mockRejectedValue(new Error('fail'))
      const store = useNotificationStore()

      await store.fetchList()

      expect(store.recentList).toEqual([])
    })

    it('passes pagination params to API', async () => {
      ;(listNotifications as any).mockResolvedValue({ data: { list: [], total: 0 } })
      const store = useNotificationStore()

      await store.fetchList({ pageNum: 2, pageSize: 50, category: 'TASK', isRead: 0 })

      expect(listNotifications).toHaveBeenCalledWith({ pageNum: 2, pageSize: 50, category: 'TASK', isRead: 0 })
    })

    it('uses default pagination when no params', async () => {
      ;(listNotifications as any).mockResolvedValue({ data: { list: [], total: 0 } })
      const store = useNotificationStore()

      await store.fetchList()

      expect(listNotifications).toHaveBeenCalledWith({ pageNum: 1, pageSize: 20 })
    })

    it('filters by isRead status', async () => {
      ;(listNotifications as any).mockResolvedValue({ data: { list: [], total: 0 } })
      const store = useNotificationStore()

      await store.fetchList({ isRead: 0 })

      const callArgs = (listNotifications as any).mock.calls[0][0]
      expect(callArgs.isRead).toBe(0)
    })
  })

  describe('readOne', () => {
    it('marks notification as read and decrements count', async () => {
      ;(markAsRead as any).mockResolvedValue({})
      const store = useNotificationStore()
      store.recentList = [
        { id: 10, title: '未读', isRead: 0, category: 'TASK', content: '', severity: 'INFO', createTime: '', userId: 1 },
      ]
      store.unreadCount = 1

      await store.readOne(10)

      expect(store.recentList[0].isRead).toBe(1)
      expect(store.unreadCount).toBe(0)
    })

    it('does not decrement below zero', async () => {
      ;(markAsRead as any).mockResolvedValue({})
      const store = useNotificationStore()
      store.recentList = [{ id: 10, title: 'x', isRead: 0, category: 'TASK', content: '', severity: 'INFO', createTime: '', userId: 1 }]
      store.unreadCount = 0

      await store.readOne(10)

      expect(store.unreadCount).toBe(0)
    })
  })

  describe('readAll', () => {
    it('marks all as read and resets count', async () => {
      ;(markAllAsRead as any).mockResolvedValue({})
      const store = useNotificationStore()
      store.recentList = [
        { id: 1, title: 'a', isRead: 0, category: 'TASK', content: '', severity: 'INFO', createTime: '', userId: 1 },
        { id: 2, title: 'b', isRead: 0, category: 'TASK', content: '', severity: 'INFO', createTime: '', userId: 1 },
      ]
      store.unreadCount = 3

      await store.readAll()

      expect(store.recentList.every((n) => n.isRead === 1)).toBe(true)
      expect(store.unreadCount).toBe(0)
    })
  })
})
