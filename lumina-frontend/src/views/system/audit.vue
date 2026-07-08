<template>
  <div class="audit-page">
    <PageHeader title="审计日志" description="用户操作审计记录与查询" />

    <el-card shadow="never">
      <el-form :inline="true" class="filter-form">
        <el-form-item label="模块">
          <el-select v-model="filterModule" :placeholder="t('common.all')" clearable style="width: 140px" @change="loadData">
            <el-option label="用户管理" value="user" />
            <el-option label="角色管理" value="role" />
            <el-option label="权限管理" value="permission" />
            <el-option label="租户管理" value="tenant" />
            <el-option label="Agent" value="agent" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('common.actions')">
          <el-input v-model="filterAction" placeholder="如 create/update/delete" clearable style="width: 160px" @change="loadData" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadData">{{ t('common.query') }}</el-button>
          <el-button @click="resetFilter">{{ t('common.reset') }}</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="logs" stripe>
        <el-table-column prop="auditId" label="ID" width="70" />
        <el-table-column prop="username" label="操作人" width="100" />
        <el-table-column prop="module" label="模块" width="100" />
        <el-table-column prop="action" :label="t('common.actions')" width="100" />
        <el-table-column prop="description" :label="t('common.description')" min-width="200" show-overflow-tooltip />
        <el-table-column prop="requestMethod" label="方法" width="70" />
        <el-table-column :label="t('common.status')" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="耗时" width="80">
          <template #default="{ row }">{{ row.durationMs ? row.durationMs + 'ms' : '-' }}</template>
        </el-table-column>
        <el-table-column prop="requestIp" label="IP" width="120" />
        <el-table-column prop="createTime" label="时间" width="170" />
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import PageHeader from '@/components/common/PageHeader.vue'
import { listAuditLogs, type AuditLogVO } from '@/api/modules/audit-log'

const { t } = useI18n()

const loading = ref(false)
const logs = ref<AuditLogVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const filterModule = ref('')
const filterAction = ref('')

const loadData = async () => {
  loading.value = true
  try {
    const res = await listAuditLogs({
      module: filterModule.value || undefined,
      action: filterAction.value || undefined,
      pageNum: pageNum.value,
      pageSize: pageSize.value
    })
    logs.value = res.data.list || []
    total.value = res.data.total || 0
  } finally {
    loading.value = false
  }
}

const resetFilter = () => {
  filterModule.value = ''
  filterAction.value = ''
  pageNum.value = 1
  loadData()
}

onMounted(loadData)
</script>

<style scoped>
.filter-form { margin-bottom: 16px; }
.pagination-wrapper { margin-top: 16px; display: flex; justify-content: flex-end; }

@media (max-width: 768px) {
  :deep(.el-col) {
    max-width: 100%;
    flex: 0 0 100%;
  }
  :deep(.el-form--inline .el-form-item) {
    display: block;
    margin-right: 0;
  }
  :deep(.el-table) {
    font-size: 12px;
  }
}
</style>
