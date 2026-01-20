/**
 * 角色指令 v-role
 * 用法: v-role="'admin'" 或 v-role="['admin', 'manager']"
 */
import type { Directive, DirectiveBinding } from 'vue'
import { usePermissionStore } from '@/stores'

const role: Directive = {
  mounted(el: HTMLElement, binding: DirectiveBinding) {
    const { value } = binding
    const permissionStore = usePermissionStore()

    if (value) {
      const hasRole = permissionStore.hasRole(value)

      if (!hasRole) {
        // 移除元素
        el.parentNode?.removeChild(el)
      }
    } else {
      throw new Error('角色指令需要指定角色值，如 v-role="\'admin\'"')
    }
  }
}

export default role
