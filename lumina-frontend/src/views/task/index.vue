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
        <el-button type="primary" @click="openBatchDialog">{{ t('task.batch.title') }}</el-button>
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
        <el-table-column :label="t('task.batch.flag')" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.bundleCount" size="small" type="primary">x{{ row.bundleCount }}</el-tag>
            <el-tag v-else-if="row.parentUuid" size="small" type="info">#{{ (row.bundleIndex ?? 0) + 1 }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.actions')" width="250" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="viewDetail(row)">{{ t('common.detail') }}</el-button>
            <el-button v-if="row.bundleCount && (row.status === 'QUEUED' || row.status === 'RUNNING')" link type="warning" @click="handleCancelBatch(row.taskUuid)">{{ t('task.batch.cancelAll') }}</el-button>
            <el-button
              v-if="row.status === 'COMPLETED' && row.result"
              link
              type="success"
              @click="openDepositDialog(row)"
            >{{ t('knowledge.depositAction') }}</el-button>
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

      <!-- 分治批次子任务 -->
      <template v-if="detailTask && detailTask.bundleCount">
        <el-divider content-position="left">{{ t('task.batch.children') }}（{{ batchChildren.length }}/{{ detailTask.bundleCount }}）</el-divider>
        <el-table :data="batchChildren" size="small" max-height="260">
          <el-table-column prop="bundleIndex" :label="t('task.batch.index')" width="70">
            <template #default="{ row }">#{{ (row.bundleIndex ?? 0) + 1 }}</template>
          </el-table-column>
          <el-table-column :label="t('task.status')" width="100">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="totalTokens" :label="t('task.tokenUsage')" width="90">
            <template #default="{ row }">{{ row.totalTokens || '-' }}</template>
          </el-table-column>
          <el-table-column prop="result" :label="t('task.result')" min-width="200" show-overflow-tooltip />
        </el-table>
      </template>
    </el-dialog>

    <!-- 分治任务提交对话框 -->
    <el-dialog v-model="batchVisible" :title="t('task.batch.title')" width="720px" :close-on-click-modal="false">
      <el-form label-width="110px">
        <el-form-item :label="t('task.agentId')" required>
          <el-select v-model="batchForm.agentId" style="width: 100%" :placeholder="t('task.batch.agentPlaceholder')">
            <el-option v-for="a in batchAgents" :key="a.agentId" :value="a.agentId" :label="a.agentName" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('task.batch.instruction')" required>
          <el-input v-model="batchForm.instruction" :placeholder="t('task.batch.instructionPlaceholder')" maxlength="2000" show-word-limit />
        </el-form-item>
        <el-form-item :label="t('task.batch.input')" required>
          <el-input v-model="batchForm.inputText" type="textarea" :rows="10" :placeholder="t('task.batch.inputPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('task.batch.strategy')">
          <el-select v-model="batchForm.splitStrategy" style="width: 200px">
            <el-option value="BY_LINES" :label="t('task.batch.byLines')" />
            <el-option value="BY_CHARS" :label="t('task.batch.byChars')" />
          </el-select>
          <el-input-number v-model="batchForm.bundleSize" :min="1" :max="50000" style="margin-left: 12px; width: 140px" />
          <span class="batch-hint">{{ t('task.batch.sizeHint') }}</span>
        </el-form-item>
        <el-form-item :label="t('task.batch.merge')">
          <el-radio-group v-model="batchForm.mergeMode">
            <el-radio value="CONCAT">{{ t('task.batch.mergeConcat') }}</el-radio>
            <el-radio value="LLM">{{ t('task.batch.mergeLlm') }}</el-radio>
          </el-radio-group>
          <el-input-number v-model="batchForm.maxBundles" :min="1" :max="50" style="margin-left: 12px; width: 120px" />
          <span class="batch-hint">{{ t('task.batch.maxBundlesHint') }}</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="batchVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="batchSubmitting" @click="submitBatch">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 知识沉淀对话框（任务产物 → 审核 → 入知识库） -->
    <el-dialog v-model="depositVisible" :title="t('knowledge.depositAction')" width="720px" :close-on-click-modal="false">
      <el-form label-width="100px">
        <el-form-item :label="t('knowledge.depositTitle')" required>
          <el-input v-model="depositForm.title" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item :label="t('knowledge.depositKb')" required>
          <el-select v-model="depositForm.kbId" style="width: 100%" :placeholder="t('knowledge.depositKbPlaceholder')">
            <el-option v-for="kb in knowledgeBases" :key="kb.id" :value="kb.id" :label="kb.name" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('knowledge.depositContent')" required>
          <el-input v-model="depositForm.content" type="textarea" :rows="12" />
        </el-form-item>
      </el-form>
      <el-alert type="info" :closable="false" :title="t('knowledge.depositTip')" style="margin-top: 8px" />
      <template #footer>
        <el-button @click="depositVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="depositing" @click="submitDeposit">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">

