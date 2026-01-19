<template>
  <div class="agent-detail-page">
    <page-header :title="`Agent 详情 - ${agentName}`">
      <el-button @click="goBack">返回</el-button>
    </page-header>

    <el-card v-loading="loading">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="Agent ID">{{ agentId }}</el-descriptions-item>
        <el-descriptions-item label="Agent 名称">{{ agentName }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ agentType }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="status === 1 ? 'success' : 'info'">
            {{ status === 1 ? '启用' : '禁用' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="描述" :span="2">{{ description }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ createTime }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ updateTime }}</el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getAgent, type AgentVO } from '@/api/modules/agent'
import PageHeader from '@/components/common/PageHeader.vue'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const agentId = ref<number>(0)
const agentName = ref('')
const agentType = ref('')
const status = ref(0)
const description = ref('')
const createTime = ref('')
const updateTime = ref('')

const loadAgentDetail = async () => {
  const id = Number(route.params.id)
  if (!id) return

  loading.value = true
  try {
    const res = await getAgent(id)
    const agent = res.data
    agentId.value = agent.agentId
    agentName.value = agent.agentName
    agentType.value = agent.agentType
    status.value = agent.status
    description.value = agent.description || ''
    createTime.value = agent.createTime
    updateTime.value = agent.updateTime
  } finally {
    loading.value = false
  }
}

const goBack = () => {
  router.back()
}

onMounted(() => {
  loadAgentDetail()
})
</script>

<style scoped lang="scss">
.agent-detail-page {
  // 样式
}
</style>
