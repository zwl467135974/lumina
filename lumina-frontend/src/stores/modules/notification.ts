/**
 * 通知中心 Store
 */
import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  listNotifications,
  getUnreadCount,
  markAsRead,
  markAllAsRead,
  streamNotifications,
  type NotificationVO,
  type NotificationQuery,
} from '@/api/modules/notification'

export const useNotificationStore = defineStore('notification', () => {
  const unreadCount = ref(0)
  const recentList = ref<NotificationVO[]>([])
  const loading = ref(false)
  let sseController: AbortController | null = null

  /** 拉取未读计数 */
  async function fetchUnreadCount() {
    try {
      const res = await getUnreadCount()
      unreadCount.value = res.data?.count || 0
    } catch {
      // 静默失败
    }
  }

  /** 拉取最近通知列表 */
  async function fetchList(params?: NotificationQuery) {
    loading.value = true
    try {
      const res = await listNotifications({ pageNum: 1, pageSize: 20, ...params })
      recentList.value = res.data?.list || []
    } catch {
      recentList.value = []
    } finally {
      loading.value = false
    }
  }

  /** 标记单条已读 */
  async function readOne(id: number) {
    await markAsRead(id)
    const item = recentList.value.find((n) => n.id === id)
    if (item && item.isRead === 0) {
      item.isRead = 1
      unreadCount.value = Math.max(0, unreadCount.value - 1)
    }
  }

  /** 全部已读 */
  async function readAll() {
    await markAllAsRead()
    recentList.value.forEach((n) => (n.isRead = 1))
    unreadCount.value = 0
  }

  /** 连接 SSE 实时推送 */
  function connectSSE() {
    disconnectSSE()
    sseController = streamNotifications({
      onMessage: (notification) => {
        // 新通知到达，更新列表和计数
        recentList.value.unshift(notification)
        if (recentList.value.length > 20) {
          recentList.value.pop()
        }
        if (notification.isRead === 0) {
          unreadCount.value++
        }
      },
      onError: () => {
        // SSE 断开，5 秒后重连
        setTimeout(() => connectSSE(), 5000)
      },
    })
  }

  /** 断开 SSE */
  function disconnectSSE() {
    if (sseController) {
      sseController.abort()
      sseController = null
    }
  }

  /** 初始化（登录后调用） */
  async function init() {
    await Promise.all([fetchUnreadCount(), fetchList()])
    connectSSE()
  }

  return {
    unreadCount,
    recentList,
    loading,
    fetchUnreadCount,
    fetchList,
    readOne,
    readAll,
    connectSSE,
    disconnectSSE,
    init,
  }
})
