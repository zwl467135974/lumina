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
      // 兼容两种分页响应：
      // - Lumina PageResult: { list, total, pageNum, pageSize }
      // - MyBatis-Plus Page: { records, total, size, current }
      const data = res.data as any
      tableData.value = data.list || data.records || []
      pagination.total = data.total || 0
    } catch {
      // 拦截器已统一弹错，这里不再重复提示，仅清空数据
      tableData.value = []
      pagination.total = 0
    } finally {
      loading.value = false
    }
  }

  /**
   * 搜索/重置时调用：pageNum 归 1 后加载
   */
  const search = () => {
    pagination.pageNum = 1
    loadData()
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
    search,
    handlePageChange,
    handleSizeChange
  }
}
