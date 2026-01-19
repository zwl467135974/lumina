/**
 * 应用状态管理
 */
import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useAppStore = defineStore('app', () => {
  const sidebarCollapsed = ref<boolean>(false)
  const device = ref<'desktop' | 'mobile'>('desktop')

  /**
   * 切换侧边栏
   */
  const toggleSidebar = () => {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  /**
   * 设置设备类型
   */
  const setDevice = (deviceType: 'desktop' | 'mobile') => {
    device.value = deviceType
  }

  return {
    sidebarCollapsed,
    device,
    toggleSidebar,
    setDevice
  }
})
