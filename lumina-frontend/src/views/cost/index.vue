<template>
  <div class="cost-page">
    <PageHeader title="成本仪表盘" description="Agent 执行 Token 消费与费用汇总" />

    <el-row :gutter="16" v-loading="loading">
      <el-col :span="6">
        <el-card shadow="hover">
          <el-statistic title="完成任务数" :value="summary?.taskCount ?? 0" />
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <el-statistic title="总 Token" :value="summary?.totalTokens ?? 0" />
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <el-statistic title="输入 Token" :value="summary?.totalPromptTokens ?? 0" />
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <el-statistic title="输出 Token" :value="summary?.totalCompletionTokens ?? 0" />
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="cost-card">
      <div class="total-cost">
        <span class="label">总费用（{{ summary?.currency || 'CNY' }}）</span>
        <span class="value">¥ {{ summary?.totalCost?.toFixed(4) ?? '0.0000' }}</span>
      </div>
    </el-card>

    <!-- 趋势图表 -->
    <el-card shadow="never" class="trend-card">
      <template #header>
        <div class="trend-header">
          <span>消费趋势</span>
          <el-radio-group v-model="trendDays" size="small" @change="loadTrend">
            <el-radio-button :value="7">近 7 天</el-radio-button>
            <el-radio-button :value="30">近 30 天</el-radio-button>
            <el-radio-button :value="90">近 90 天</el-radio-button>
          </el-radio-group>
        </div>
      </template>
      <div v-loading="trendLoading" class="chart-container">
        <v-chart v-if="trendOption" :option="trendOption" autoresize style="height: 320px" />
        <el-empty v-else description="暂无趋势数据" :image-size="60" />
      </div>
    </el-card>

    <el-card shadow="never" class="top-card">
      <template #header>Top Agent 消费</template>
      <el-table :data="summary?.topAgents || []" stripe>
        <el-table-column prop="agentId" label="Agent ID" width="120" />
        <el-table-column prop="tokens" label="Token 用量" />
        <el-table-column label="费用（元）">
          <template #default="{ row }">¥ {{ row.cost.toFixed(4) }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && (!summary?.topAgents || summary.topAgents.length === 0)"
                description="暂无消费数据" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart, BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent, DataZoomComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import PageHeader from '@/components/common/PageHeader.vue'
import { getCostSummary, getCostTrend, type CostSummary, type CostTrendPoint } from '@/api/modules/cost'

use([CanvasRenderer, LineChart, BarChart, GridComponent, TooltipComponent, LegendComponent, DataZoomComponent])

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
        itemStyle: { color: '#409eff' },
        barMaxWidth: 30
      },
      {
        name: '费用 (元)',
        type: 'line',
        yAxisIndex: 1,
        data: costs,
        smooth: true,
        itemStyle: { color: '#67c23a' },
        lineStyle: { width: 2 },
        areaStyle: { opacity: 0.1 }
      },
      {
        name: '任务数',
        type: 'line',
        data: taskCounts,
        smooth: true,
        itemStyle: { color: '#e6a23c' },
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
.total-cost .label { font-size: 14px; color: #909399; margin-right: 12px; }
.total-cost .value { font-size: 28px; font-weight: 700; color: #409eff; }

.trend-card { margin-top: 16px; }
.trend-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.chart-container { min-height: 320px; }

.top-card { margin-top: 16px; }
</style>
