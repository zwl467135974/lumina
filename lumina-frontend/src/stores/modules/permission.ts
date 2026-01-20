/**
 * 权限状态管理
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { useUserStore } from './user'

export const usePermissionStore = defineStore('permission', () => {
  const permissions = ref<string[]>([])
  const roles = ref<string[]>([])

  /**
   * 是否有某个权限
   */
  const hasPermission = (permission: string | string[]): boolean => {
    const userStore = useUserStore()
    const userPermissions = userStore.userInfo?.permissions || permissions.value

    if (Array.isArray(permission)) {
      return permission.some(p => userPermissions.includes(p))
    }
    return userPermissions.includes(permission)
  }

  /**
   * 是否有某个角色
   */
  const hasRole = (role: string | string[]): boolean => {
    const userStore = useUserStore()
    const userRoles = userStore.userInfo?.roles || roles.value

    if (Array.isArray(role)) {
      return role.some(r => userRoles.includes(r))
    }
    return userRoles.includes(role)
  }

  /**
   * 是否有所有权限
   */
  const hasAllPermissions = (permissionList: string[]): boolean => {
    const userStore = useUserStore()
    const userPermissions = userStore.userInfo?.permissions || permissions.value
    return permissionList.every(p => userPermissions.includes(p))
  }

  /**
   * 是否有所有角色
   */
  const hasAllRoles = (roleList: string[]): boolean => {
    const userStore = useUserStore()
    const userRoles = userStore.userInfo?.roles || roles.value
    return roleList.every(r => userRoles.includes(r))
  }

  /**
   * 设置权限列表
   */
  const setPermissions = (permissionList: string[]) => {
    permissions.value = permissionList
  }

  /**
   * 设置角色列表
   */
  const setRoles = (roleList: string[]) => {
    roles.value = roleList
  }

  return {
    permissions,
    roles,
    hasPermission,
    hasRole,
    hasAllPermissions,
    hasAllRoles,
    setPermissions,
    setRoles
  }
}, {
  persist: {
    key: 'lumina-permission',
    storage: localStorage,
    paths: ['permissions', 'roles']
  }
})
