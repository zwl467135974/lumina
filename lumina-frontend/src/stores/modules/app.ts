/**
 * 应用状态管理
 */
import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useAppStore = defineStore('app', () => {
  const sidebarCollapsed = ref<boolean>(false)
  const device = ref<'desktop' | 'mobile'>('desktop')
  const theme = ref<'light' | 'dark'>('light')

  const toggleSidebar = () => {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  const setDevice = (deviceType: 'desktop' | 'mobile') => {
    device.value = deviceType
  }

  const setTheme = (t: 'light' | 'dark') => {
    theme.value = t
    applyTheme(t)
  }

  const toggleTheme = () => {
    setTheme(theme.value === 'light' ? 'dark' : 'light')
  }

  const applyTheme = (t: 'light' | 'dark') => {
    const html = document.documentElement
    if (t === 'dark') {
      html.classList.add('dark')
    } else {
      html.classList.remove('dark')
    }
  }

  const initTheme = () => {
    applyTheme(theme.value)
  }

  return {
    sidebarCollapsed,
    device,
    theme,
    toggleSidebar,
    setDevice,
    setTheme,
    toggleTheme,
    initTheme
  }
}, {
  persist: {
    key: 'lumina-app',
    storage: localStorage,
    paths: ['sidebarCollapsed', 'device', 'theme']
  }
})
