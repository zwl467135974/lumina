<template>
  <div class="dashboard-page">
    <PageHeader :title="t('dashboard.title')" :description="t('dashboard.description')" />

    <el-row :gutter="16" class="stat-row">
      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="hover" class="stat-card" @click="router.push('/agent/list')">
          <div class="stat-icon agent-icon"><el-icon :size="28"><Monitor /></el-icon></div>
          <el-statistic :title="t('dashboard.agentCount')" :value="stats.agentCount" />
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="hover" class="stat-card" @click="router.push('/agent/tasks')">
          <div class="stat-icon task-icon"><el-icon :size="28"><List /></el-icon></div>
          <el-statistic :title="t('dashboard.todayTasks')" :value="stats.todayTasks" />
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon token-icon"><el-icon :size="28"><DataLine /></el-icon></div>
          <el-statistic :title="t('dashboard.totalTokens')" :value="stats.totalTokens" />
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon cost-icon"><el-icon :size="28"><Money /></el-icon></div>
          <el-statistic :title="t('dashboard.totalCost')" :value="stats.totalCost" :precision="4" prefix="¥ " />
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :xs="24" :md="16">
        <el-card shadow="never">
          <template #header>{{ t('dashboard.recentTasks') }}</template>
          <el-table v-loading="loading" :data="recentTasks" stripe size="small">
            <el-table-column prop="taskUuid" label="UUID" width="100">
              <template #default="{ row }">{{ row.taskUuid?.substring(0, 8) }}…</template>
            </el-table-column>
            <el-table-column prop="agentId" label="Agent" width="70" />
            <el-table-column prop="inputText" label="Input" min-width="200" show-overflow-tooltip />
            <el-table-column label="Status" width="90">
              <template #default="{ row }">
                <el-tag :type="taskStatusType(row.status)" size="small">{{ taskStatusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="Time" width="150" />
          </el-table>
          <el-empty v-if="!loading && recentTasks.length === 0" :description="t('common.noData')" :image-size="40" />
        </el-card>
      </el-col>

      <el-col :xs="24" :md="8">
        <el-card shadow="never">
          <template #header>{{ t('dashboard.quickActions') }}</template>
          <div class="quick-actions">
            <el-button type="primary" size="large" @click="router.push('/agent/create')">
              <el-icon><Plus /></el-icon> {{ t('dashboard.createAgent') }}
            </el-button>
            <el-button type="success" size="large" @click="router.push('/knowledge')">
              <el-icon><Upload /></el-icon> {{ t('dashboard.uploadDoc') }}
            </el-button>
            <el-button type="warning" size="large" @click="router.push('/evaluation')">
              <el-icon><DataAnalysis /></el-icon> {{ t('dashboard.newEvaluation') }}
            </el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Monitor, List, DataLine, Money, Plus, Upload, DataAnalysis } from '@element-plus/icons-vue'
import PageHeader from '@/components/common/PageHeader.vue'
import { listAgents } from '@/api/modules/agent'
import { listAgentTasks, type AgentTaskVO } from '@/api/modules/agent'
import { getCostSummary } from '@/api/modules/cost'

const { t } = useI18n()
const router = useRouter()
const loading = ref(false)
const recentTasks = ref<AgentTaskVO[]>([])

const stats = reactive({
  agentCount: 0,
  todayTasks: 0,
  totalTokens: 0,
  totalCost: 0
})

const taskStatusType = (s: string) => ({ QUEUED: 'info', RUNNING: 'warning', COMPLETED: 'success', FAILED: 'danger', CANCELLED: 'info' }[s] || 'info')
const taskStatusLabel = (s: string) => t(`task.${s?.toLowerCase()}`) || s

const loadDashboard = async () => {
  loading.value = true
  try {
    const [agentsRes, tasksRes, costRes] = await Promise.allSettled([
      listAgents({ pageNum: 1, pageSize: 1 }),
      listAgentTasks({ pageNum: 1, pageSize: 5 }),
      getCostSummary()
    ])

    if (agentsRes.status === 'fulfilled') stats.agentCount = agentsRes.value.data.total || 0
    if (tasksRes.status === 'fulfilled') {
      recentTasks.value = tasksRes.value.data.list || []
      stats.todayTasks = tasksRes.value.data.total || 0
    }
    if (costRes.status === 'fulfilled') {
      const summary = costRes.value.data as any
      stats.totalTokens = summary.totalTokens || 0
      stats.totalCost = summary.totalCost || 0
    }
  } finally {
    loading.value = false
  }
}

onMounted(loadDashboard)
</script>

<style scoped lang="scss">
.dashboard-page { padding: 0; }
.stat-row { margin-bottom: 16px; }

.stat-card {
  cursor: pointer;
  transition: transform 0.2s;
  &:hover { transform: translateY(-2px); }

  :deep(.el-statistic__content) { font-size: 28px; font-weight: 700; }
}

.stat-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border-radius: 8px;
  margin-bottom: 12px;

  &.agent-icon { background: var(--el-color-primary-light-8); color: var(--el-color-primary); }
  &.task-icon { background: var(--el-color-success-light-8); color: var(--el-color-success); }
  &.token-icon { background: var(--el-color-warning-light-8); color: var(--el-color-warning); }
  &.cost-icon { background: var(--el-color-danger-light-8); color: var(--el-color-danger); }
}

.quick-actions {
  display: flex;
  flex-direction: column;
  gap: 12px;

  .el-button { width: 100%; justify-content: flex-start; }
}

@media (max-width: 768px) {
  .stat-card { margin-bottom: 8px; }
}
</style>
