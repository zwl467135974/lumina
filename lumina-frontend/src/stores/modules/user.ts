/**
 * 用户状态管理
 */
import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { UserInfo } from '@/types/api'
import { login as loginApi, getUserInfo, logout as logoutApi } from '@/api/modules/user'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem('lumina_token') || '')
  const userInfo = ref<UserInfo | null>(null)

  /**
   * 登录
   */
  const login = async (username: string, password: string) => {
    const res = await loginApi({ username, password })
    token.value = res.data.token
    userInfo.value = res.data.userInfo
    localStorage.setItem('lumina_token', res.data.token)
  }

  /**
   * 获取用户信息
   */
  const getUserInfoAction = async () => {
    const res = await getUserInfo()
    userInfo.value = res.data
  }

  /**
   * 登出
   */
  const logout = async () => {
    try {
      await logoutApi()
    } finally {
      token.value = ''
      userInfo.value = null
      localStorage.removeItem('lumina_token')
    }
  }

  return {
    token,
    userInfo,
    login,
    getUserInfoAction,
    logout
  }
})
