<template>
  <div class="evaluation-page">
    <PageHeader :title="t('evaluation.title')" :description="t('evaluation.description')" />

    <el-row :gutter="16">
      <el-col :span="10">
        <el-card shadow="never" class="panel-card">
          <template #header>
            <div class="card-header">
              <span>{{ t('evaluation.datasets') }}</span>
              <div>
                <el-upload :show-file-list="false" :before-upload="handleFileImport" accept=".yaml,.yml" style="display: inline-block; margin-right: 8px">
                  <el-button size="small" type="success" plain>{{ t('evaluation.importYaml') }}</el-button>
                </el-upload>
                <el-button type="primary" size="small" @click="openDatasetDialog">{{ t('evaluation.createDataset') }}</el-button>
              </div>
            </div>
          </template>

          <el-input v-model="queryName" placeholder="按名称搜索" clearable class="search-input" @change="loadDatasets" />
          <el-table v-loading="datasetLoading" :data="datasets" stripe @row-click="selectDataset">
            <el-table-column prop="name" label="名称" min-width="160" />
            <el-table-column :label="t('evaluation.caseId')" width="70">
              <template #default="{ row }">{{ row.cases?.length || 0 }}</template>
            </el-table-column>
            <el-table-column :label="t('common.actions')" width="90" fixed="right">
              <template #default="{ row }">
                <el-button link type="danger" @click.stop="handleDeleteDataset(row.id)">{{ t('common.delete') }}</el-button>
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
              <el-descriptions-item :label="t('common.createTime')">{{ selectedDataset.createTime || '-' }}</el-descriptions-item>
            </el-descriptions>

            <el-form :model="runForm" label-width="110px" class="run-form">
              <el-form-item label="Agent">
                <el-select v-model="runForm.agentId" style="width: 280px" placeholder="Select Agent">
                  <el-option v-for="a in agents" :key="a.agentId" :label="`${a.agentName} (#${a.agentId})`" :value="a.agentId" />
                </el-select>
              </el-form-item>
              <el-form-item :label="t('evaluation.scoringMethod')">
                <el-select v-model="runForm.scoringMethod" style="width: 240px">
                  <el-option :label="t('evaluation.exactMatch')" value="EXACT_MATCH" />
                  <el-option :label="t('evaluation.contains')" value="CONTAINS" />
                  <el-option :label="t('evaluation.semanticSimilarity')" value="SEMANTIC_SIMILARITY" />
                  <el-option label="LLM Judge" value="LLM_JUDGE" />
                </el-select>
              </el-form-item>
              <el-form-item :label="t('evaluation.threshold')">
                <el-slider v-model="runForm.threshold" :min="0" :max="1" :step="0.05" show-input style="max-width: 420px" />
              </el-form-item>
              <el-form-item :label="t('evaluation.asyncMode')">
                <el-switch v-model="asyncMode" active-text="大数据集（>20 条）建议开启" />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" :loading="runLoading" @click="handleRunEvaluation">{{ t('evaluation.runEvaluation') }}</el-button>
                <el-tag v-if="asyncRunning" type="warning" class="async-tag">评估进行中... (Run #{{ asyncRunId }})</el-tag>
              </el-form-item>
            </el-form>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="panel-card" v-if="currentReport">
      <template #header>
        <div class="card-header">
          <span>{{ t('evaluation.report') }} #{{ currentReport.runId }}</span>
          <el-tag :type="currentReport.passRate >= currentReport.threshold ? 'success' : 'warning'">
            {{ t('evaluation.passRate') }} {{ percent(currentReport.passRate) }}
          </el-tag>
        </div>
      </template>
      <el-row :gutter="16" class="metric-row">
        <el-col :span="6"><el-statistic title="通过用例" :value="`${currentReport.passedCases}/${currentReport.totalCases}`" /></el-col>
        <el-col :span="6"><el-statistic :title="t('evaluation.avgScore')" :value="currentReport.avgScore" :precision="3" /></el-col>
        <el-col :span="6"><el-statistic title="平均延迟(ms)" :value="currentReport.avgLatencyMs" /></el-col>
        <el-col :span="6"><el-statistic :title="t('evaluation.totalTokens')" :value="currentReport.totalTokens || 0" /></el-col>
      </el-row>

      <el-row :gutter="16">
        <el-col :span="12">
          <div class="chart-title">{{ t('evaluation.categoryStats') }}</div>
          <v-chart v-if="categoryChartOption" :option="categoryChartOption" autoresize style="height: 280px" />
          <el-empty v-else description="暂无分类数据" :image-size="40" />
        </el-col>
        <el-col :span="12">
          <div class="chart-title">{{ t('evaluation.trend') }}</div>
          <v-chart v-if="trendChartOption" :option="trendChartOption" autoresize style="height: 280px" />
          <el-empty v-else description="暂无趋势数据（至少需 2 次评估）" :image-size="40" />
        </el-col>
      </el-row>

      <el-table :data="currentReport.results" stripe class="result-table">
        <el-table-column prop="caseId" :label="t('evaluation.caseId')" width="120" />
        <el-table-column prop="category" :label="t('evaluation.category')" width="120" />
        <el-table-column prop="input" :label="t('evaluation.input')" min-width="180" show-overflow-tooltip />
        <el-table-column prop="expected" :label="t('evaluation.expected')" min-width="160" show-overflow-tooltip />
        <el-table-column prop="actual" :label="t('evaluation.actual')" min-width="220" show-overflow-tooltip />
        <el-table-column prop="score" :label="t('evaluation.score')" width="90">
          <template #default="{ row }">{{ row.score.toFixed(3) }}</template>
        </el-table-column>
        <el-table-column :label="t('common.status')" width="90">
          <template #default="{ row }">
            <el-tag :type="row.passed ? 'success' : 'danger'">{{ row.passed ? '通过' : '失败' }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never" class="panel-card">
      <template #header>{{ t('evaluation.history') }}</template>
      <el-table v-loading="runListLoading" :data="runs" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="datasetName" label="数据集" min-width="150" />
        <el-table-column prop="agentId" label="Agent ID" width="100" />
        <el-table-column prop="scoringMethod" :label="t('evaluation.scoringMethod')" width="160" />
        <el-table-column :label="t('common.status')" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'COMPLETED' ? 'success' : row.status === 'FAILED' ? 'danger' : 'warning'" size="small">
              {{ row.status || 'COMPLETED' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('evaluation.passRate')" width="110">
          <template #default="{ row }">{{ percent(row.passRate) }}</template>
        </el-table-column>
        <el-table-column label="平均分" width="100">
          <template #default="{ row }">{{ Number(row.avgScore).toFixed(3) }}</template>
        </el-table-column>
        <el-table-column prop="createTime" label="时间" width="180" />
        <el-table-column :label="t('common.actions')" width="170">
          <template #default="{ row }">
            <el-button link type="primary" @click="loadReport(row.id)">查看</el-button>
            <el-button link type="info" @click="startCompare(row.id)">{{ t('evaluation.compare') }}</el-button>
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
        <el-form-item :label="t('common.description')">
          <el-input v-model="datasetForm.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item :label="t('evaluation.yamlContent')" required>
          <el-input v-model="datasetForm.casesYaml" type="textarea" :rows="12" class="yaml-input" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="datasetDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="savingDataset" @click="handleCreateDataset">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="compareDialogVisible" :title="t('evaluation.compareDialog')" width="800px">
      <div v-if="compareData" class="compare-content">
        <el-row :gutter="16" class="metric-row">
          <el-col :span="6">
            <el-statistic :title="t('evaluation.passRateChange')"
              :value="(compareData.passRateDiff * 100).toFixed(1) + '%'"
              :value-style="{ color: compareData.passRateDiff >= 0 ? '#67c23a' : '#f56c6c' }" />
          </el-col>
          <el-col :span="6">
            <el-statistic :title="t('evaluation.scoreChange')"
              :value="compareData.avgScoreDiff.toFixed(4)"
              :value-style="{ color: compareData.avgScoreDiff >= 0 ? '#67c23a' : '#f56c6c' }" />
          </el-col>
          <el-col :span="4"><el-statistic :title="t('evaluation.improved')" :value="compareData.improved" /></el-col>
          <el-col :span="4"><el-statistic :title="t('evaluation.regressed')" :value="compareData.regressed" /></el-col>
          <el-col :span="4"><el-statistic :title="t('evaluation.unchanged')" :value="compareData.unchanged" /></el-col>
        </el-row>
        <el-table :data="compareData.cases" stripe max-height="400">
          <el-table-column prop="caseId" :label="t('evaluation.caseId')" width="120" />
          <el-table-column prop="category" :label="t('evaluation.category')" width="100" />
          <el-table-column label="分数A" width="80">
            <template #default="{ row }">{{ row.scoreA.toFixed(3) }}</template>
          </el-table-column>
          <el-table-column label="分数B" width="80">
            <template #default="{ row }">{{ row.scoreB.toFixed(3) }}</template>
          </el-table-column>
          <el-table-column label="变化" width="80">
            <template #default="{ row }">
              <span :style="{ color: row.scoreDiff > 0 ? '#67c23a' : row.scoreDiff < 0 ? '#f56c6c' : '#909399' }">
                {{ row.scoreDiff > 0 ? '+' : '' }}{{ row.scoreDiff.toFixed(3) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="趋势" width="80">
            <template #default="{ row }">
              <el-tag :type="row.trend === 'IMPROVED' ? 'success' : row.trend === 'REGRESSED' ? 'danger' : 'info'" size="small">
                {{ row.trend === 'IMPROVED' ? '↑' : row.trend === 'REGRESSED' ? '↓' : '→' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart, LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import {
  compareEvaluationRuns,
  createEvaluationDataset,
  deleteEvaluationDataset,
  getEvaluationRunReport,
  getEvaluationTrend,
  importEvaluationDataset,
  listEvaluationDatasets,
  listEvaluationRuns,
  runEvaluation,
  runEvaluationAsync,
  type EvaluationDataset,
  type EvaluationRunRecord,
  type RunReport,
  type ScoringMethod
} from '@/api/modules/evaluation'
import { listAgents } from '@/api/modules/agent'
import type { AgentVO } from '@/types/api'

use([CanvasRenderer, BarChart, LineChart, GridComponent, TooltipComponent, LegendComponent])

const { t } = useI18n()

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
const agents = ref<AgentVO[]>([])
const selectedDataset = ref<EvaluationDataset | null>(null)
const datasetLoading = ref(false)
const runLoading = ref(false)
const runListLoading = ref(false)
const savingDataset = ref(false)
const datasetDialogVisible = ref(false)
const runs = ref<EvaluationRunRecord[]>([])
const trendData = ref<EvaluationRunRecord[]>([])
const currentReport = ref<RunReport | null>(null)
const asyncMode = ref(false)
const asyncRunning = ref(false)
const asyncRunId = ref<number | null>(null)
let asyncPollTimer: ReturnType<typeof setInterval> | null = null

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

const categoryChartOption = computed(() => {
  if (!currentReport.value?.categoryStats) return null
  const entries = Object.entries(currentReport.value.categoryStats)
  if (entries.length === 0) return null
  return {
    tooltip: { trigger: 'axis', formatter: (params: any) => {
      const data = params[0]
      const stats = entries.find(e => e[0] === data.name)?.[1]
      return `${data.name}<br/>通过率: ${(data.value * 100).toFixed(1)}%<br/>通过: ${stats?.passedCases}/${stats?.totalCases}`
    }},
    grid: { left: '3%', right: '4%', bottom: '10%', containLabel: true },
    xAxis: {
      type: 'category',
      data: entries.map(e => e[0]),
      axisLabel: { rotate: entries.length > 4 ? 30 : 0, interval: 0 }
    },
    yAxis: {
      type: 'value',
      max: 1,
      axisLabel: { formatter: (val: number) => `${(val * 100).toFixed(0)}%` }
    },
    series: [{
      type: 'bar',
      data: entries.map(e => Number((e[1].passRate * 100).toFixed(1)) / 100),
      itemStyle: {
        color: (params: any) => params.value >= (currentReport.value?.threshold || 0.7) ? '#67c23a' : '#e6a23c'
      },
      barMaxWidth: 40,
      label: { show: true, position: 'top', formatter: (p: any) => `${(p.value * 100).toFixed(0)}%` }
    }]
  }
})

const trendChartOption = computed(() => {
  if (trendData.value.length === 0) return null
  const labels = trendData.value.map(r => `#${r.id}`)
  const passRates = trendData.value.map(r => Number((r.passRate * 100).toFixed(1)) / 100)
  const avgScores = trendData.value.map(r => Number(r.avgScore.toFixed(3)))
  return {
    tooltip: { trigger: 'axis' },
    legend: { data: ['通过率', '平均分'], top: 0 },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: labels },
    yAxis: [
      { type: 'value', name: '通过率', max: 1, axisLabel: { formatter: (v: number) => `${(v * 100).toFixed(0)}%` } },
      { type: 'value', name: '平均分', max: 1 }
    ],
    series: [
      {
        name: '通过率',
        type: 'line',
        data: passRates,
        smooth: true,
        itemStyle: { color: '#409eff' },
        lineStyle: { width: 2 },
        label: { show: true, formatter: (p: any) => `${(p.value * 100).toFixed(0)}%` }
      },
      {
        name: '平均分',
        type: 'line',
        yAxisIndex: 1,
        data: avgScores,
        smooth: true,
        itemStyle: { color: '#67c23a' },
        lineStyle: { width: 2, type: 'dashed' }
      }
    ]
  }
})

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

const loadTrend = async () => {
  if (!selectedDataset.value) {
    trendData.value = []
    return
  }
  try {
    const res = await getEvaluationTrend(selectedDataset.value.id)
    trendData.value = res.data || []
  } catch {
    trendData.value = []
  }
}

const selectDataset = (row: EvaluationDataset) => {
  selectedDataset.value = row
  currentReport.value = null
  loadRuns()
  loadTrend()
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
  await ElMessageBox.confirm(t('evaluation.deleteDatasetConfirm'), t('common.tip'), { type: 'warning' })
  await deleteEvaluationDataset(id)
  ElMessage.success('已删除')
  if (selectedDataset.value?.id === id) {
    selectedDataset.value = null
    currentReport.value = null
    trendData.value = []
  }
  await loadDatasets()
}

const handleRunEvaluation = async () => {
  if (!selectedDataset.value) return
  runLoading.value = true
  try {
    if (asyncMode.value) {
      const res = await runEvaluationAsync(selectedDataset.value.id, runForm)
      asyncRunId.value = res.data
      asyncRunning.value = true
      ElMessage.success(`异步评估已提交 (Run #${res.data})`)
      startAsyncPolling(res.data)
    } else {
      const res = await runEvaluation(selectedDataset.value.id, runForm)
      currentReport.value = res.data
      ElMessage.success('评估完成')
      await loadRuns()
      await loadTrend()
    }
  } finally {
    runLoading.value = false
  }
}

const startAsyncPolling = (runId: number) => {
  if (asyncPollTimer) clearInterval(asyncPollTimer)
  asyncPollTimer = setInterval(async () => {
    try {
      const res = await listEvaluationRuns({ datasetId: selectedDataset.value?.id })
      const run = res.data?.find(r => r.id === runId)
      if (run && run.status !== 'RUNNING') {
        clearInterval(asyncPollTimer!)
        asyncPollTimer = null
        asyncRunning.value = false
        const reportRes = await getEvaluationRunReport(runId)
        currentReport.value = reportRes.data
        ElMessage.success(run.status === 'COMPLETED' ? '异步评估完成' : '异步评估失败')
        await loadRuns()
        await loadTrend()
      }
    } catch {
      // ignore polling errors
    }
  }, 2000)
}

const handleFileImport = async (file: File) => {
  try {
    await importEvaluationDataset(file)
    ElMessage.success('数据集导入成功')
    await loadDatasets()
  } catch {
    // error handled by interceptor
  }
  return false // prevent default upload
}

// A/B compare
const compareDialogVisible = ref(false)
const compareData = ref<Record<string, any> | null>(null)
const compareFirstRun = ref<number | null>(null)

const startCompare = async (runId: number) => {
  if (compareFirstRun.value === null) {
    compareFirstRun.value = runId
    ElMessage.info(`已选择 Run #${runId} 作为基准，请点击另一条记录进行对比`)
  } else if (compareFirstRun.value === runId) {
    ElMessage.warning('不能与自身对比')
  } else {
    try {
      const res = await compareEvaluationRuns(compareFirstRun.value, runId)
      compareData.value = res.data
      compareDialogVisible.value = true
    } finally {
      compareFirstRun.value = null
    }
  }
}

const loadReport = async (id: number) => {
  const res = await getEvaluationRunReport(id)
  currentReport.value = res.data
}

onMounted(() => {
  loadDatasets()
  loadRuns()
  listAgents({ pageNum: 1, pageSize: 100 }).then(res => { agents.value = res.data.list || [] }).catch(() => {})
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
.chart-title { font-size: 14px; font-weight: 600; color: #606266; margin-bottom: 8px; }
.result-table { margin-top: 16px; }
.async-tag { margin-left: 12px; }
.yaml-input :deep(textarea) { font-family: Consolas, Monaco, monospace; }
@media (max-width: 900px) {
  .evaluation-page :deep(.el-col) { max-width: 100%; flex: 0 0 100%; }
}
</style>