defineOptions({ name: 'AgentTasks' })
import { onMounted, onUnmounted, ref, reactive, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { PageHeader, LumTablePanel, type SearchField } from '@/components/common'
import { cancelAgentTask, listAgentTasks, listAgents, submitBatchTask, listBatchChildren, cancelBatchTask, type AgentTaskVO } from '@/api/modules/agent'
import type { AgentVO } from '@/types/api'
import { createKnowledgeDeposit } from '@/api/modules/knowledge'
import { listKnowledgeBases, type KnowledgeBaseVO } from '@/api/modules/knowledge-base'

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
      { label: t('task.cancelled'), value: 'CANCELLED' },
      { label: t('task.interrupted'), value: 'INTERRUPTED' }
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
    CANCELLED: 'info',
    INTERRUPTED: 'warning'
  }
  return map[status] || 'info'
}

const statusLabel = (status: string) => {
  const map: Record<string, string> = {
    QUEUED: t('task.queued'),
    RUNNING: t('task.running'),
    COMPLETED: t('task.completed'),
    FAILED: t('task.failed'),
    CANCELLED: t('task.cancelled'),
    INTERRUPTED: t('task.interrupted')
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
  batchChildren.value = []
  if (row.bundleCount) {
    listBatchChildren(row.taskUuid).then(res => { batchChildren.value = res.data || [] }).catch(() => {})
  }
}

// ==================== 分治批次（大输入 fan-out） ====================
const batchVisible = ref(false)
const batchSubmitting = ref(false)
const batchAgents = ref<AgentVO[]>([])
const batchChildren = ref<AgentTaskVO[]>([])
const batchForm = reactive({
  agentId: null as number | null,
  instruction: '',
  inputText: '',
  splitStrategy: 'BY_LINES',
  bundleSize: 50,
  maxBundles: 20,
  mergeMode: 'CONCAT'
})

const openBatchDialog = async () => {
  if (batchAgents.value.length === 0) {
    try {
      const res = await listAgents({ pageNum: 1, pageSize: 100 })
      batchAgents.value = (res.data?.list || []).filter(a => a.status === 1)
    } catch {
      batchAgents.value = []
    }
  }
  batchForm.agentId = null
  batchForm.instruction = ''
  batchForm.inputText = ''
  batchVisible.value = true
}

const submitBatch = async () => {
  if (!batchForm.agentId || !batchForm.instruction.trim() || !batchForm.inputText.trim()) {
    ElMessage.warning(t('task.batch.required'))
    return
  }
  batchSubmitting.value = true
  try {
    await submitBatchTask(batchForm.agentId, {
      instruction: batchForm.instruction.trim(),
      inputText: batchForm.inputText,
      splitStrategy: batchForm.splitStrategy as 'BY_LINES' | 'BY_CHARS',
      bundleSize: batchForm.bundleSize,
      maxBundles: batchForm.maxBundles,
      mergeMode: batchForm.mergeMode as 'CONCAT' | 'LLM'
    })
    ElMessage.success(t('task.batch.submitted'))
    batchVisible.value = false
    pagination.pageNum = 1
    loadTasks()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message ?? t('common.saveFailed'))
  } finally {
    batchSubmitting.value = false
  }
}

const handleCancelBatch = async (taskUuid: string) => {
  await ElMessageBox.confirm(t('task.batch.cancelConfirm'), t('common.confirm'), { type: 'warning' })
  await cancelBatchTask(taskUuid)
  ElMessage.success(t('common.success'))
  loadTasks()
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

// ==================== 知识沉淀（任务产物 → 审核 → 入知识库） ====================
const depositVisible = ref(false)
const depositing = ref(false)
const knowledgeBases = ref<KnowledgeBaseVO[]>([])
const depositForm = reactive({ title: '', kbId: null as number | null, content: '' })
let depositTask: AgentTaskVO | null = null

const openDepositDialog = async (row: AgentTaskVO) => {
  depositTask = row
  depositForm.title = (row.inputText || '').slice(0, 50) || `task-${row.taskUuid.slice(0, 8)}`
  depositForm.content = row.result || ''
  if (knowledgeBases.value.length === 0) {
    try {
      const res = await listKnowledgeBases()
      knowledgeBases.value = res.data || []
    } catch {
      knowledgeBases.value = []
    }
  }
  depositVisible.value = true
}

const submitDeposit = async () => {
  if (!depositForm.title.trim() || !depositForm.kbId || !depositForm.content.trim()) {
    ElMessage.warning(t('knowledge.depositRequired'))
    return
  }
  depositing.value = true
  try {
    await createKnowledgeDeposit({
      title: depositForm.title.trim(),
      content: depositForm.content,
      kbId: depositForm.kbId,
      sourceType: 'TASK',
      sourceId: depositTask?.taskUuid,
      agentId: depositTask?.agentId
    })
    ElMessage.success(t('knowledge.depositSubmitted'))
    depositVisible.value = false
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message ?? t('common.saveFailed'))
  } finally {
    depositing.value = false
  }
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
.batch-hint { margin-left: 8px; color: var(--lumina-text-secondary); font-size: 12px; }

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
