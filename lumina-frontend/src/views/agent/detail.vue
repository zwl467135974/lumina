<template>
  <div class="agent-detail-page">
    <page-header :title="`${t('agent.detail')} - ${agentName}`">
      <el-button @click="goBack">返回</el-button>
    </page-header>

    <el-card v-loading="loading">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="Agent ID">{{ agentId }}</el-descriptions-item>
        <el-descriptions-item :label="t('agent.name')">{{ agentName }}</el-descriptions-item>
        <el-descriptions-item :label="t('agent.type')">{{ agentType }}</el-descriptions-item>
        <el-descriptions-item :label="t('common.status')">
          <el-tag :type="status === 1 ? 'success' : 'info'">
            {{ status === 1 ? t('common.enable') : t('common.disable') }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item :label="t('common.description')" :span="2">{{ description }}</el-descriptions-item>
        <el-descriptions-item :label="t('common.createTime')">{{ createTime }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ updateTime }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card class="prompt-card" shadow="never" v-loading="promptLoading">
      <template #header>
        <span>{{ t('agent.runtimePrompt') }}</span>
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

    <el-card class="task-card" shadow="never">
      <template #header>
        <span>后台任务执行</span>
      </template>
      <el-form label-width="90px">
        <el-form-item label="任务描述">
          <el-input
            v-model="asyncTaskText"
            type="textarea"
            :rows="3"
            placeholder="提交后立即返回 taskUuid，后台继续执行"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="submittingTask" :disabled="status !== 1" @click="submitAsyncTask">
            {{ t('agent.executeAsync') }}
          </el-button>
        </el-form-item>
      </el-form>

      <el-descriptions v-if="currentTask" class="task-result" :column="2" border>
        <el-descriptions-item label="任务 UUID" :span="2">{{ currentTask.taskUuid }}</el-descriptions-item>
        <el-descriptions-item :label="t('common.status')">
          <el-tag :type="taskStatusType(currentTask.status)">{{ currentTask.status }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item :label="t('task.duration')">{{ currentTask.durationMs ?? '-' }} ms</el-descriptions-item>
        <el-descriptions-item v-if="currentTask.result" :label="t('agent.taskResult')" :span="2">
          <el-input :model-value="currentTask.result" type="textarea" :rows="5" readonly />
        </el-descriptions-item>
        <el-descriptions-item v-if="currentTask.errorMessage" label="错误" :span="2">
          {{ currentTask.errorMessage }}
        </el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { getAgent, submitAgentTask, streamAgentTask, type AgentTaskVO, type TaskProgressEvent } from '@/api/modules/agent'
import { getActivePrompt, type PromptVO } from '@/api/modules/prompt'
import PageHeader from '@/components/common/PageHeader.vue'
import AgentChat from '@/components/agent/AgentChat.vue'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()

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
const asyncTaskText = ref('')
const submittingTask = ref(false)
const currentTask = ref<AgentTaskVO | null>(null)
let taskSseController: AbortController | undefined

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

const submitAsyncTask = async () => {
  if (!asyncTaskText.value.trim()) {
    ElMessage.warning('请输入任务描述')
    return
  }
  submittingTask.value = true
  try {
    const res = await submitAgentTask(agentId.value, { task: asyncTaskText.value.trim() })
    currentTask.value = res.data
    ElMessage.success(t('agent.asyncSubmitted'))
    startTaskStream(res.data.taskUuid)
  } finally {
    submittingTask.value = false
  }
}

const startTaskStream = (taskUuid: string) => {
  if (taskSseController) {
    taskSseController.abort()
  }
  taskSseController = streamAgentTask(taskUuid, {
    onEvent: (event: TaskProgressEvent) => {
      currentTask.value = {
        ...currentTask.value!,
        taskUuid: event.taskUuid,
        status: event.status as AgentTaskVO['status'],
        result: event.result,
        errorMessage: event.errorMessage,
        durationMs: event.durationMs,
        totalTokens: event.totalTokens
      }
    },
    onError: () => {
      // SSE 断开时静默处理，任务可能仍在后台执行
    },
    onClose: () => {
      taskSseController = undefined
    }
  })
}

const taskStatusType = (taskStatus: string) => {
  if (taskStatus === 'COMPLETED') return 'success'
  if (taskStatus === 'FAILED') return 'danger'
  if (taskStatus === 'RUNNING') return 'warning'
  return 'info'
}

onMounted(() => {
  loadAgentDetail()
})

onUnmounted(() => {
  if (taskSseController) {
    taskSseController.abort()
  }
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
.chat-card,
.task-card {
  margin-top: 16px;
}

.task-result {
  margin-top: 16px;
}
</style>
