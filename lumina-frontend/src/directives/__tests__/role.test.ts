/**
 * v-role 指令测试
 */
import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useUserStore } from '@/stores'
import role from '../role'

describe('v-role directive', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  function createEl(): HTMLElement {
    const parent = document.createElement('div')
    const el = document.createElement('div')
    parent.appendChild(el)
    return el
  }

  function mockBinding(value: string | string[]) {
    return { value } as any
  }

  it('keeps element when user has the role (string)', () => {
    const userStore = useUserStore()
    userStore.userInfo = { userId: 1, username: 'admin', roles: ['SUPER_ADMIN'] }

    const el = createEl()
    ;(role as any).mounted(el, mockBinding('SUPER_ADMIN'))

    expect(el.parentNode).not.toBeNull()
  })

  it('removes element when user lacks the role', () => {
    const userStore = useUserStore()
    userStore.userInfo = { userId: 1, username: 'admin', roles: ['TENANT_USER'] }

    const el = createEl()
    ;(role as any).mounted(el, mockBinding('SUPER_ADMIN'))

    expect(el.parentNode).toBeNull()
  })

  it('keeps element when user has any of the required roles (array)', () => {
    const userStore = useUserStore()
    userStore.userInfo = { userId: 1, username: 'admin', roles: ['TENANT_ADMIN'] }

    const el = createEl()
    ;(role as any).mounted(el, mockBinding(['SUPER_ADMIN', 'TENANT_ADMIN']))

    expect(el.parentNode).not.toBeNull()
  })

  it('removes element when user has none of the required roles (array)', () => {
    const userStore = useUserStore()
    userStore.userInfo = { userId: 1, username: 'admin', roles: ['TENANT_USER'] }

    const el = createEl()
    ;(role as any).mounted(el, mockBinding(['SUPER_ADMIN', 'TENANT_ADMIN']))

    expect(el.parentNode).toBeNull()
  })

  it('throws when no role value provided', () => {
    const userStore = useUserStore()
    userStore.userInfo = { userId: 1, username: 'admin', roles: ['TENANT_USER'] }

    const el = createEl()
    expect(() => (role as any).mounted(el, mockBinding(''))).toThrow()
  })
})
