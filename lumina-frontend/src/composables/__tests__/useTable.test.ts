import { describe, it, expect, vi } from 'vitest'
import { useTable } from '../useTable'
import type { PageResult } from '@/types/api'

interface TestItem { id: number; name: string }

function makeFetchFn(items: TestItem[], total: number) {
  return vi.fn().mockResolvedValue({
    data: {
      list: items,
      total,
      pageNum: 1,
      pageSize: 10,
      pages: Math.ceil(total / 10)
    } as PageResult<TestItem>
  })
}

describe('useTable', () => {
  it('initializes with default pagination', () => {
    const { pagination, tableData, loading } = useTable<TestItem>(makeFetchFn([], 0))
    expect(pagination.pageNum).toBe(1)
    expect(pagination.pageSize).toBe(10)
    expect(pagination.total).toBe(0)
    expect(tableData.value).toEqual([])
    expect(loading.value).toBe(false)
  })

  it('loadData populates tableData and total', async () => {
    const items = [{ id: 1, name: 'A' }, { id: 2, name: 'B' }]
    const fetchFn = makeFetchFn(items, 2)
    const { loadData, tableData, pagination } = useTable<TestItem>(fetchFn)

    await loadData()

    expect(fetchFn).toHaveBeenCalledWith({ pageNum: 1, pageSize: 10 })
    expect(tableData.value).toEqual(items)
    expect(pagination.total).toBe(2)
  })

  it('handlePageChange updates pageNum and reloads', async () => {
    const fetchFn = makeFetchFn([], 50)
    const { handlePageChange, pagination } = useTable<TestItem>(fetchFn)

    await handlePageChange(3)

    expect(pagination.pageNum).toBe(3)
    expect(fetchFn).toHaveBeenCalledWith({ pageNum: 3, pageSize: 10 })
  })

  it('handleSizeChange resets to page 1', async () => {
    const fetchFn = makeFetchFn([], 50)
    const { handleSizeChange, pagination } = useTable<TestItem>(fetchFn)

    pagination.pageNum = 5
    await handleSizeChange(20)

    expect(pagination.pageSize).toBe(20)
    expect(pagination.pageNum).toBe(1)
    expect(fetchFn).toHaveBeenCalledWith({ pageNum: 1, pageSize: 20 })
  })

  it('loadData handles error gracefully', async () => {
    const fetchFn = vi.fn().mockRejectedValue(new Error('network error'))
    const { loadData, tableData, pagination, loading } = useTable<TestItem>(fetchFn)

    await loadData()

    expect(tableData.value).toEqual([])
    expect(pagination.total).toBe(0)
    expect(loading.value).toBe(false)
  })
})
