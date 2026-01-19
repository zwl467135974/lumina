/**
 * 表格组合式函数
 */
import { ref, reactive } from 'vue'
import type { PageResult } from '@/types/api'

export function useTable<T>(
  fetchFn: (params: any) => Promise<{ data: PageResult<T> }>
) {
  const loading = ref(false)
  const tableData = ref<T[]>([])
  const pagination = reactive({
    pageNum: 1,
    pageSize: 10,
    total: 0
  })

  /**
   * 加载数据
   */
  const loadData = async () => {
    loading.value = true
    try {
      const res = await fetchFn({
        pageNum: pagination.pageNum,
        pageSize: pagination.pageSize
      })
      tableData.value = res.data.list
      pagination.total = res.data.total
    } finally {
      loading.value = false
    }
  }

  /**
   * 页码变化
   */
  const handlePageChange = (page: number) => {
    pagination.pageNum = page
    loadData()
  }

  /**
   * 每页数量变化
   */
  const handleSizeChange = (size: number) => {
    pagination.pageSize = size
    pagination.pageNum = 1
    loadData()
  }

  return {
    loading,
    tableData,
    pagination,
    loadData,
    handlePageChange,
    handleSizeChange
  }
}
