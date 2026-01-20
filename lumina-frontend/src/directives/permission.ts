/**
 * 权限指令 v-permission
 * 用法: v-permission="'user:create'" 或 v-permission="['user:create', 'user:update']"
 */
import type { Directive, DirectiveBinding } from 'vue'
import { usePermissionStore } from '@/stores'

const permission: Directive = {
  mounted(el: HTMLElement, binding: DirectiveBinding) {
    const { value } = binding
    const permissionStore = usePermissionStore()

    if (value) {
      const hasPermission = permissionStore.hasPermission(value)

      if (!hasPermission) {
        // 移除元素
        el.parentNode?.removeChild(el)
      }
    } else {
      throw new Error('权限指令需要指定权限值，如 v-permission="\'user:create\'"')
    }
  }
}

export default permission
