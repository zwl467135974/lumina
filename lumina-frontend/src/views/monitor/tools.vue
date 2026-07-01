<template>
  <div class="tool-monitor">
    <div class="page-header">
      <h2>工具监控</h2>
      <div class="actions">
        <el-button @click="loadAll" :loading="loading">刷新</el-button>
        <el-button type="danger" plain @click="handleClear">清空记录</el-button>
      </div>
    </div>

    <!-- 调用统计 -->
    <el-card shadow="never" class="section-card">
      <template #header><span>调用统计</span></template>
      <el-table :data="statsList" v-loading="loading" stripe size="small">
        <el-table-column prop="toolName" label="工具名称" min-width="140" />
        <el-table-column prop="totalInvocations" label="调用次数" width="100" sortable />
        <el-table-column label="成功率" width="110">
          <template #default="{ row }">
            <el-tag :type="rateType(row.successRate)" size="small">
              {{ (row.successRate * 100).toFixed(1) }}%
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="平均耗时" width="100">
          <template #default="{ row }">{{ row.avgDurationMs.toFixed(0) }}ms</template>
        </el-table-column>
        <el-table-column label="最大耗时" width="100">
          <template #default="{ row }">{{ row.maxDurationMs }}ms</template>
        </el-table-column>
        <el-table-column label="最小耗时" width="100">
          <template #default="{ row }">{{ row.minDurationMs }}ms</template>
        </el-table-column>
        <el-table-column prop="failureCount" label="失败数" width="80" />
      </el-table>
      <el-empty v-if="!loading && statsList.length === 0" description="暂无工具调用记录" :image-size="60" />
    </el-card>

    <!-- 熔断状态 -->
    <el-card shadow="never" class="section-card">
      <template #header><span>熔断器状态</span></template>
      <el-table :data="breakerList" stripe size="small">
        <el-table-column prop="toolName" label="工具名称" min-width="140" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.open ? 'danger' : 'success'" size="small">
              {{ row.open ? '熔断中' : '正常' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="consecutiveFailures" label="连续失败" width="120" />
      </el-table>
      <el-empty v-if="breakerList.length === 0" description="暂无熔断记录" :image-size="60" />
    </el-card>

    <!-- 最近调用 -->
    <el-card shadow="never" class="section-card">
      <template #header><span>最近调用记录（{{ invocations.length }}）</span></template>
      <el-table :data="invocations" stripe size="small" max-height="400">
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ formatTime(row.timestamp) }}</template>
        </el-table-column>
        <el-table-column prop="toolName" label="工具" min-width="120" />
        <el-table-column label="结果" width="80">
          <template #default="{ row }">
            <el-tag :type="row.success ? 'success' : 'danger'" size="small">
              {{ row.success ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="耗时" width="80">
          <template #default="{ row }">{{ row.durationMs }}ms</template>
        </el-table-column>
        <el-table-column prop="error" label="错误信息" min-width="200" show-overflow-tooltip />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
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
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

const handleClear = async () => {
  try {
    await ElMessageBox.confirm('确认清空所有调用记录与统计？', '提示', { type: 'warning' })
    await clearToolInvocations()
    ElMessage.success('已清空')
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
</style>
