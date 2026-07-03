import { describe, it, expect, beforeEach } from 'vitest'
import { getToken, setToken, removeToken } from '../auth'

describe('auth utils', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('setToken stores token in localStorage', () => {
    setToken('my-jwt-token')
    expect(localStorage.getItem('lumina_token')).toBe('my-jwt-token')
  })

  it('getToken returns stored token', () => {
    setToken('abc123')
    expect(getToken()).toBe('abc123')
  })

  it('getToken returns null when no token', () => {
    expect(getToken()).toBeNull()
  })

  it('removeToken clears token', () => {
    setToken('temp-token')
    removeToken()
    expect(getToken()).toBeNull()
  })
})
