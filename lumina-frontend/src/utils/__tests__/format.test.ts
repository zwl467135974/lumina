import { describe, it, expect } from 'vitest'
import { formatDate, formatFileSize, formatNumber } from '../format'

describe('formatDate', () => {
  it('formats ISO string with default format', () => {
    const result = formatDate('2025-01-15T10:30:00')
    expect(result).toBe('2025-01-15 10:30:00')
  })

  it('formats with custom format', () => {
    const result = formatDate('2025-01-15T10:30:00', 'YYYY/MM/DD')
    expect(result).toBe('2025/01/15')
  })

  it('formats Date object', () => {
    const date = new Date(2025, 0, 15, 10, 30, 0)
    const result = formatDate(date, 'YYYY-MM-DD')
    expect(result).toBe('2025-01-15')
  })
})

describe('formatFileSize', () => {
  it('returns 0 B for zero', () => {
    expect(formatFileSize(0)).toBe('0 B')
  })

  it('formats bytes', () => {
    expect(formatFileSize(500)).toBe('500 B')
  })

  it('formats kilobytes', () => {
    expect(formatFileSize(1024)).toBe('1 KB')
  })

  it('formats megabytes', () => {
    expect(formatFileSize(1048576)).toBe('1 MB')
  })

  it('formats gigabytes', () => {
    expect(formatFileSize(1073741824)).toBe('1 GB')
  })

  it('formats fractional sizes', () => {
    expect(formatFileSize(1536)).toBe('1.5 KB')
  })
})

describe('formatNumber', () => {
  it('formats thousands separator', () => {
    expect(formatNumber(1234567)).toBe('1,234,567')
  })

  it('does not add separator for small numbers', () => {
    expect(formatNumber(999)).toBe('999')
  })

  it('formats exact thousands', () => {
    expect(formatNumber(1000)).toBe('1,000')
  })

  it('handles zero', () => {
    expect(formatNumber(0)).toBe('0')
  })
})
