<template>
  <div class="task-page">
    <PageHeader :title="t('task.title')" :description="t('task.description')" />

    <el-card shadow="never">
      <el-form :inline="true" class="filter-form">
        <el-form-item :label="t('task.status')">
          <el-select v-model="filterStatus" :placeholder="t('common.all')" clearable style="width: 140px" @change="loadTasks">
            <el-option :label="t('task.queued')" value="QUEUED" />
            <el-option :label="t('task.running')" value="RUNNING" />
            <el-option :label="t('task.completed')" value="COMPLETED" />
            <el-option :label="t('task.failed')" value="FAILED" />
            <el-option :label="t('task.cancelled')" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="Agent ID">
          <el-input-number v-model="filterAgentId" :min="1" :controls="false" style="width: 100px" @change="loadTasks" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadTasks">{{ t('common.search') }}</el-button>
          <el-button @click="resetFilter">{{ t('common.refresh') }}</el-button>
          <el-button v-if="autoRefresh" type="success" plain @click="stopAutoRefresh">{{ t('task.stopRefresh') }}</el-button>
          <el-button v-else type="warning" plain @click="startAutoRefresh">{{ t('task.autoRefresh') }}</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="tasks" stripe>
        <el-table-column prop="taskUuid" label="任务 UUID" min-width="200" show-overflow-tooltip>
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
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="loadTasks"
          @current-change="loadTasks"
        />
      </div>
    </el-card>

    <el-dialog v-model="detailVisible" :title="t('task.detail')" width="700px">
      <el-descriptions v-if="detailTask" :column="2" border>
        <el-descriptions-item label="任务 UUID" :span="2">{{ detailTask.taskUuid }}</el-descriptions-item>
        <el-descriptions-item label="Agent ID">{{ detailTask.agentId }}</el-descriptions-item>
        <el-descriptions-item :label="t('task.status')">
          <el-tag :type="statusType(detailTask.status)" size="small">{{ statusLabel(detailTask.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item :label="t('task.input')" :span="2">{{ detailTask.inputText }}</el-descriptions-item>
        <el-descriptions-item :label="t('task.result')" :span="2">
          <div class="task-result">{{ detailTask.result || '-' }}</div>
        </el-descriptions-item>
        <el-descriptions-item v-if="detailTask.errorMessage" label="错误" :span="2">
          <span class="error-text">{{ detailTask.errorMessage }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="输入 Token">{{ detailTask.promptTokens || 0 }}</el-descriptions-item>
        <el-descriptions-item label="输出 Token">{{ detailTask.completionTokens || 0 }}</el-descriptions-item>
        <el-descriptions-item label="总 Token">{{ detailTask.totalTokens || 0 }}</el-descriptions-item>
        <el-descriptions-item :label="t('task.duration')">{{ detailTask.durationMs ? (detailTask.durationMs / 1000).toFixed(2) + 's' : '-' }}</el-descriptions-item>
        <el-descriptions-item :label="t('task.createTime')">{{ detailTask.createTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ detailTask.updateTime || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import { cancelAgentTask, listAgentTasks, type AgentTaskVO } from '@/api/modules/agent'

const { t } = useI18n()

const loading = ref(false)
const tasks = ref<AgentTaskVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const filterStatus = ref('')
const filterAgentId = ref<number | undefined>(undefined)
const detailVisible = ref(false)
const detailTask = ref<AgentTaskVO | null>(null)
const autoRefresh = ref(false)
let refreshTimer: ReturnType<typeof setInterval> | null = null

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
      status: filterStatus.value || undefined,
      agentId: filterAgentId.value,
      pageNum: pageNum.value,
      pageSize: pageSize.value
    })
    tasks.value = res.data.list || []
    total.value = res.data.total || 0
  } finally {
    loading.value = false
  }
}

const resetFilter = () => {
  filterStatus.value = ''
  filterAgentId.value = undefined
  pageNum.value = 1
  loadTasks()
}

const viewDetail = (row: AgentTaskVO) => {
  detailTask.value = row
  detailVisible.value = true
}

const handleCancel = async (taskUuid: string) => {
  await ElMessageBox.confirm(t('task.cancelConfirm'), '提示', { type: 'warning' })
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
.uuid-text { font-family: Consolas, Monaco, monospace; color: #909399; }
.pagination-wrapper { margin-top: 16px; display: flex; justify-content: flex-end; }
.task-result {
  max-height: 200px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-all;
  font-size: 13px;
  line-height: 1.6;
}
.error-text { color: #f56c6c; }
</style>
