<template>
  <div class="task-page">
    <PageHeader :title="t('task.title')" :description="t('task.description')" />

    <LumTablePanel
      :search-model="queryForm"
      :data="tasks"
      :loading="loading"
      :pagination="pagination"
      :search-fields="searchFields"
      :page-sizes="[10, 20, 50]"
      @search="loadTasks"
      @reset="resetFilter"
      @page-change="onPageChange"
      @size-change="onSizeChange"
    >
      <template #toolbar-right>
        <el-button v-if="autoRefresh" type="success" plain @click="stopAutoRefresh">{{ t('task.stopRefresh') }}</el-button>
        <el-button v-else type="warning" plain @click="startAutoRefresh">{{ t('task.autoRefresh') }}</el-button>
      </template>

      <el-table-column prop="taskUuid" :label="t('task.taskUuid')" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="uuid-text">{{ row.taskUuid.substring(0, 8) }}...</span>
          </template>
        </el-table-column>
        <el-table-column prop="agentId" :label="t('task.agentId')" width="80" />
        <el-table-column prop="inputText" :label="t('task.input')" min-width="200" show-overflow-tooltip />
        <el-table-column :label="t('task.status')" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="totalTokens" :label="t('task.tokenUsage')" width="90">
          <template #default="{ row }">{{ row.totalTokens || '-' }}</template>
        </el-table-column>
        <el-table-column :label="t('task.duration')" width="90">
          <template #default="{ row }">{{ row.durationMs ? (row.durationMs / 1000).toFixed(1) + 's' : '-' }}</template>
        </el-table-column>
        <el-table-column prop="createTime" :label="t('task.createTime')" width="170" />
        <el-table-column :label="t('common.actions')" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="viewDetail(row)">{{ t('common.detail') }}</el-button>
            <el-button v-if="row.status === 'QUEUED' || row.status === 'RUNNING'" link type="danger" @click="handleCancel(row.taskUuid)">{{ t('common.cancel') }}</el-button>
          </template>
        </el-table-column>
    </LumTablePanel>

    <el-dialog v-model="detailVisible" :title="t('task.detail')" width="700px">
      <el-descriptions v-if="detailTask" :column="2" border>
        <el-descriptions-item :label="t('task.taskUuid')" :span="2">{{ detailTask.taskUuid }}</el-descriptions-item>
        <el-descriptions-item label="Agent ID">{{ detailTask.agentId }}</el-descriptions-item>
        <el-descriptions-item :label="t('task.status')">
          <el-tag :type="statusType(detailTask.status)" size="small">{{ statusLabel(detailTask.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item :label="t('task.input')" :span="2">{{ detailTask.inputText }}</el-descriptions-item>
        <el-descriptions-item :label="t('task.result')" :span="2">
          <div class="task-result">{{ detailTask.result || '-' }}</div>
        </el-descriptions-item>
        <el-descriptions-item v-if="detailTask.errorMessage" :label="t('task.result')" :span="2">
          <span class="error-text">{{ detailTask.errorMessage }}</span>
        </el-descriptions-item>
        <el-descriptions-item :label="t('cost.inputTokens')">{{ detailTask.promptTokens || 0 }}</el-descriptions-item>
        <el-descriptions-item :label="t('cost.outputTokens')">{{ detailTask.completionTokens || 0 }}</el-descriptions-item>
        <el-descriptions-item :label="t('cost.totalTokens')">{{ detailTask.totalTokens || 0 }}</el-descriptions-item>
        <el-descriptions-item :label="t('task.duration')">{{ detailTask.durationMs ? (detailTask.durationMs / 1000).toFixed(2) + 's' : '-' }}</el-descriptions-item>
        <el-descriptions-item :label="t('task.createTime')">{{ detailTask.createTime || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="t('task.createTime')">{{ detailTask.updateTime || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">

defineOptions({ name: 'AgentTasks' })
import { onMounted, onUnmounted, ref, reactive, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { PageHeader, LumTablePanel, type SearchField } from '@/components/common'
import { cancelAgentTask, listAgentTasks, type AgentTaskVO } from '@/api/modules/agent'

const { t } = useI18n()

const loading = ref(false)
const tasks = ref<AgentTaskVO[]>([])
const queryForm = reactive({ status: '', agentId: '' })
const pagination = reactive({ pageNum: 1, pageSize: 20, total: 0 })
const detailVisible = ref(false)
const detailTask = ref<AgentTaskVO | null>(null)
const autoRefresh = ref(false)
let refreshTimer: ReturnType<typeof setInterval> | null = null

const searchFields = computed<SearchField[]>(() => [
  {
    prop: 'status',
    label: t('task.status'),
    type: 'select',
    placeholder: t('common.all'),
    options: [
      { label: t('task.queued'), value: 'QUEUED' },
      { label: t('task.running'), value: 'RUNNING' },
      { label: t('task.completed'), value: 'COMPLETED' },
      { label: t('task.failed'), value: 'FAILED' },
      { label: t('task.cancelled'), value: 'CANCELLED' }
    ]
  },
  { prop: 'agentId', label: 'Agent ID', type: 'input', placeholder: t('common.pleaseInput') }
])

const statusType = (status: string) => {
  const map: Record<string, string> = {
    QUEUED: 'info',
    RUNNING: 'warning',
    COMPLETED: 'success',
    FAILED: 'danger',
    CANCELLED: 'info'
  }
  return map[status] || 'info'
}

const statusLabel = (status: string) => {
  const map: Record<string, string> = {
    QUEUED: t('task.queued'),
    RUNNING: t('task.running'),
    COMPLETED: t('task.completed'),
    FAILED: t('task.failed'),
    CANCELLED: t('task.cancelled')
  }
  return map[status] || status
}

const loadTasks = async () => {
  loading.value = true
  try {
    const res = await listAgentTasks({
      status: queryForm.status || undefined,
      agentId: queryForm.agentId ? Number(queryForm.agentId) : undefined,
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize
    })
    tasks.value = res.data.list || []
    pagination.total = res.data.total || 0
  } finally {
    loading.value = false
  }
}

const onPageChange = (page: number) => { pagination.pageNum = page; loadTasks() }
const onSizeChange = (size: number) => { pagination.pageSize = size; pagination.pageNum = 1; loadTasks() }

const resetFilter = () => {
  queryForm.status = ''
  queryForm.agentId = ''
  pagination.pageNum = 1
  loadTasks()
}

const viewDetail = (row: AgentTaskVO) => {
  detailTask.value = row
  detailVisible.value = true
}

const handleCancel = async (taskUuid: string) => {
  await ElMessageBox.confirm(t('task.cancelConfirm'), t('common.tip'), { type: 'warning' })
  await cancelAgentTask(taskUuid)
  ElMessage.success('已发送取消请求')
  await loadTasks()
}

const startAutoRefresh = () => {
  autoRefresh.value = true
  refreshTimer = setInterval(loadTasks, 3000)
}

const stopAutoRefresh = () => {
  autoRefresh.value = false
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
}

onMounted(() => {
  loadTasks()
})

onUnmounted(() => {
  stopAutoRefresh()
})
</script>

<style scoped>
.task-page { padding: 0; }
.filter-form { margin-bottom: 16px; }
.uuid-text { font-family: Consolas, Monaco, monospace; color: var(--lumina-text-secondary); }
.pagination-wrapper { margin-top: 16px; display: flex; justify-content: flex-end; }
.task-result {
  max-height: 200px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-all;
  font-size: 13px;
  line-height: 1.6;
}
.error-text { color: var(--lumina-danger); }

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
