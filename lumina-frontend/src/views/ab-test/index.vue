<template>
  <div class="ab-test-page">
    <PageHeader :title="t('abTest.title')" :description="t('abTest.description')">
      <template #actions>
        <el-button type="primary" @click="showCreateDialog = true">
          <el-icon><Plus /></el-icon>
          {{ t('abTest.create') }}
        </el-button>
      </template>
    </PageHeader>

    <el-card v-loading="loading">
      <el-table :data="experiments" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" :label="t('abTest.name')" min-width="180" />
        <el-table-column prop="agentId" :label="t('abTest.agentId')" width="90" />
        <el-table-column :label="t('abTest.variants')" width="150">
          <template #default="{ row }">
            <el-tag v-for="v in row.variants" :key="v.id" size="small" class="variant-tag">
              {{ v.name }} ({{ v.weight }}%)
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="trafficPercent" :label="t('abTest.traffic')" width="90">
          <template #default="{ row }">{{ row.trafficPercent }}%</template>
        </el-table-column>
        <el-table-column prop="status" :label="t('common.status')" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ t(`abTest.status.${row.status}`) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="report.totalExposures" :label="t('abTest.exposures')" width="90" />
        <el-table-column prop="createTime" :label="t('common.createTime')" width="180" />
        <el-table-column :label="t('common.actions')" width="280" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="viewReport(row)">{{ t('abTest.report') }}</el-button>
            <el-button v-if="row.status === 'DRAFT' || row.status === 'PAUSED'" link type="success" @click="handleStart(row)">{{ t('abTest.start') }}</el-button>
            <el-button v-if="row.status === 'RUNNING'" link type="warning" @click="handlePause(row)">{{ t('abTest.pause') }}</el-button>
            <el-button v-if="row.status === 'RUNNING' || row.status === 'PAUSED'" link type="info" @click="handleComplete(row)">{{ t('abTest.complete') }}</el-button>
            <el-button link type="danger" @click="handleDelete(row)">{{ t('common.delete') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !experiments.length" :description="t('common.noData')">
        <el-button type="primary" @click="showCreateDialog = true">{{ t('abTest.create') }}</el-button>
      </el-empty>
    </el-card>

    <!-- 创建实验对话框 -->
    <el-dialog v-model="showCreateDialog" :title="t('abTest.create')" width="700px">
      <el-form :model="createForm" label-width="120px">
        <el-form-item :label="t('abTest.name')">
          <el-input v-model="createForm.name" :placeholder="t('abTest.namePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('abTest.agentId')">
          <el-input-number v-model="createForm.agentId" :min="1" />
        </el-form-item>
        <el-form-item :label="t('abTest.traffic')">
          <el-slider v-model="createForm.trafficPercent" :min="1" :max="100" show-input style="max-width: 400px" />
        </el-form-item>
        <el-form-item v-for="(v, i) in createForm.variants" :key="i" :label="t('abTest.variantLabel', { name: v.name })">
          <el-input v-model="v.name" placeholder="A/B/C" style="width: 80px; margin-right: 8px" />
          <el-input-number v-model="v.weight" :min="1" :max="100" :placeholder="t('abTest.weightPlaceholder')" style="width: 120px; margin-right: 8px" />
          <el-input v-model="v.llmConfig" type="textarea" :rows="2" placeholder='LLM配置 JSON, 如 {"modelType":"glm","modelName":"glm-4"}' style="flex: 1" />
          <el-button link type="danger" @click="createForm.variants.splice(i, 1)" style="margin-left: 8px">
            <el-icon><Delete /></el-icon>
          </el-button>
        </el-form-item>
        <el-button @click="addVariant">{{ t('abTest.addVariant') }}</el-button>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 报告对话框 -->
    <el-dialog v-model="showReportDialog" :title="t('abTest.reportTitle')" width="700px">
      <div v-if="currentReport">
        <el-descriptions :column="2" border>
          <el-descriptions-item :label="t('abTest.totalExposures')">{{ currentReport.report?.totalExposures || 0 }}</el-descriptions-item>
        </el-descriptions>
        <el-table :data="currentReport.report?.variants || []" stripe style="margin-top: 16px">
          <el-table-column prop="variantName" :label="t('abTest.variantName')" width="100" />
          <el-table-column prop="exposures" :label="t('abTest.exposures')" width="90" />
          <el-table-column :label="t('abTest.successRate')" width="120">
            <template #default="{ row }">{{ (row.successRate * 100).toFixed(1) }}%</template>
          </el-table-column>
          <el-table-column :label="t('abTest.avgLatency')" width="120">
            <template #default="{ row }">{{ row.avgLatencyMs?.toFixed(0) || 0 }} ms</template>
          </el-table-column>
          <el-table-column :label="t('abTest.avgTokens')" width="100">
            <template #default="{ row }">{{ row.avgTokens?.toFixed(0) || 0 }}</template>
          </el-table-column>
        </el-table>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">

defineOptions({ name: 'AbTest' })
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete } from '@element-plus/icons-vue'
import {
  listAbExperiments, getAbExperiment, createAbExperiment,
  startAbExperiment, pauseAbExperiment, completeAbExperiment, deleteAbExperiment,
  type AbExperimentVO, type CreateAbExperimentDTO,
} from '@/api/modules/ab-test'
import { PageHeader } from '@/components/common'

const { t } = useI18n()
const loading = ref(false)
const experiments = ref<AbExperimentVO[]>([])
const showCreateDialog = ref(false)
const showReportDialog = ref(false)
const creating = ref(false)
const currentReport = ref<AbExperimentVO | null>(null)

const createForm = ref<CreateAbExperimentDTO>({
  name: '',
  agentId: 1,
  trafficPercent: 100,
  variants: [
    { name: 'A', weight: 50, llmConfig: '' },
    { name: 'B', weight: 50, llmConfig: '' },
  ],
})

async function loadData() {
  loading.value = true
  try {
    const res = await listAbExperiments()
    experiments.value = res.data || []
  } catch { experiments.value = [] }
  finally { loading.value = false }
}

function addVariant() {
  const name = String.fromCharCode(65 + createForm.value.variants.length)
  createForm.value.variants.push({ name, weight: 50, llmConfig: '' })
}

async function handleCreate() {
  if (!createForm.value.name || createForm.value.variants.length < 2) {
    ElMessage.warning(t('abTest.validateError'))
    return
  }
  creating.value = true
  try {
    await createAbExperiment(createForm.value)
    ElMessage.success(t('common.createSuccess'))
    showCreateDialog.value = false
    loadData()
  } catch { /* handled by interceptor */ }
  finally { creating.value = false }
}

async function viewReport(row: AbExperimentVO) {
  const res = await getAbExperiment(row.id)
  currentReport.value = res.data
  showReportDialog.value = true
}

async function handleStart(row: AbExperimentVO) {
  await startAbExperiment(row.id)
  ElMessage.success(t('abTest.startSuccess'))
  loadData()
}

async function handlePause(row: AbExperimentVO) {
  await pauseAbExperiment(row.id)
  ElMessage.success(t('abTest.pauseSuccess'))
  loadData()
}

async function handleComplete(row: AbExperimentVO) {
  await ElMessageBox.confirm(t('abTest.completeConfirm'), t('abTest.complete'), { type: 'warning' })
  await completeAbExperiment(row.id)
  ElMessage.success(t('abTest.completeSuccess'))
  loadData()
}

async function handleDelete(row: AbExperimentVO) {
  await ElMessageBox.confirm(t('common.deleteConfirm'), t('common.delete'), { type: 'warning' })
  await deleteAbExperiment(row.id)
  ElMessage.success(t('common.deleteSuccess'))
  loadData()
}

function statusTagType(status: string) {
  switch (status) {
    case 'RUNNING': return 'success'
    case 'PAUSED': return 'warning'
    case 'COMPLETED': return 'info'
    default: return ''
  }
}

onMounted(() => loadData())
</script>

<style scoped>
.ab-test-page { padding: var(--lumina-spacing-lg); }
.variant-tag { margin-right: 4px; }
</style>
