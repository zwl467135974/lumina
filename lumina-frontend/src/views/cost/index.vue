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
import { ref, onMounted } from 'vue'
import PageHeader from '@/components/common/PageHeader.vue'
import { getCostSummary, type CostSummary } from '@/api/modules/cost'

const loading = ref(false)
const summary = ref<CostSummary | null>(null)

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

onMounted(() => {
  loadSummary()
})
</script>

<style scoped>
.cost-page { padding: 0; }
.cost-card { margin-top: 16px; text-align: center; }
.total-cost .label { font-size: 14px; color: #909399; margin-right: 12px; }
.total-cost .value { font-size: 28px; font-weight: 700; color: #409eff; }
.top-card { margin-top: 16px; }
</style>
