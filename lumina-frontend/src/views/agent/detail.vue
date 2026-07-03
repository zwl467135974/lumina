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

    <el-card class="prompt-card" shadow="never" v-loading="promptLoading">
      <template #header>
        <span>运行时 Prompt</span>
      </template>
      <template v-if="currentPrompt">
        <div class="prompt-header">
          <el-tag type="success" size="small">DB 激活</el-tag>
          <span>{{ currentPrompt.name }} v{{ currentPrompt.version }}</span>
        </div>
        <div class="prompt-desc">{{ currentPrompt.description || '无描述' }}</div>
        <el-input :model-value="currentPrompt.content" type="textarea" :rows="5" readonly />
      </template>
      <template v-else>
        <div class="prompt-header">
          <el-tag type="info" size="small">内置回退</el-tag>
          <span>prompts/{{ promptName }}.txt</span>
        </div>
        <div class="prompt-desc">
          Prompt 管理中没有名称为 {{ promptName }} 的激活版本，执行时使用 agent-core 内置 Prompt。
        </div>
      </template>
    </el-card>

    <el-card class="chat-card" shadow="never">
      <template #header>
        <span>💬 对话执行</span>
      </template>
      <agent-chat v-if="agentId && status === 1" :agent-id="agentId" />
      <el-alert v-else-if="agentId && status !== 1" title="Agent 未启用，无法对话" type="warning" :closable="false" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getAgent } from '@/api/modules/agent'
import { getActivePrompt, type PromptVO } from '@/api/modules/prompt'
import PageHeader from '@/components/common/PageHeader.vue'
import AgentChat from '@/components/agent/AgentChat.vue'

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
const promptLoading = ref(false)
const currentPrompt = ref<PromptVO | null>(null)

const promptName = computed(() => agentType.value.toLowerCase())

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
    await loadActivePrompt()
  } finally {
    loading.value = false
  }
}

const loadActivePrompt = async () => {
  if (!promptName.value) {
    currentPrompt.value = null
    return
  }
  promptLoading.value = true
  try {
    const res = await getActivePrompt(promptName.value)
    currentPrompt.value = res.data || null
  } catch {
    currentPrompt.value = null
  } finally {
    promptLoading.value = false
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
  .prompt-header {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 8px;
    font-weight: 500;
  }

  .prompt-desc {
    margin-bottom: 10px;
    color: #606266;
    font-size: 13px;
  }
}

.prompt-card,
.chat-card {
  margin-top: 16px;
}
</style>
