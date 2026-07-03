import { describe, it, expect, beforeEach, vi } from 'vitest'

vi.mock('element-plus', () => ({
  ElMessage: {
    error: vi.fn(),
    success: vi.fn(),
    warning: vi.fn(),
    info: vi.fn()
  }
}))

import http from '../../request'

describe('http wrapper', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('exposes get method', () => {
    expect(typeof http.get).toBe('function')
  })

  it('exposes post method', () => {
    expect(typeof http.post).toBe('function')
  })

  it('exposes put method', () => {
    expect(typeof http.put).toBe('function')
  })

  it('exposes delete method', () => {
    expect(typeof http.delete).toBe('function')
  })

  it('all methods are distinct functions', () => {
    expect(http.get).not.toBe(http.post)
    expect(http.post).not.toBe(http.put)
    expect(http.put).not.toBe(http.delete)
  })
})
