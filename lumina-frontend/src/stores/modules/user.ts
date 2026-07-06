/**
 * 用户状态管理
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { UserInfo } from '@/types/api'
import { login as loginApi, getUserInfo, logout as logoutApi } from '@/api/modules/user'
import { setToken, removeToken } from '@/utils'
import { getUserMenus, type MenuVO } from '@/api/modules/menu'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>('')
  const userInfo = ref<UserInfo | null>(null)
  const menus = ref<MenuVO[]>([])

  /**
   * 是否已登录
   */
  const isLoggedIn = computed(() => !!token.value)

  /**
   * 登录
   */
  const login = async (username: string, password: string) => {
    const res = await loginApi({ username, password })
    token.value = res.data.token
    setToken(res.data.token)
    // 后端 LoginVO 是扁平结构（roles/permissions 在顶层），直接作为 userInfo
    userInfo.value = res.data as any
    await loadMenus()
  }

  /**
   * 加载动态菜单
   */
  const loadMenus = async () => {
    try {
      const res = await getUserMenus()
      menus.value = res.data || []
    } catch {
      menus.value = []
    }
  }

  /**
   * 获取用户信息
   */
  const getUserInfoAction = async () => {
    const res = await getUserInfo()
    userInfo.value = res.data
    await loadMenus()
  }

  /**
   * 登出
   */
  const logout = async () => {
    try {
      await logoutApi()
    } finally {
      token.value = ''
      removeToken()
      userInfo.value = null
      menus.value = []
    }
  }

  return {
    token,
    userInfo,
    menus,
    isLoggedIn,
    login,
    loadMenus,
    getUserInfoAction,
    logout
  }
}, {
  persist: {
    key: 'lumina-user',
    storage: localStorage,
    paths: ['token', 'userInfo', 'menus']
  }
})
