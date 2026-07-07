import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

vi.mock('@/api/modules/user', () => ({
  login: vi.fn(),
  getUserInfo: vi.fn(),
  logout: vi.fn()
}))

vi.mock('@/api/modules/menu', () => ({
  getUserMenus: vi.fn()
}))

import { login as loginApi, getUserInfo as getUserInfoApi, logout as logoutApi } from '@/api/modules/user'
import { getUserMenus } from '@/api/modules/menu'
import { useUserStore } from '../user'

describe('user store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    localStorage.clear()
  })

  describe('isLoggedIn', () => {
    it('returns false when no token', () => {
      const store = useUserStore()
      expect(store.isLoggedIn).toBe(false)
    })

    it('returns true when token is set', () => {
      const store = useUserStore()
      store.token = 'my-token'
      expect(store.isLoggedIn).toBe(true)
    })
  })

  describe('login', () => {
    it('sets token and userInfo on successful login', async () => {
      const mockLoginData = {
        token: 'jwt-token-123',
        userId: 1,
        username: 'admin',
        roles: ['SUPER_ADMIN'],
        permissions: ['*']
      }
      vi.mocked(loginApi).mockResolvedValue({
        code: 200, msg: 'ok', data: mockLoginData, timestamp: Date.now()
      })
      vi.mocked(getUserMenus).mockResolvedValue({
        code: 200, msg: 'ok', data: [], timestamp: Date.now()
      })

      const store = useUserStore()
      await store.login('admin', 'password')

      expect(store.token).toBe('jwt-token-123')
      expect(store.userInfo).toEqual(mockLoginData)
      expect(loginApi).toHaveBeenCalledWith({ username: 'admin', password: 'password' })
    })

    it('loads menus after login', async () => {
      vi.mocked(loginApi).mockResolvedValue({
        code: 200, msg: 'ok',
        data: { token: 'tok', userId: 1, username: 'admin', roles: [], permissions: [] },
        timestamp: Date.now()
      })
      const mockMenus = [{ name: 'dashboard', path: '/dashboard', title: 'Dashboard' }]
      vi.mocked(getUserMenus).mockResolvedValue({
        code: 200, msg: 'ok', data: mockMenus, timestamp: Date.now()
      })

      const store = useUserStore()
      await store.login('admin', 'password')

      expect(store.menus).toEqual(mockMenus)
      expect(getUserMenus).toHaveBeenCalled()
    })
  })

  describe('logout', () => {
    it('clears token, userInfo, and menus', async () => {
      vi.mocked(logoutApi).mockResolvedValue({} as any)

      const store = useUserStore()
      store.token = 'temp-token'
      store.userInfo = { userId: 1, username: 'admin' }
      store.menus = [{ name: 'a', path: '/a', title: 'A' }]

      await store.logout()

      expect(store.token).toBe('')
      expect(store.userInfo).toBeNull()
      expect(store.menus).toEqual([])
    })

    it('clears state even if logoutApi fails', async () => {
      vi.mocked(logoutApi).mockRejectedValue(new Error('network error'))

      const store = useUserStore()
      store.token = 'temp-token'

      // logout uses try/finally, so error propagates but state is still cleared
      await expect(store.logout()).rejects.toThrow('network error')

      expect(store.token).toBe('')
    })
  })

  describe('loadMenus', () => {
    it('sets menus from API', async () => {
      const mockMenus = [{ name: 'home', path: '/home', title: 'Home' }]
      vi.mocked(getUserMenus).mockResolvedValue({
        code: 200, msg: 'ok', data: mockMenus, timestamp: Date.now()
      })

      const store = useUserStore()
      await store.loadMenus()

      expect(store.menus).toEqual(mockMenus)
    })

    it('sets empty array on error', async () => {
      vi.mocked(getUserMenus).mockRejectedValue(new Error('fail'))

      const store = useUserStore()
      await store.loadMenus()

      expect(store.menus).toEqual([])
    })
  })

  describe('getUserInfoAction', () => {
    it('fetches and stores user info', async () => {
      const mockUserInfo = { userId: 1, username: 'admin', roles: ['ADMIN'] }
      vi.mocked(getUserInfoApi).mockResolvedValue({
        code: 200, msg: 'ok', data: mockUserInfo, timestamp: Date.now()
      })
      vi.mocked(getUserMenus).mockResolvedValue({
        code: 200, msg: 'ok', data: [], timestamp: Date.now()
      })

      const store = useUserStore()
      await store.getUserInfoAction()

      expect(store.userInfo).toEqual(mockUserInfo)
    })
  })
})
