<template>
  <div class="trace-page">
    <!-- 列表区 -->
    <el-card v-if="!selectedTrace">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>推理追踪</span>
          <div>
            <el-select v-model="filterStatus" placeholder="状态" clearable size="small" style="width: 120px; margin-right: 8px">
              <el-option label="成功" value="SUCCESS" />
              <el-option label="失败" value="FAILED" />
              <el-option label="执行中" value="RUNNING" />
            </el-select>
            <el-button size="small" @click="loadData">刷新</el-button>
          </div>
        </div>
      </template>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="agentName" label="Agent" width="120" />
        <el-table-column prop="inputText" label="输入" min-width="200" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Token" width="80" align="center">
          <template #default="{ row }">{{ row.totalTokens || 0 }}</template>
        </el-table-column>
        <el-table-column label="耗时" width="90" align="center">
          <template #default="{ row }">{{ row.durationMs ? (row.durationMs + 'ms') : '-' }}</template>
        </el-table-column>
        <el-table-column prop="startedAt" label="开始时间" width="180" />
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button size="small" link @click="showDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pagination.pageNum"
        v-model:page-size="pagination.pageSize"
        :total="pagination.total"
        @current-change="loadData"
        style="margin-top: 16px; justify-content: flex-end"
      />
    </el-card>

    <!-- 详情区 -->
    <el-card v-else>
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>推理链详情: {{ selectedTrace.agentName }}</span>
          <el-button size="small" @click="selectedTrace = null">返回列表</el-button>
        </div>
      </template>

      <!-- 概况卡片 -->
      <el-row :gutter="16" style="margin-bottom: 16px">
        <el-col :span="6">
          <LumStatCard label="状态" :value="statusLabel(selectedTrace.status)" :color="selectedTrace.status === 'SUCCESS' ? 'success' : 'danger'" />
        </el-col>
        <el-col :span="6">
          <LumStatCard label="Token 总计" :value="selectedTrace.totalTokens || 0" color="primary" />
        </el-col>
        <el-col :span="6">
          <LumStatCard label="耗时" :value="selectedTrace.durationMs ? (selectedTrace.durationMs + 'ms') : '-'" color="warning" />
        </el-col>
        <el-col :span="6">
          <LumStatCard label="步骤数" :value="(selectedTrace.steps?.length) || 0" color="info" />
        </el-col>
      </el-row>

      <!-- 输入输出 -->
      <el-descriptions :column="1" border style="margin-bottom: 16px">
        <el-descriptions-item label="输入">{{ selectedTrace.inputText || '-' }}</el-descriptions-item>
        <el-descriptions-item label="输出">{{ selectedTrace.outputText || '-' }}</el-descriptions-item>
      </el-descriptions>

      <!-- 步骤时间线 -->
      <h4 style="margin-bottom: 12px">推理步骤</h4>
      <el-timeline v-if="selectedTrace.steps && selectedTrace.steps.length > 0">
        <el-timeline-item
          v-for="step in selectedTrace.steps"
          :key="step.seq"
          :type="stepTagType(step.type)"
          :timestamp="step.durationMs + 'ms'"
          placement="top"
        >
          <el-card shadow="never">
            <div style="display: flex; align-items: center; gap: 8px; margin-bottom: 8px">
              <el-tag :type="stepTagType(step.type)" size="small">{{ stepLabel(step.type) }}</el-tag>
              <span style="font-weight: 600">{{ step.name }}</span>
              <span v-if="step.promptTokens" style="color: var(--el-text-color-secondary); font-size: 12px">
                {{ step.promptTokens }} + {{ step.completionTokens }} Token
              </span>
            </div>
            <div v-if="step.input" style="margin-bottom: 4px">
              <span style="color: var(--el-text-color-secondary)">输入: </span>
              <span style="font-family: monospace; font-size: 12px">{{ step.input }}</span>
            </div>
            <div v-if="step.output">
              <span style="color: var(--el-text-color-secondary)">输出: </span>
              <span style="font-family: monospace; font-size: 12px">{{ step.output }}</span>
            </div>
          </el-card>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="暂无推理步骤数据" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { listAgentTraces, getAgentTraceDetail } from '@/api/modules/agent-trace'
import type { AgentTraceVO } from '@/types/agent-trace'
import { STEP_TYPE_TAG, TRACE_STATUS_TAG } from '@/types/agent-trace'
import LumStatCard from '@/components/common/LumStatCard.vue'

// === 列表状态 ===
const loading = ref(false)
const tableData = ref<AgentTraceVO[]>([])
const pagination = reactive({ pageNum: 1, pageSize: 20, total: 0 })
const filterStatus = ref<string>('')

// === 详情状态 ===
const selectedTrace = ref<AgentTraceVO | null>(null)

// === 加载列表 ===
const loadData = async () => {
  loading.value = true
  try {
    const res = await listAgentTraces({
      status: filterStatus.value || undefined,
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize
    })
    tableData.value = res.data.list || []
    pagination.total = res.data.total || 0
  } finally {
    loading.value = false
  }
}

// === 查看详情 ===
const showDetail = async (row: AgentTraceVO) => {
  const res = await getAgentTraceDetail(row.traceUuid)
  const trace = res.data
  // steps 从后端返回的是 JSON 字符串，需要解析为数组
  if (trace.steps && typeof trace.steps === 'string') {
    try {
      ;(trace as any).steps = JSON.parse(trace.steps as any)
    } catch {
      ;(trace as any).steps = []
    }
  }
  selectedTrace.value = trace
}

// === 标签辅助 ===
const statusLabel = (status: string) => TRACE_STATUS_TAG[status]?.label || status
const statusTagType = (status: string) => TRACE_STATUS_TAG[status]?.color || 'info'
const stepLabel = (type: string) => STEP_TYPE_TAG[type]?.label || type
const stepTagType = (type: string) => STEP_TYPE_TAG[type]?.color || 'info'

onMounted(() => loadData())
</script>

<style scoped>
.trace-page {
  padding: 0;
}
</style>
