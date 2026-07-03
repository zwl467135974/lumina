import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { usePermissionStore } from '../permission'
import { useUserStore } from '../user'

describe('permission store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  describe('hasPermission', () => {
    it('returns true when user has the permission', () => {
      const userStore = useUserStore()
      userStore.userInfo = {
        userId: 1, username: 'admin', permissions: ['user:read', 'user:write']
      }
      const permStore = usePermissionStore()

      expect(permStore.hasPermission('user:read')).toBe(true)
    })

    it('returns false when user lacks the permission', () => {
      const userStore = useUserStore()
      userStore.userInfo = {
        userId: 1, username: 'admin', permissions: ['user:read']
      }
      const permStore = usePermissionStore()

      expect(permStore.hasPermission('user:delete')).toBe(false)
    })

    it('returns true if any permission in array matches', () => {
      const userStore = useUserStore()
      userStore.userInfo = {
        userId: 1, username: 'admin', permissions: ['user:read']
      }
      const permStore = usePermissionStore()

      expect(permStore.hasPermission(['user:read', 'user:delete'])).toBe(true)
    })

    it('returns false if no permission in array matches', () => {
      const userStore = useUserStore()
      userStore.userInfo = {
        userId: 1, username: 'admin', permissions: ['user:read']
      }
      const permStore = usePermissionStore()

      expect(permStore.hasPermission(['user:delete', 'user:export'])).toBe(false)
    })

    it('falls back to store permissions when userInfo is null', () => {
      const permStore = usePermissionStore()
      permStore.setPermissions(['agent:read'])

      expect(permStore.hasPermission('agent:read')).toBe(true)
      expect(permStore.hasPermission('agent:delete')).toBe(false)
    })

    it('returns false when no permissions at all', () => {
      const permStore = usePermissionStore()
      expect(permStore.hasPermission('anything')).toBe(false)
    })
  })

  describe('hasRole', () => {
    it('returns true when user has the role', () => {
      const userStore = useUserStore()
      userStore.userInfo = {
        userId: 1, username: 'admin', roles: ['ADMIN', 'USER']
      }
      const permStore = usePermissionStore()

      expect(permStore.hasRole('ADMIN')).toBe(true)
    })

    it('returns false when user lacks the role', () => {
      const userStore = useUserStore()
      userStore.userInfo = {
        userId: 1, username: 'admin', roles: ['USER']
      }
      const permStore = usePermissionStore()

      expect(permStore.hasRole('ADMIN')).toBe(false)
    })

    it('returns true if any role in array matches', () => {
      const userStore = useUserStore()
      userStore.userInfo = {
        userId: 1, username: 'admin', roles: ['USER']
      }
      const permStore = usePermissionStore()

      expect(permStore.hasRole(['ADMIN', 'USER'])).toBe(true)
    })
  })

  describe('hasAllPermissions', () => {
    it('returns true when user has all permissions', () => {
      const userStore = useUserStore()
      userStore.userInfo = {
        userId: 1, username: 'admin', permissions: ['user:read', 'user:write', 'user:delete']
      }
      const permStore = usePermissionStore()

      expect(permStore.hasAllPermissions(['user:read', 'user:write'])).toBe(true)
    })

    it('returns false when user is missing one', () => {
      const userStore = useUserStore()
      userStore.userInfo = {
        userId: 1, username: 'admin', permissions: ['user:read']
      }
      const permStore = usePermissionStore()

      expect(permStore.hasAllPermissions(['user:read', 'user:write'])).toBe(false)
    })
  })

  describe('hasAllRoles', () => {
    it('returns true when user has all roles', () => {
      const userStore = useUserStore()
      userStore.userInfo = {
        userId: 1, username: 'admin', roles: ['ADMIN', 'USER', 'GUEST']
      }
      const permStore = usePermissionStore()

      expect(permStore.hasAllRoles(['ADMIN', 'USER'])).toBe(true)
    })

    it('returns false when missing one role', () => {
      const userStore = useUserStore()
      userStore.userInfo = {
        userId: 1, username: 'admin', roles: ['USER']
      }
      const permStore = usePermissionStore()

      expect(permStore.hasAllRoles(['ADMIN', 'USER'])).toBe(false)
    })
  })

  describe('setPermissions / setRoles', () => {
    it('sets permissions directly', () => {
      const permStore = usePermissionStore()
      permStore.setPermissions(['perm1', 'perm2'])

      expect(permStore.permissions).toEqual(['perm1', 'perm2'])
    })

    it('sets roles directly', () => {
      const permStore = usePermissionStore()
      permStore.setRoles(['role1'])

      expect(permStore.roles).toEqual(['role1'])
    })
  })
})
