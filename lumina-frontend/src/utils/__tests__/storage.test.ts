import { describe, it, expect, beforeEach } from 'vitest'
import { storage, session } from '../storage'

describe('storage utils', () => {
  beforeEach(() => {
    localStorage.clear()
    sessionStorage.clear()
  })

  it('set/get stores and retrieves object', () => {
    storage.set('key', { name: 'test', count: 42 })
    const result = storage.get<{ name: string; count: number }>('key')
    expect(result).toEqual({ name: 'test', count: 42 })
  })

  it('set stores string as-is', () => {
    storage.set('key', 'plain-string')
    expect(localStorage.getItem('key')).toBe('plain-string')
  })

  it('get returns null for missing key', () => {
    expect(storage.get('nonexistent')).toBeNull()
  })

  it('get returns raw string for invalid JSON', () => {
    localStorage.setItem('bad-json', '{invalid}')
    expect(storage.get('bad-json')).toBe('{invalid}')
  })

  it('remove deletes key', () => {
    storage.set('key', 'value')
    storage.remove('key')
    expect(storage.get('key')).toBeNull()
  })

  it('clear removes all keys', () => {
    storage.set('a', 1)
    storage.set('b', 2)
    storage.clear()
    expect(storage.get('a')).toBeNull()
    expect(storage.get('b')).toBeNull()
  })
})

describe('session utils', () => {
  beforeEach(() => {
    sessionStorage.clear()
  })

  it('set/get stores and retrieves in sessionStorage', () => {
    session.set('key', { id: 1 })
    expect(session.get<{ id: number }>('key')).toEqual({ id: 1 })
  })

  it('remove deletes from sessionStorage', () => {
    session.set('key', 'val')
    session.remove('key')
    expect(session.get('key')).toBeNull()
  })
})
