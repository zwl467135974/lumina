<template>
  <div class="tool-monitor">
    <div class="page-header">
      <h2>{{ t('monitor.title') }}</h2>
      <div class="actions">
        <el-button @click="loadAll" :loading="loading">{{ t('common.refresh') }}</el-button>
        <el-button type="danger" plain @click="handleClear">{{ t('monitor.clearRecords') }}</el-button>
      </div>
    </div>

    <!-- 调用统计 -->
    <el-card shadow="never" class="section-card">
      <template #header><span>{{ t('monitor.callStats') }}</span></template>
      <el-table :data="statsList" v-loading="loading" stripe size="small">
        <el-table-column prop="toolName" :label="t('monitor.toolName')" min-width="140" />
        <el-table-column prop="totalInvocations" :label="t('monitor.callCount')" width="100" sortable />
        <el-table-column :label="t('monitor.successRate')" width="110">
          <template #default="{ row }">
            <el-tag :type="rateType(row.successRate)" size="small">
              {{ (row.successRate * 100).toFixed(1) }}%
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('monitor.avgDuration')" width="100">
          <template #default="{ row }">{{ row.avgDurationMs.toFixed(0) }}ms</template>
        </el-table-column>
        <el-table-column :label="t('monitor.maxDuration')" width="100">
          <template #default="{ row }">{{ row.maxDurationMs }}ms</template>
        </el-table-column>
        <el-table-column :label="t('monitor.minDuration')" width="100">
          <template #default="{ row }">{{ row.minDurationMs }}ms</template>
        </el-table-column>
        <el-table-column prop="failureCount" :label="t('monitor.failCount')" width="80" />
      </el-table>
      <el-empty v-if="!loading && statsList.length === 0" :description="t('monitor.noRecords')" :image-size="60" />
    </el-card>

    <!-- 熔断状态 -->
    <el-card shadow="never" class="section-card">
      <template #header><span>{{ t('monitor.breakerStatus') }}</span></template>
      <el-table :data="breakerList" stripe size="small">
        <el-table-column prop="toolName" :label="t('monitor.toolName')" min-width="140" />
        <el-table-column :label="t('common.status')" width="120">
          <template #default="{ row }">
            <el-tag :type="row.open ? 'danger' : 'success'" size="small">
              {{ row.open ? t('monitor.statusOpen') : t('monitor.statusClosed') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="consecutiveFailures" :label="t('monitor.consecutiveFails')" width="120" />
      </el-table>
      <el-empty v-if="breakerList.length === 0" :description="t('monitor.noBreaker')" :image-size="60" />
    </el-card>

    <!-- 最近调用 -->
    <el-card shadow="never" class="section-card">
      <template #header><span>{{ t('monitor.recentCallsTitle', { n: invocations.length }) }}</span></template>
      <el-table :data="invocations" stripe size="small" max-height="400">
        <el-table-column :label="t('monitor.time')" width="170">
          <template #default="{ row }">{{ formatTime(row.timestamp) }}</template>
        </el-table-column>
        <el-table-column prop="toolName" :label="t('monitor.tool')" min-width="120" />
        <el-table-column :label="t('monitor.result')" width="80">
          <template #default="{ row }">
            <el-tag :type="row.success ? 'success' : 'danger'" size="small">
              {{ row.success ? t('monitor.resultSuccess') : t('monitor.resultFailed') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('monitor.duration')" width="80">
          <template #default="{ row }">{{ row.durationMs }}ms</template>
        </el-table-column>
        <el-table-column prop="error" :label="t('monitor.errorMsg')" min-width="200" show-overflow-tooltip />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getToolStats,
  getToolInvocations,
  getBreakerStates,
  clearToolInvocations,
  type ToolStats,
  type ToolInvocation,
  type BreakerState
} from '@/api/modules/tools'

const { t } = useI18n()

const loading = ref(false)
const statsMap = ref<Record<string, ToolStats>>({})
const invocations = ref<ToolInvocation[]>([])
const breakers = ref<Record<string, BreakerState>>({})

const statsList = computed(() => Object.values(statsMap.value))
const breakerList = computed(() => Object.values(breakers.value))

const rateType = (rate: number) => {
  if (rate >= 0.95) return 'success'
  if (rate >= 0.8) return 'warning'
  return 'danger'
}

const formatTime = (ts: number) => new Date(ts).toLocaleString()

const loadAll = async () => {
  loading.value = true
  try {
    const [s, i, b] = await Promise.all([
      getToolStats(),
      getToolInvocations(50),
      getBreakerStates()
    ])
    statsMap.value = s.data || {}
    invocations.value = i.data || []
    breakers.value = b.data || {}
  } catch (e: any) {
    ElMessage.error(e.message || t('monitor.loadFailed'))
  } finally {
    loading.value = false
  }
}

const handleClear = async () => {
  try {
    await ElMessageBox.confirm(t('monitor.clearConfirm'), t('common.tip'), { type: 'warning' })
    await clearToolInvocations()
    ElMessage.success(t('monitor.cleared'))
    loadAll()
  } catch {
    // 取消
  }
}

onMounted(loadAll)
</script>

<style scoped lang="scss">
.tool-monitor {
  padding: 16px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;

  h2 {
    margin: 0;
    font-size: 18px;
  }

  .actions {
    display: flex;
    gap: 8px;
  }
}

.section-card {
  margin-bottom: 16px;
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
