/**
 * 自定义指令入口
 */
import type { App } from 'vue'
import permission from './permission'
import role from './role'

export default function setupDirectives(app: App) {
  // 注册权限指令
  app.directive('permission', permission)
  // 注册角色指令
  app.directive('role', role)
}
