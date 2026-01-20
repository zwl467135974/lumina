/**
 * Pinia Store 入口
 */
import { createPinia } from 'pinia'
import piniaPluginPersistedstate from 'pinia-plugin-persistedstate'

// 创建 Pinia 实例
const pinia = createPinia()

// 添加持久化插件
pinia.use(piniaPluginPersistedstate)

export { useUserStore } from './modules/user'
export { useAppStore } from './modules/app'
export { usePermissionStore } from './modules/permission'
export default pinia
