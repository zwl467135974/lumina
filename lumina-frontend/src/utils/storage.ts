/**
 * 存储工具
 */
export const storage = {
  get<T = any>(key: string): T | null {
    const value = localStorage.getItem(key)
    if (!value) return null
    try {
      return JSON.parse(value)
    } catch {
      return value as any
    }
  },

  set(key: string, value: any): void {
    const strValue = typeof value === 'string' ? value : JSON.stringify(value)
    localStorage.setItem(key, strValue)
  },

  remove(key: string): void {
    localStorage.removeItem(key)
  },

  clear(): void {
    localStorage.clear()
  }
}

export const session = {
  get<T = any>(key: string): T | null {
    const value = sessionStorage.getItem(key)
    if (!value) return null
    try {
      return JSON.parse(value)
    } catch {
      return value as any
    }
  },

  set(key: string, value: any): void {
    const strValue = typeof value === 'string' ? value : JSON.stringify(value)
    sessionStorage.setItem(key, strValue)
  },

  remove(key: string): void {
    sessionStorage.removeItem(key)
  },

  clear(): void {
    sessionStorage.clear()
  }
}
