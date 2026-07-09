<template>
  <el-card class="lum-table-panel" shadow="never">
    <!-- 搜索区 -->
    <div v-if="searchFields?.length" class="lum-table-panel__search">
      <el-form :model="searchModel" inline>
        <el-form-item
          v-for="field in searchFields"
          :key="field.prop"
          :label="field.label"
        >
          <el-input
            v-if="field.type === 'input'"
            v-model="searchModel[field.prop]"
            :placeholder="field.placeholder"
            clearable
            @keyup.enter="$emit('search')"
          />
          <el-select
            v-else-if="field.type === 'select'"
            v-model="searchModel[field.prop]"
            :placeholder="field.placeholder"
            clearable
            style="width: 160px"
          >
            <el-option
              v-for="opt in field.options"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="$emit('search')">
            <el-icon><Search /></el-icon>
            {{ t('common.search') }}
          </el-button>
          <el-button @click="$emit('reset')">{{ t('common.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 工具栏 -->
    <div v-if="$slots['toolbar-left'] || $slots['toolbar-right']" class="lum-table-panel__toolbar">
      <div class="lum-table-panel__toolbar-left">
        <slot name="toolbar-left" />
      </div>
      <div class="lum-table-panel__toolbar-right">
        <slot name="toolbar-right" />
      </div>
    </div>

    <!-- 表格 -->
    <el-table
      :data="data"
      v-loading="loading"
      stripe
      v-bind="$attrs"
    >
      <slot />
    </el-table>

    <!-- 分页 -->
    <div v-if="pagination" class="lum-table-panel__pagination">
      <el-pagination
        v-model:current-page="pagination.pageNum"
        v-model:page-size="pagination.pageSize"
        :total="pagination.total"
        :page-sizes="pageSizes"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @size-change="$emit('size-change', $event)"
        @current-change="$emit('page-change', $event)"
      />
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { Search } from '@element-plus/icons-vue'

const { t } = useI18n()

export interface SearchFieldOption {
  label: string
  value: string | number
}

export interface SearchField {
  prop: string
  label: string
  type: 'input' | 'select'
  placeholder?: string
  options?: SearchFieldOption[]
}

export interface Pagination {
  pageNum: number
  pageSize: number
  total: number
}

interface Props {
  data: readonly any[]
  loading?: boolean
  searchModel?: Record<string, any>
  searchFields?: SearchField[]
  pagination?: Pagination
  pageSizes?: number[]
}

withDefaults(defineProps<Props>(), {
  loading: false,
  searchModel: () => ({}),
  pageSizes: () => [10, 20, 50, 100]
})

defineEmits<{
  search: []
  reset: []
  'page-change': [page: number]
  'size-change': [size: number]
}>()

defineOptions({ inheritAttrs: false })
</script>

<style scoped>
.lum-table-panel {
  border-radius: var(--lumina-radius-lg);
}

.lum-table-panel__search {
  margin-bottom: var(--lumina-spacing-md);
}

.lum-table-panel__search :deep(.el-form--inline .el-form-item) {
  margin-bottom: var(--lumina-spacing-sm);
}

.lum-table-panel__toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--lumina-spacing-md);
  gap: var(--lumina-spacing-sm);
}

.lum-table-panel__toolbar-left,
.lum-table-panel__toolbar-right {
  display: flex;
  align-items: center;
  gap: var(--lumina-spacing-sm);
}

.lum-table-panel__pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--lumina-spacing-md);
}
</style>
