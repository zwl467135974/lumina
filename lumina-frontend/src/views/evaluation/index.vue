<template>
  <div class="evaluation-page">
    <PageHeader title="Agent 评估" description="用数据集批量评估 Agent 输出质量、延迟和 Token 消耗" />

    <el-row :gutter="16">
      <el-col :span="10">
        <el-card shadow="never" class="panel-card">
          <template #header>
            <div class="card-header">
              <span>评估数据集</span>
              <el-button type="primary" size="small" @click="openDatasetDialog">新建数据集</el-button>
            </div>
          </template>

          <el-input v-model="queryName" placeholder="按名称搜索" clearable class="search-input" @change="loadDatasets" />
          <el-table v-loading="datasetLoading" :data="datasets" stripe @row-click="selectDataset">
            <el-table-column prop="name" label="名称" min-width="160" />
            <el-table-column label="用例" width="70">
              <template #default="{ row }">{{ row.cases?.length || 0 }}</template>
            </el-table-column>
            <el-table-column label="操作" width="90" fixed="right">
              <template #default="{ row }">
                <el-button link type="danger" @click.stop="handleDeleteDataset(row.id)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <el-col :span="14">
        <el-card shadow="never" class="panel-card">
          <template #header>执行评估</template>
          <el-empty v-if="!selectedDataset" description="请选择左侧数据集" />
          <div v-else>
            <el-descriptions :column="2" border size="small" class="dataset-summary">
              <el-descriptions-item label="数据集">{{ selectedDataset.name }}</el-descriptions-item>
              <el-descriptions-item label="用例数">{{ selectedDataset.cases.length }}</el-descriptions-item>
              <el-descriptions-item label="Agent 类型">{{ selectedDataset.agentType || '-' }}</el-descriptions-item>
              <el-descriptions-item label="创建时间">{{ selectedDataset.createTime || '-' }}</el-descriptions-item>
            </el-descriptions>

            <el-form :model="runForm" label-width="110px" class="run-form">
              <el-form-item label="Agent ID">
                <el-input-number v-model="runForm.agentId" :min="1" />
              </el-form-item>
              <el-form-item label="评分方式">
                <el-select v-model="runForm.scoringMethod" style="width: 240px">
                  <el-option label="精确匹配" value="EXACT_MATCH" />
                  <el-option label="关键词包含" value="CONTAINS" />
                  <el-option label="语义相似度" value="SEMANTIC_SIMILARITY" />
                  <el-option label="LLM Judge" value="LLM_JUDGE" />
                </el-select>
              </el-form-item>
              <el-form-item label="通过阈值">
                <el-slider v-model="runForm.threshold" :min="0" :max="1" :step="0.05" show-input style="max-width: 420px" />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" :loading="runLoading" @click="handleRunEvaluation">开始评估</el-button>
              </el-form-item>
            </el-form>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="panel-card" v-if="currentReport">
      <template #header>
        <div class="card-header">
          <span>评估报告 #{{ currentReport.runId }}</span>
          <el-tag :type="currentReport.passRate >= currentReport.threshold ? 'success' : 'warning'">
            通过率 {{ percent(currentReport.passRate) }}
          </el-tag>
        </div>
      </template>
      <el-row :gutter="16" class="metric-row">
        <el-col :span="6"><el-statistic title="通过用例" :value="`${currentReport.passedCases}/${currentReport.totalCases}`" /></el-col>
        <el-col :span="6"><el-statistic title="平均得分" :value="currentReport.avgScore" :precision="3" /></el-col>
        <el-col :span="6"><el-statistic title="平均延迟(ms)" :value="currentReport.avgLatencyMs" /></el-col>
        <el-col :span="6"><el-statistic title="总 Token" :value="currentReport.totalTokens || 0" /></el-col>
      </el-row>
      <el-table :data="currentReport.results" stripe class="result-table">
        <el-table-column prop="caseId" label="用例" width="120" />
        <el-table-column prop="category" label="分类" width="120" />
        <el-table-column prop="input" label="输入" min-width="180" show-overflow-tooltip />
        <el-table-column prop="expected" label="期望" min-width="160" show-overflow-tooltip />
        <el-table-column prop="actual" label="实际输出" min-width="220" show-overflow-tooltip />
        <el-table-column prop="score" label="得分" width="90">
          <template #default="{ row }">{{ row.score.toFixed(3) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.passed ? 'success' : 'danger'">{{ row.passed ? '通过' : '失败' }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never" class="panel-card">
      <template #header>历史评估</template>
      <el-table v-loading="runListLoading" :data="runs" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="datasetName" label="数据集" min-width="150" />
        <el-table-column prop="agentId" label="Agent ID" width="100" />
        <el-table-column prop="scoringMethod" label="评分方式" width="160" />
        <el-table-column label="通过率" width="110">
          <template #default="{ row }">{{ percent(row.passRate) }}</template>
        </el-table-column>
        <el-table-column label="平均分" width="100">
          <template #default="{ row }">{{ Number(row.avgScore).toFixed(3) }}</template>
        </el-table-column>
        <el-table-column prop="createTime" label="时间" width="180" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button link type="primary" @click="loadReport(row.id)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="datasetDialogVisible" title="新建评估数据集" width="760px">
      <el-form :model="datasetForm" label-width="110px">
        <el-form-item label="名称" required>
          <el-input v-model="datasetForm.name" placeholder="例如：客服 Agent 基础问答" />
        </el-form-item>
        <el-form-item label="Agent 类型">
          <el-input v-model="datasetForm.agentType" placeholder="例如：assistant" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="datasetForm.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="用例 YAML" required>
          <el-input v-model="datasetForm.casesYaml" type="textarea" :rows="12" class="yaml-input" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="datasetDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingDataset" @click="handleCreateDataset">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import {
  createEvaluationDataset,
  deleteEvaluationDataset,
  getEvaluationRunReport,
  listEvaluationDatasets,
  listEvaluationRuns,
  runEvaluation,
  type EvaluationDataset,
  type EvaluationRunRecord,
  type RunReport,
  type ScoringMethod
} from '@/api/modules/evaluation'

