<template>
  <div class="cost-page">
    <PageHeader :title="t('cost.title')" :description="t('cost.description')" />

    <el-row :gutter="16" v-loading="loading">
      <el-col :span="6">
        <el-card shadow="hover">
          <el-statistic :title="t('cost.taskCount')" :value="summary?.taskCount ?? 0" />
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <el-statistic :title="t('cost.totalTokens')" :value="summary?.totalTokens ?? 0" />
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <el-statistic :title="t('cost.inputTokens')" :value="summary?.totalPromptTokens ?? 0" />
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <el-statistic :title="t('cost.outputTokens')" :value="summary?.totalCompletionTokens ?? 0" />
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="cost-card">
      <div class="total-cost">
        <span class="label">{{ t('cost.totalCost') }}（{{ summary?.currency || 'CNY' }}）</span>
        <span class="value">¥ {{ summary?.totalCost?.toFixed(4) ?? '0.0000' }}</span>
      </div>
    </el-card>

    <!-- 趋势图表 -->
    <el-card shadow="never" class="trend-card">
      <template #header>
        <div class="trend-header">
          <span>{{ t('cost.trend') }}</span>
          <el-radio-group v-model="trendDays" size="small" @change="loadTrend">
            <el-radio-button :value="7">{{ t('cost.days7') }}</el-radio-button>
            <el-radio-button :value="30">{{ t('cost.days30') }}</el-radio-button>
            <el-radio-button :value="90">{{ t('cost.days90') }}</el-radio-button>
          </el-radio-group>
        </div>
      </template>
      <div v-loading="trendLoading" class="chart-container">
        <v-chart v-if="trendOption" :option="trendOption" autoresize style="height: 320px" />
        <el-empty v-else :description="t('common.noData')" :image-size="60" />
      </div>
    </el-card>

    <el-card shadow="never" class="top-card">
      <template #header>{{ t('cost.topAgents') }}</template>
      <el-table :data="summary?.topAgents || []" stripe>
        <el-table-column prop="agentId" label="Agent ID" width="120" />
        <el-table-column prop="tokens" :label="t('cost.tokenUsage')" />
        <el-table-column :label="t('cost.costYuan')">
          <template #default="{ row }">¥ {{ row.cost.toFixed(4) }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && (!summary?.topAgents || summary.topAgents.length === 0)"
                 :description="t('common.noData')" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart, BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent, DataZoomComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import PageHeader from '@/components/common/PageHeader.vue'
import { getCostSummary, getCostTrend, type CostSummary, type CostTrendPoint } from '@/api/modules/cost'

use([CanvasRenderer, LineChart, BarChart, GridComponent, TooltipComponent, LegendComponent, DataZoomComponent])

const { t } = useI18n()

const loading = ref(false)
const trendLoading = ref(false)
const summary = ref<CostSummary | null>(null)
const trendData = ref<CostTrendPoint[]>([])
const trendDays = ref(30)

const trendOption = computed(() => {
  if (trendData.value.length === 0) return null

  const dates = trendData.value.map(d => d.date)
  const tokens = trendData.value.map(d => d.totalTokens)
  const costs = trendData.value.map(d => d.cost)
  const taskCounts = trendData.value.map(d => d.taskCount)

  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' }
    },
    legend: {
      data: ['Token 用量', '费用 (元)', '任务数'],
      top: 0
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: dates,
      axisLabel: { rotate: dates.length > 15 ? 45 : 0 }
    },
    yAxis: [
      {
        type: 'value',
        name: 'Token',
        position: 'left'
      },
      {
        type: 'value',
        name: '费用 (元)',
        position: 'right'
      }
    ],
    series: [
      {
        name: 'Token 用量',
        type: 'bar',
        data: tokens,
        itemStyle: { color: 'var(--lumina-primary)' },
        barMaxWidth: 30
      },
      {
        name: '费用 (元)',
        type: 'line',
        yAxisIndex: 1,
        data: costs,
        smooth: true,
        itemStyle: { color: 'var(--lumina-success)' },
        lineStyle: { width: 2 },
        areaStyle: { opacity: 0.1 }
      },
      {
        name: '任务数',
        type: 'line',
        data: taskCounts,
        smooth: true,
        itemStyle: { color: 'var(--lumina-warning)' },
        lineStyle: { width: 1, type: 'dashed' }
      }
    ]
  }
})

const loadSummary = async () => {
  loading.value = true
  try {
    const res = await getCostSummary()
    summary.value = res.data
  } catch {
    summary.value = null
  } finally {
    loading.value = false
  }
}

const loadTrend = async () => {
  trendLoading.value = true
  try {
    const res = await getCostTrend(trendDays.value)
    trendData.value = res.data || []
  } catch {
    trendData.value = []
  } finally {
    trendLoading.value = false
  }
}

onMounted(() => {
  loadSummary()
  loadTrend()
})
</script>

<style scoped>
.cost-page { padding: 0; }
.cost-card { margin-top: 16px; text-align: center; }
.total-cost .label { font-size: 14px; color: var(--lumina-text-secondary); margin-right: 12px; }
.total-cost .value { font-size: 28px; font-weight: 700; color: var(--lumina-primary); }

.trend-card { margin-top: 16px; }
.trend-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.chart-container { min-height: 320px; }

.top-card { margin-top: 16px; }

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
