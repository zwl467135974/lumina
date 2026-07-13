/**
 * 路由守卫测试
 */
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { setupRouterGuards } from '../guards'
import { useUserStore, usePermissionStore } from '@/stores'

describe('router guards', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  /** 构造一个最小的 mock router，收集 beforeEach 注册的 guard */
  function createMockRouter() {
    let guard: ((to: any, from: any, next: (v?: any) => void) => void) | null = null
    return {
      beforeEach(fn: any) { guard = fn },
      afterEach() {},
      onError() {},
      runGuard(to: any, next: (v?: any) => void) {
        guard!(to, null, next)
      },
    }
  }

  function mockRoute(path: string, meta: Record<string, any> = {}) {
    return { path, fullPath: path, meta }
  }

  it('redirects to login when not authenticated', () => {
    const router = createMockRouter()
    setupRouterGuards(router as any)

    // 未登录
    const userStore = useUserStore()
    userStore.token = ''
    userStore.userInfo = null

    const next = vi.fn()
    router.runGuard(mockRoute('/dashboard', { requiresAuth: true }), next)

    expect(next).toHaveBeenCalledWith(expect.objectContaining({ path: '/login' }))
  })

  it('allows access when authenticated and no permission required', () => {
    const router = createMockRouter()
    setupRouterGuards(router as any)

    const userStore = useUserStore()
    userStore.token = 'fake-token'
    userStore.userInfo = { userId: 1, username: 'admin', permissions: [] }

    const next = vi.fn()
    router.runGuard(mockRoute('/dashboard', { requiresAuth: true }), next)

    expect(next).toHaveBeenCalledWith()
  })

  it('allows access when route does not require auth', () => {
    const router = createMockRouter()
    setupRouterGuards(router as any)

    const next = vi.fn()
    router.runGuard(mockRoute('/login', { requiresAuth: false }), next)

    expect(next).toHaveBeenCalledWith()
  })

  it('redirects to 404 when user lacks required permissions', () => {
    const router = createMockRouter()
    setupRouterGuards(router as any)

    const userStore = useUserStore()
    userStore.token = 'fake-token'
    userStore.userInfo = { userId: 1, username: 'admin', permissions: ['user:read'] }

    const next = vi.fn()
    router.runGuard(
      mockRoute('/admin', { requiresAuth: true, permissions: ['admin:all'] }),
      next
    )

    expect(next).toHaveBeenCalledWith('/404')
  })

  it('allows access when user has required permissions', () => {
    const router = createMockRouter()
    setupRouterGuards(router as any)

    const userStore = useUserStore()
    userStore.token = 'fake-token'
    userStore.userInfo = { userId: 1, username: 'admin', permissions: ['admin:all'] }

    const next = vi.fn()
    router.runGuard(
      mockRoute('/admin', { requiresAuth: true, permissions: ['admin:all'] }),
      next
    )

    expect(next).toHaveBeenCalledWith()
  })

  it('preserves redirect path in query when redirecting to login', () => {
    const router = createMockRouter()
    setupRouterGuards(router as any)

    const userStore = useUserStore()
    userStore.token = ''
    userStore.userInfo = null

    const next = vi.fn()
    router.runGuard(mockRoute('/agent/list', { requiresAuth: true }), next)

    expect(next).toHaveBeenCalledWith(
      expect.objectContaining({
        path: '/login',
        query: expect.objectContaining({ redirect: '/agent/list' }),
      })
    )
  })
})
