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

          <el-input v-model="queryName" :placeholder="t('evaluation.searchByName')" clearable class="search-input" @change="loadDatasets" />
          <el-table v-loading="datasetLoading" :data="datasets" stripe @row-click="selectDataset">
            <el-table-column prop="name" :label="t('evaluation.name')" min-width="160" />
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
          <template #header>{{ t('evaluation.executeEval') }}</template>
          <el-empty v-if="!selectedDataset" :description="t('evaluation.pleaseSelectDataset')" />
          <div v-else>
            <el-descriptions :column="2" border size="small" class="dataset-summary">
              <el-descriptions-item :label="t('evaluation.datasetName')">{{ selectedDataset.name }}</el-descriptions-item>
              <el-descriptions-item :label="t('evaluation.caseCount')">{{ selectedDataset.cases.length }}</el-descriptions-item>
              <el-descriptions-item :label="t('agent.type')">{{ selectedDataset.agentType || '-' }}</el-descriptions-item>
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
                <el-switch v-model="asyncMode" :active-text="t('evaluation.asyncHint')" />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" :loading="runLoading" @click="handleRunEvaluation">{{ t('evaluation.runEvaluation') }}</el-button>
                <el-tag v-if="asyncRunning" type="warning" class="async-tag">{{ t('evaluation.asyncRunning', { id: asyncRunId }) }}</el-tag>
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
        <el-col :span="6"><el-statistic :title="t('evaluation.passCases')" :value="`${currentReport.passedCases}/${currentReport.totalCases}`" /></el-col>
        <el-col :span="6"><el-statistic :title="t('evaluation.avgScore')" :value="currentReport.avgScore" :precision="3" /></el-col>
        <el-col :span="6"><el-statistic :title="t('evaluation.avgLatency') + '(ms)'" :value="currentReport.avgLatencyMs" /></el-col>
        <el-col :span="6"><el-statistic :title="t('evaluation.totalTokens')" :value="currentReport.totalTokens || 0" /></el-col>
      </el-row>

      <el-row :gutter="16">
        <el-col :span="12">
          <div class="chart-title">{{ t('evaluation.categoryStats') }}</div>
          <v-chart v-if="categoryChartOption" :option="categoryChartOption" autoresize style="height: 280px" />
          <el-empty v-else :description="t('common.noData')" :image-size="40" />
        </el-col>
        <el-col :span="12">
          <div class="chart-title">{{ t('evaluation.trend') }}</div>
          <v-chart v-if="trendChartOption" :option="trendChartOption" autoresize style="height: 280px" />
          <el-empty v-else :description="t('evaluation.noTrendData')" :image-size="40" />
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
            <el-tag :type="row.passed ? 'success' : 'danger'">{{ row.passed ? t('evaluation.resultPass') : t('evaluation.resultFail') }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never" class="panel-card">
      <template #header>{{ t('evaluation.history') }}</template>
      <el-table v-loading="runListLoading" :data="runs" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="datasetName" :label="t('evaluation.datasetName')" min-width="150" />
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
        <el-table-column :label="t('evaluation.avgScore')" width="100">
          <template #default="{ row }">{{ Number(row.avgScore).toFixed(3) }}</template>
        </el-table-column>
        <el-table-column prop="createTime" :label="t('common.createTime')" width="180" />
        <el-table-column :label="t('common.actions')" min-width="210">
          <template #default="{ row }">
            <div class="eval-actions">
              <el-button link type="primary" @click="loadReport(row.id)">{{ t('common.view') }}</el-button>
              <el-button link type="info" @click="startCompare(row.id)">{{ t('evaluation.compare') }}</el-button>
              <el-button link type="warning" @click="handleMarkBaseline(row.id)">{{ t('evaluation.markBaseline') }}</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="datasetDialogVisible" :title="t('evaluation.createDataset')" width="760px">
      <el-form :model="datasetForm" label-width="110px">
        <el-form-item :label="t('evaluation.name')" required>
          <el-input v-model="datasetForm.name" :placeholder="t('evaluation.datasetPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('agent.type')">
          <el-input v-model="datasetForm.agentType" :placeholder="t('evaluation.agentTypePlaceholder')" />
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
              :value-style="{ color: compareData.passRateDiff >= 0 ? 'var(--lumina-success)' : 'var(--lumina-danger)' }" />
          </el-col>
          <el-col :span="6">
            <el-statistic :title="t('evaluation.scoreChange')"
              :value="compareData.avgScoreDiff.toFixed(4)"
              :value-style="{ color: compareData.avgScoreDiff >= 0 ? 'var(--lumina-success)' : 'var(--lumina-danger)' }" />
          </el-col>
          <el-col :span="4"><el-statistic :title="t('evaluation.improved')" :value="compareData.improved" /></el-col>
          <el-col :span="4"><el-statistic :title="t('evaluation.regressed')" :value="compareData.regressed" /></el-col>
          <el-col :span="4"><el-statistic :title="t('evaluation.unchanged')" :value="compareData.unchanged" /></el-col>
        </el-row>
        <el-table :data="compareData.cases" stripe max-height="400">
          <el-table-column prop="caseId" :label="t('evaluation.caseId')" width="120" />
          <el-table-column prop="category" :label="t('evaluation.category')" width="100" />
          <el-table-column :label="t('evaluation.scoreA')" width="80">
            <template #default="{ row }">{{ row.scoreA.toFixed(3) }}</template>
          </el-table-column>
          <el-table-column :label="t('evaluation.scoreB')" width="80">
            <template #default="{ row }">{{ row.scoreB.toFixed(3) }}</template>
          </el-table-column>
          <el-table-column :label="t('evaluation.change')" width="80">
            <template #default="{ row }">
              <span :style="{ color: row.scoreDiff > 0 ? 'var(--lumina-success)' : row.scoreDiff < 0 ? 'var(--lumina-danger)' : 'var(--lumina-text-secondary)' }">
                {{ row.scoreDiff > 0 ? '+' : '' }}{{ row.scoreDiff.toFixed(3) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column :label="t('evaluation.trend')" width="80">
            <template #default="{ row }">
              <el-tag :type="row.trend === 'IMPROVED' ? 'success' : row.trend === 'REGRESSED' ? 'danger' : 'info'" size="small">
                {{ row.trend === 'IMPROVED' ? '↑' : row.trend === 'REGRESSED' ? '↓' : '→' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-dialog>

    <!-- 回归测试面板 -->
    <el-card class="panel-card">
      <template #header>
        <div class="card-header">
          <span>{{ t('evaluation.batchRegressionPanel') }}</span>
          <el-button size="small" @click="regressionDialogVisible = true">{{ t('evaluation.startBatchRegression') }}</el-button>
        </div>
      </template>

      <!-- Prompt 版本对比 -->
      <el-form :inline="true" class="prompt-compare-form">
        <el-form-item :label="t('evaluation.promptName')">
          <el-input v-model="promptCompare.name" :placeholder="t('evaluation.promptNamePlaceholder')" style="width: 150px" />
        </el-form-item>
        <el-form-item :label="t('evaluation.versionA')">
          <el-input-number v-model="promptCompare.vA" :min="1" controls-position="right" style="width: 90px" />
        </el-form-item>
        <el-form-item :label="t('evaluation.versionB')">
          <el-input-number v-model="promptCompare.vB" :min="1" controls-position="right" style="width: 90px" />
        </el-form-item>
        <el-form-item>
          <el-button size="small" :loading="promptCompareLoading" @click="doPromptCompare">{{ t('evaluation.compareDiff') }}</el-button>
        </el-form-item>
      </el-form>

      <div v-if="promptDiff" class="prompt-diff-result">
        <el-tag type="info">{{ t('evaluation.totalDiff', { n: promptDiff.totalChanges }) }}</el-tag>
        <div v-for="d in promptDiff.diffLines" :key="d.line" class="diff-line">
          <span class="diff-line-num">L{{ d.line }}</span>
          <el-tag size="small" :type="d.type === 'ADDED' ? 'success' : d.type === 'REMOVED' ? 'danger' : 'warning'">{{ d.type }}</el-tag>
          <span v-if="d.type === 'MODIFIED'" class="diff-content">
            <span class="diff-old">{{ d.oldContent }}</span> → <span class="diff-new">{{ d.newContent }}</span>
          </span>
          <span v-else class="diff-content">{{ d.content }}</span>
        </div>
      </div>
    </el-card>

    <!-- 批量回归对话框 -->
    <el-dialog v-model="regressionDialogVisible" :title="t('evaluation.batchRegression')" width="550px">
      <el-form :model="regressionForm" label-width="110px">
        <el-form-item :label="t('evaluation.datasetIds')">
          <el-input v-model="regressionForm.datasetIdsStr" :placeholder="t('evaluation.datasetIdsPlaceholder')" />
        </el-form-item>
        <el-form-item label="Agent ID">
          <el-input-number v-model="regressionForm.agentId" :min="1" controls-position="right" />
        </el-form-item>
        <el-form-item :label="t('evaluation.scoringMethod')">
          <el-select v-model="regressionForm.scoringMethod" style="width: 100%">
            <el-option :label="t('evaluation.exactMatch')" value="EXACT_MATCH" />
            <el-option :label="t('evaluation.contains')" value="CONTAINS" />
            <el-option :label="t('evaluation.semanticSimilarity')" value="SEMANTIC_SIMILARITY" />
            <el-option label="LLM Judge" value="LLM_JUDGE" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('evaluation.threshold')">
          <el-slider v-model="regressionForm.threshold" :min="0" :max="1" :step="0.05" show-input style="width: 100%" />
        </el-form-item>
        <el-form-item :label="t('evaluation.promptName')">
          <el-input v-model="regressionForm.promptName" :placeholder="t('evaluation.optional')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="regressionDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="regressionLoading" @click="doBatchRegression">{{ t('evaluation.executeRegression') }}</el-button>
      </template>
    </el-dialog>

    <!-- 回归结果对话框 -->
    <el-dialog v-model="regressionResultVisible" :title="t('evaluation.regressionReport')" width="600px">
      <div v-if="regressionResult">
        <el-descriptions :column="2" border>
          <el-descriptions-item :label="t('evaluation.datasetCount')">{{ regressionResult.totalDatasets }}</el-descriptions-item>
          <el-descriptions-item :label="t('evaluation.completedCount')">{{ regressionResult.completedDatasets }}</el-descriptions-item>
          <el-descriptions-item :label="t('evaluation.passCases')">{{ regressionResult.totalPassedCases }}</el-descriptions-item>
          <el-descriptions-item :label="t('evaluation.regressionCases')">{{ regressionResult.totalRegressedCases }}</el-descriptions-item>
          <el-descriptions-item :label="t('evaluation.result')">
            <el-tag :type="regressionResult.pass ? 'success' : 'danger'">
              {{ regressionResult.pass ? t('evaluation.resultPass') : t('evaluation.hasRegression') }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>
        <el-table v-if="regressionResult.datasetResults" :data="regressionResult.datasetResults" size="small" style="margin-top: 12px">
          <el-table-column prop="datasetId" :label="t('evaluation.datasetName')" width="80" />
          <el-table-column prop="passRate" :label="t('evaluation.passRate')" width="80" />
          <el-table-column prop="passedCases" :label="t('evaluation.passed')" width="60" />
          <el-table-column prop="regressed" :label="t('evaluation.regress')" width="60" />
          <el-table-column prop="status" :label="t('common.status')" />
        </el-table>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">

defineOptions({ name: 'Evaluation' })
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
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
  runBatchRegression,
  markBaseline,
  comparePromptVersions,
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
      return t('evaluation.chartTooltip', { name: data.name, rate: (data.value * 100).toFixed(1), passed: stats?.passedCases, total: stats?.totalCases })
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
        color: (params: any) => params.value >= (currentReport.value?.threshold || 0.7) ? 'var(--lumina-success)' : 'var(--lumina-warning)'
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
    legend: { data: [t('evaluation.chartPassRate'), t('evaluation.chartAvgScore')], top: 0 },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: labels },
    yAxis: [
      { type: 'value', name: t('evaluation.chartPassRate'), max: 1, axisLabel: { formatter: (v: number) => `${(v * 100).toFixed(0)}%` } },
      { type: 'value', name: t('evaluation.chartAvgScore'), max: 1 }
    ],
    series: [
      {
        name: t('evaluation.chartPassRate'),
        type: 'line',
        data: passRates,
        smooth: true,
        itemStyle: { color: 'var(--lumina-primary)' },
        lineStyle: { width: 2 },
        label: { show: true, formatter: (p: any) => `${(p.value * 100).toFixed(0)}%` }
      },
      {
        name: t('evaluation.chartAvgScore'),
        type: 'line',
        yAxisIndex: 1,
        data: avgScores,
        smooth: true,
        itemStyle: { color: 'var(--lumina-success)' },
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
    ElMessage.warning(t('evaluation.nameAndYamlRequired'))
    return
  }
  savingDataset.value = true
  try {
    await createEvaluationDataset({ ...datasetForm })
    ElMessage.success(t('common.createSuccess'))
    datasetDialogVisible.value = false
    await loadDatasets()
  } finally {
    savingDataset.value = false
  }
}

const handleDeleteDataset = async (id: number) => {
  await ElMessageBox.confirm(t('evaluation.deleteDatasetConfirm'), t('common.tip'), { type: 'warning' })
  await deleteEvaluationDataset(id)
  ElMessage.success(t('common.deleteSuccess'))
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
      ElMessage.success(t('evaluation.asyncSubmitted', { id: res.data }))
      startAsyncPolling(res.data)
    } else {
      const res = await runEvaluation(selectedDataset.value.id, runForm)
      currentReport.value = res.data
      ElMessage.success(t('evaluation.evalDone'))
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
        ElMessage.success(run.status === 'COMPLETED' ? t('evaluation.asyncEvalDone') : t('evaluation.asyncEvalFailed'))
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
    ElMessage.success(t('evaluation.importSuccess'))
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
    ElMessage.info(t('evaluation.selectBaselineHint', { id: runId }))
  } else if (compareFirstRun.value === runId) {
    ElMessage.warning(t('evaluation.cannotSelfCompare'))
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

onUnmounted(() => {
  if (asyncPollTimer) {
    clearInterval(asyncPollTimer)
    asyncPollTimer = null
  }
})

// ==================== 批量回归 & Prompt 版本对比 ====================
const regressionDialogVisible = ref(false)
const regressionLoading = ref(false)
const regressionResultVisible = ref(false)
const regressionResult = ref<Record<string, any> | null>(null)
const regressionForm = reactive({
  datasetIdsStr: '',
  agentId: 1,
  scoringMethod: 'EXACT_MATCH' as ScoringMethod,
  threshold: 0.7,
  promptName: ''
})

const doBatchRegression = async () => {
  const ids = regressionForm.datasetIdsStr.split(',').map(s => parseInt(s.trim())).filter(n => !isNaN(n))
  if (ids.length === 0) {
    ElMessage.warning(t('evaluation.datasetIdsRequired'))
    return
  }
  regressionLoading.value = true
  try {
    const res = await runBatchRegression({
      datasetIds: ids,
      agentId: regressionForm.agentId,
      scoringMethod: regressionForm.scoringMethod,
      threshold: regressionForm.threshold,
      promptName: regressionForm.promptName || undefined
    })
    regressionResult.value = res.data
    regressionResultVisible.value = true
    regressionDialogVisible.value = false
    ElMessage.success(t('evaluation.regressionDone'))
  } catch (e: any) {
    ElMessage.error(e.message || t('evaluation.regressionFailed'))
  } finally {
    regressionLoading.value = false
  }
}

// 标记基线
const handleMarkBaseline = async (runId: number) => {
  try {
    await ElMessageBox.confirm(t('evaluation.markBaselineConfirm', { id: runId }), t('evaluation.markBaselineTitle'), { type: 'warning' })
    await markBaseline(runId)
    ElMessage.success(t('evaluation.baselineMarked'))
    loadRuns()
  } catch { /* cancelled */ }
}

// Prompt 版本对比
const promptCompare = reactive({ name: 'react', vA: 1, vB: 2 })
const promptCompareLoading = ref(false)
const promptDiff = ref<{ totalChanges: number; diffLines: Array<Record<string, any>> } | null>(null)

const doPromptCompare = async () => {
  promptCompareLoading.value = true
  try {
    const res = await comparePromptVersions(promptCompare.name, promptCompare.vA, promptCompare.vB)
    promptDiff.value = res.data
  } catch (e: any) {
    ElMessage.error(e.message || t('evaluation.compareFailed'))
    promptDiff.value = null
  } finally {
    promptCompareLoading.value = false
  }
}
</script>

<style scoped>
.evaluation-page { padding: 0; }
.eval-actions {
  display: inline-flex;
  align-items: center;
  gap: var(--lumina-spacing-xs);
  white-space: nowrap;
}
.eval-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}
.panel-card { margin-bottom: 16px; }
.card-header { display: flex; align-items: center; justify-content: space-between; }
.search-input { margin-bottom: 12px; }
.dataset-summary { margin-bottom: 16px; }
.run-form { margin-top: 16px; }
.metric-row { margin-bottom: 16px; }
.chart-title { font-size: 14px; font-weight: 600; color: var(--lumina-text-secondary); margin-bottom: 8px; }
.result-table { margin-top: 16px; }
.async-tag { margin-left: 12px; }
.yaml-input :deep(textarea) { font-family: Consolas, Monaco, monospace; }
.prompt-compare-form { margin-bottom: 12px; }
.prompt-diff-result { margin-top: 12px; }
.diff-line { display: flex; align-items: center; gap: 8px; padding: 4px 0; font-size: 13px; }
.diff-line-num { color: var(--el-text-color-secondary); min-width: 40px; }
.diff-content { flex: 1; }
.diff-old { color: var(--el-color-danger); text-decoration: line-through; }
.diff-new { color: var(--el-color-success); }
@media (max-width: 900px) {
  .evaluation-page :deep(.el-col) { max-width: 100%; flex: 0 0 100%; }
}

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