const sampleYaml = `cases:
  - id: basic-001
    input: 你好，请介绍一下 Lumina
    expected: Lumina
    category: 基础问答
  - id: basic-002
    input: 你支持哪些 Agent 能力？
    expected: Agent,工具,知识库
    category: 能力介绍`

const queryName = ref('')
const datasets = ref<EvaluationDataset[]>([])
const selectedDataset = ref<EvaluationDataset | null>(null)
const datasetLoading = ref(false)
const runLoading = ref(false)
const runListLoading = ref(false)
const savingDataset = ref(false)
const datasetDialogVisible = ref(false)
const runs = ref<EvaluationRunRecord[]>([])
const currentReport = ref<RunReport | null>(null)

const runForm = reactive<{ agentId: number; scoringMethod: ScoringMethod; threshold: number }>({
  agentId: 1,
  scoringMethod: 'CONTAINS',
  threshold: 0.7
})

const datasetForm = reactive({
  name: '',
  description: '',
  agentType: '',
  casesYaml: sampleYaml
})

const percent = (value?: number) => `${((value || 0) * 100).toFixed(1)}%`

const loadDatasets = async () => {
  datasetLoading.value = true
  try {
    const res = await listEvaluationDatasets({ name: queryName.value || undefined })
    datasets.value = res.data || []
  } finally {
    datasetLoading.value = false
  }
}

const loadRuns = async () => {
  runListLoading.value = true
  try {
    const res = await listEvaluationRuns({ datasetId: selectedDataset.value?.id })
    runs.value = res.data || []
  } finally {
    runListLoading.value = false
  }
}

const selectDataset = (row: EvaluationDataset) => {
  selectedDataset.value = row
  currentReport.value = null
  loadRuns()
}

const openDatasetDialog = () => {
  datasetForm.name = ''
  datasetForm.description = ''
  datasetForm.agentType = ''
  datasetForm.casesYaml = sampleYaml
  datasetDialogVisible.value = true
}

const handleCreateDataset = async () => {
  if (!datasetForm.name.trim() || !datasetForm.casesYaml.trim()) {
    ElMessage.warning('请填写名称和用例 YAML')
    return
  }
  savingDataset.value = true
  try {
    await createEvaluationDataset({ ...datasetForm })
    ElMessage.success('数据集已创建')
    datasetDialogVisible.value = false
    await loadDatasets()
  } finally {
    savingDataset.value = false
  }
}

const handleDeleteDataset = async (id: number) => {
  await ElMessageBox.confirm('确认删除该评估数据集？', '提示', { type: 'warning' })
  await deleteEvaluationDataset(id)
  ElMessage.success('已删除')
  if (selectedDataset.value?.id === id) {
    selectedDataset.value = null
    currentReport.value = null
  }
  await loadDatasets()
}

const handleRunEvaluation = async () => {
  if (!selectedDataset.value) return
  runLoading.value = true
  try {
    const res = await runEvaluation(selectedDataset.value.id, runForm)
    currentReport.value = res.data
    ElMessage.success('评估完成')
    await loadRuns()
  } finally {
    runLoading.value = false
  }
}

const loadReport = async (id: number) => {
  const res = await getEvaluationRunReport(id)
  currentReport.value = res.data
}

onMounted(() => {
  loadDatasets()
  loadRuns()
})
</script>

<style scoped>
.evaluation-page { padding: 0; }
.panel-card { margin-bottom: 16px; }
.card-header { display: flex; align-items: center; justify-content: space-between; }
.search-input { margin-bottom: 12px; }
.dataset-summary { margin-bottom: 16px; }
.run-form { margin-top: 16px; }
.metric-row { margin-bottom: 16px; }
.result-table { margin-top: 12px; }
.yaml-input :deep(textarea) { font-family: Consolas, Monaco, monospace; }
@media (max-width: 900px) {
  .evaluation-page :deep(.el-col) { max-width: 100%; flex: 0 0 100%; }
}
</style>
