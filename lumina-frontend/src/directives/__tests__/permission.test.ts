/**
 * v-permission 指令测试
 */
import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useUserStore } from '@/stores'
import permission from '../permission'

describe('v-permission directive', () => {
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

  it('keeps element when user has the permission (string)', () => {
    const userStore = useUserStore()
    userStore.userInfo = { userId: 1, username: 'admin', permissions: ['user:create'] }

    const el = createEl()
    ;(permission as any).mounted(el, mockBinding('user:create'))

    expect(el.parentNode).not.toBeNull()
  })

  it('removes element when user lacks the permission', () => {
    const userStore = useUserStore()
    userStore.userInfo = { userId: 1, username: 'admin', permissions: ['user:read'] }

    const el = createEl()
    ;(permission as any).mounted(el, mockBinding('user:delete'))

    expect(el.parentNode).toBeNull()
  })

  it('keeps element when user has any of the required permissions (array)', () => {
    const userStore = useUserStore()
    userStore.userInfo = { userId: 1, username: 'admin', permissions: ['user:update'] }

    const el = createEl()
    ;(permission as any).mounted(el, mockBinding(['user:create', 'user:update']))

    expect(el.parentNode).not.toBeNull()
  })

  it('removes element when user has none of the required permissions (array)', () => {
    const userStore = useUserStore()
    userStore.userInfo = { userId: 1, username: 'admin', permissions: ['user:read'] }

    const el = createEl()
    ;(permission as any).mounted(el, mockBinding(['user:create', 'user:update']))

    expect(el.parentNode).toBeNull()
  })

  it('throws when no permission value provided', () => {
    const userStore = useUserStore()
    userStore.userInfo = { userId: 1, username: 'admin', permissions: ['user:read'] }

    const el = createEl()
    expect(() => (permission as any).mounted(el, mockBinding(''))).toThrow()
  })
})
