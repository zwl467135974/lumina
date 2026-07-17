<template>
  <div class="trigger-page">
    <PageHeader :title="t('trigger.title')" :description="t('trigger.description')">
      <template #actions>
        <el-button type="primary" @click="handleCreate">
          <el-icon><Plus /></el-icon>
          {{ t('trigger.create') }}
        </el-button>
      </template>
    </PageHeader>

    <el-card v-loading="loading">
      <el-table :data="list" stripe>
        <el-table-column prop="name" :label="t('trigger.name')" min-width="140" show-overflow-tooltip />
        <el-table-column prop="cronExpr" :label="t('trigger.cronExpr')" width="140">
          <template #default="{ row }">
            <code class="cron-text">{{ row.cronExpr }}</code>
          </template>
        </el-table-column>
        <el-table-column prop="agentId" :label="t('trigger.agentId')" width="90">
          <template #default="{ row }">{{ agentName(row.agentId) }}</template>
        </el-table-column>
        <el-table-column prop="enabled" :label="t('common.status')" width="90">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">
              {{ row.enabled === 1 ? t('trigger.enabled') : t('trigger.paused') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastFireAt" :label="t('trigger.lastFireAt')" width="170">
          <template #default="{ row }">{{ row.lastFireAt || '-' }}</template>
        </el-table-column>
        <el-table-column prop="nextFireAt" :label="t('trigger.nextFireAt')" width="170">
          <template #default="{ row }">{{ row.enabled === 1 ? (row.nextFireAt || '-') : '-' }}</template>
        </el-table-column>
        <el-table-column prop="lastStatus" :label="t('trigger.lastStatus')" width="120">
          <template #default="{ row }">
            <el-tooltip v-if="row.lastStatus === 'FAILED' && row.lastError" :content="row.lastError" placement="top">
              <el-tag type="danger" size="small">FAILED ({{ row.failCount }})</el-tag>
            </el-tooltip>
            <el-tag v-else-if="row.lastStatus === 'SUCCESS'" type="success" size="small">SUCCESS</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.actions')" width="220" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.enabled === 1" link type="warning" @click="handlePause(row)">{{ t('trigger.pause') }}</el-button>
            <el-button v-else link type="success" @click="handleResume(row)">{{ t('trigger.resume') }}</el-button>
            <el-button link type="primary" :loading="firingId === row.id" @click="handleTriggerNow(row)">{{ t('trigger.triggerNow') }}</el-button>
            <el-button link type="danger" @click="handleDelete(row)">{{ t('common.delete') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !list.length" :description="t('trigger.empty')" />
      <div v-if="pagination.total > 0" class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pagination.pageNum"
          v-model:page-size="pagination.pageSize"
          :total="pagination.total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @current-change="loadData"
          @size-change="onSizeChange"
        />
      </div>
    </el-card>

    <!-- 创建对话框 -->
    <el-dialog v-model="createDialogVisible" :title="t('trigger.create')" width="580px" @close="handleCreateDialogClose">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="110px">
        <el-form-item :label="t('trigger.name')" prop="name">
          <el-input v-model="formData.name" :placeholder="t('trigger.namePlaceholder')" maxlength="64" />
        </el-form-item>
        <el-form-item :label="t('trigger.agentId')" prop="agentId">
          <el-select v-model="formData.agentId" filterable :placeholder="t('trigger.agentPlaceholder')" style="width: 100%">
            <el-option v-for="a in agents" :key="a.agentId" :label="a.agentName" :value="a.agentId" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('trigger.cronExpr')" prop="cronExpr">
          <el-input v-model="formData.cronExpr" :placeholder="t('trigger.cronPlaceholder')" maxlength="64" />
          <div class="form-tip">{{ t('trigger.cronTip') }}</div>
        </el-form-item>
        <el-form-item :label="t('trigger.inputText')" prop="inputText">
          <el-input v-model="formData.inputText" type="textarea" :rows="3" :placeholder="t('trigger.inputPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('trigger.misfirePolicy')" prop="misfirePolicy">
          <el-select v-model="formData.misfirePolicy" style="width: 100%">
            <el-option :label="t('trigger.misfireFireOnce')" value="FIRE_ONCE" />
            <el-option :label="t('trigger.misfireSkip')" value="SKIP" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="creating" @click="handleSubmit">{{ t('common.ok') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import {
  listTriggers, createTrigger, deleteTrigger, pauseTrigger, resumeTrigger, triggerNow,
  type AgentTriggerVO, type MisfirePolicy
} from '@/api/modules/trigger'
import { listAgents } from '@/api/modules/agent'
import type { AgentVO } from '@/types/api'
import { PageHeader } from '@/components/common'

const { t } = useI18n()
const loading = ref(false)
const list = ref<AgentTriggerVO[]>([])
const agents = ref<AgentVO[]>([])
const pagination = reactive({ pageNum: 1, pageSize: 20, total: 0 })
const firingId = ref<number | null>(null)

// 创建对话框
const createDialogVisible = ref(false)
const creating = ref(false)
const formRef = ref<FormInstance>()
const formData = reactive<{
  name: string
  agentId: number | undefined
  cronExpr: string
  inputText: string
  misfirePolicy: MisfirePolicy
}>({
  name: '',
  agentId: undefined,
  cronExpr: '',
  inputText: '',
  misfirePolicy: 'FIRE_ONCE'
})

const formRules: FormRules = {
  name: [{ required: true, message: t('trigger.nameRequired'), trigger: 'blur' }],
  agentId: [{ required: true, message: t('trigger.agentRequired'), trigger: 'change' }],
  cronExpr: [
    { required: true, message: t('trigger.cronRequired'), trigger: 'blur' },
    { pattern: /^\S+\s+\S+\s+\S+\s+\S+\s+\S+\s+\S+$/, message: t('trigger.cronInvalid'), trigger: 'blur' }
  ],
  inputText: [{ required: true, message: t('trigger.inputRequired'), trigger: 'blur' }]
}

const agentName = (agentId: number) => {
  const agent = agents.value.find(a => a.agentId === agentId)
  return agent ? agent.agentName : `#${agentId}`
}

async function loadData() {
  loading.value = true
  try {
    const res = await listTriggers({ pageNum: pagination.pageNum, pageSize: pagination.pageSize })
    list.value = res.data.list || []
    pagination.total = res.data.total || 0
  } catch { list.value = [] }
  finally { loading.value = false }
}

async function loadAgents() {
  try {
    const res = await listAgents({ pageNum: 1, pageSize: 100 })
    agents.value = res.data.list || []
  } catch { agents.value = [] }
}

const onSizeChange = (size: number) => { pagination.pageSize = size; pagination.pageNum = 1; loadData() }

function handleCreate() {
  createDialogVisible.value = true
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    creating.value = true
    try {
      await createTrigger({
        name: formData.name,
        agentId: formData.agentId!,
        cronExpr: formData.cronExpr,
        inputText: formData.inputText,
        misfirePolicy: formData.misfirePolicy
      })
      createDialogVisible.value = false
      ElMessage.success(t('trigger.createSuccess'))
      loadData()
    } catch (error) {
      console.error('创建失败:', error)
    } finally {
      creating.value = false
    }
  })
}

function handleCreateDialogClose() {
  formRef.value?.resetFields()
  formData.name = ''
  formData.agentId = undefined
  formData.cronExpr = ''
  formData.inputText = ''
  formData.misfirePolicy = 'FIRE_ONCE'
}

async function handlePause(row: AgentTriggerVO) {
  await pauseTrigger(row.id)
  ElMessage.success(t('trigger.pauseSuccess'))
  loadData()
}

async function handleResume(row: AgentTriggerVO) {
  await resumeTrigger(row.id)
  ElMessage.success(t('trigger.resumeSuccess'))
  loadData()
}

async function handleTriggerNow(row: AgentTriggerVO) {
  firingId.value = row.id
  try {
    await triggerNow(row.id)
    ElMessage.success(t('trigger.triggerNowSuccess'))
    loadData()
  } finally {
    firingId.value = null
  }
}

async function handleDelete(row: AgentTriggerVO) {
  try {
    await ElMessageBox.confirm(
      t('trigger.deleteConfirm', { name: row.name }),
      t('common.tip'),
      { type: 'warning' }
    )
    await deleteTrigger(row.id)
    ElMessage.success(t('common.deleteSuccess'))
    loadData()
  } catch {
    // 用户取消
  }
}

onMounted(() => {
  loadData()
  loadAgents()
})
</script>

<style scoped>
.trigger-page { padding: 0; }
.cron-text { font-family: Consolas, Monaco, monospace; color: var(--lumina-text-secondary); }
.form-tip { font-size: 12px; color: var(--lumina-text-secondary); line-height: 1.5; margin-top: 4px; }
.pagination-wrapper { margin-top: 16px; display: flex; justify-content: flex-end; }

@media (max-width: 768px) {
  :deep(.el-table) {
    font-size: 12px;
  }
}
</style>
