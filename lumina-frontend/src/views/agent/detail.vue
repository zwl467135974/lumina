<template>
  <div class="agent-detail-page">
    <page-header :title="`${t('agent.detail')} - ${agentName}`">
      <el-button @click="goBack">{{ t('agent.form.back') }}</el-button>
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
        <el-descriptions-item :label="t('agent.form.updateTime')">{{ updateTime }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card class="prompt-card" shadow="never" v-loading="promptLoading">
      <template #header>
        <span>{{ t('agent.runtimePrompt') }}</span>
      </template>
      <template v-if="currentPrompt">
        <div class="prompt-header">
          <el-tag type="success" size="small">{{ t('agent.promptActive') }}</el-tag>
          <span>{{ currentPrompt.name }} v{{ currentPrompt.version }}</span>
        </div>
        <div class="prompt-desc">{{ currentPrompt.description || t('agent.form.noDescription') }}</div>
        <el-input :model-value="currentPrompt.content" type="textarea" :rows="5" readonly />
      </template>
      <template v-else>
        <div class="prompt-header">
          <el-tag type="info" size="small">{{ t('agent.promptFallback') }}</el-tag>
          <span>prompts/{{ promptName }}.txt</span>
        </div>
        <div class="prompt-desc">
          {{ t('agent.form.promptNotFound', { name: promptName }) }}
        </div>
      </template>
    </el-card>

    <el-card class="chat-card" shadow="never">
      <template #header>
        <span>{{ t('agent.form.chatExecution') }}</span>
      </template>
      <agent-chat v-if="agentId && status === 1" :agent-id="agentId" />
      <el-alert v-else-if="agentId && status !== 1" :title="t('agent.form.agentDisabled')" type="warning" :closable="false" />
    </el-card>

    <el-card class="task-card" shadow="never">
      <template #header>
        <span>{{ t('agent.form.backgroundTask') }}</span>
      </template>
      <el-form label-width="90px">
        <el-form-item :label="t('agent.form.taskDesc')">
          <el-input
            v-model="asyncTaskText"
            type="textarea"
            :rows="3"
            :placeholder="t('agent.form.taskDescPlaceholder')"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="submittingTask" :disabled="status !== 1" @click="submitAsyncTask">
            {{ t('agent.executeAsync') }}
          </el-button>
        </el-form-item>
      </el-form>

      <el-descriptions v-if="currentTask" class="task-result" :column="2" border>
        <el-descriptions-item :label="t('agent.form.taskUuid')" :span="2">{{ currentTask.taskUuid }}</el-descriptions-item>
        <el-descriptions-item :label="t('common.status')">
          <el-tag :type="taskStatusType(currentTask.status)">{{ currentTask.status }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item :label="t('task.duration')">{{ currentTask.durationMs ?? '-' }} ms</el-descriptions-item>
        <el-descriptions-item v-if="currentTask.result" :label="t('agent.taskResult')" :span="2">
          <el-input :model-value="currentTask.result" type="textarea" :rows="5" readonly />
        </el-descriptions-item>
        <el-descriptions-item v-if="currentTask.errorMessage" :label="t('agent.form.error')" :span="2">
          {{ currentTask.errorMessage }}
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card class="usage-card" shadow="never" v-loading="usageLoading">
      <template #header>
        <div class="usage-header">
          <span>{{ t('agent.toolUsage.title') }}</span>
          <el-select v-model="usageDays" size="small" style="width: 110px" @change="loadToolUsage">
            <el-option :value="7" :label="t('agent.toolUsage.days', { n: 7 })" />
            <el-option :value="30" :label="t('agent.toolUsage.days', { n: 30 })" />
            <el-option :value="90" :label="t('agent.toolUsage.days', { n: 90 })" />
          </el-select>
        </div>
      </template>

      <el-alert
        v-if="toolUsage?.unusedTools?.length"
        type="warning"
        :closable="false"
        class="unused-alert"
        :title="t('agent.toolUsage.unusedTip')"
      >
        <div class="unused-tags">
          <el-tag v-for="tool in toolUsage.unusedTools" :key="tool" size="small" type="warning">{{ tool }}</el-tag>
        </div>
      </el-alert>

      <el-table :data="toolUsage?.tools ?? []" size="small">
        <el-table-column prop="toolName" :label="t('agent.toolUsage.toolName')" min-width="170">
          <template #default="{ row }">
            <span class="tool-name">{{ row.toolName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="calls" :label="t('agent.toolUsage.calls')" width="90" />
        <el-table-column :label="t('agent.toolUsage.successRate')" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="rateType(row.successRate)">
              {{ row.successRate != null ? `${row.successRate}%` : '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('agent.toolUsage.avgDuration')" width="110">
          <template #default="{ row }">{{ Math.round(row.avgDurationMs) }} ms</template>
        </el-table-column>
        <el-table-column :label="t('agent.toolUsage.avgResult')" width="120">
          <template #default="{ row }">{{ Math.round(row.avgResultChars) }}</template>
        </el-table-column>
        <el-table-column :label="t('agent.toolUsage.lastUsed')" width="170">
          <template #default="{ row }">{{ row.lastUsedAt ? new Date(row.lastUsedAt).toLocaleString() : '-' }}</template>
        </el-table-column>
        <template #empty>{{ t('agent.toolUsage.empty') }}</template>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { getAgent, submitAgentTask, streamAgentTask, getAgentToolUsage, type AgentTaskVO, type TaskProgressEvent, type AgentToolUsageVO } from '@/api/modules/agent'
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
const usageLoading = ref(false)
const usageDays = ref(30)
const toolUsage = ref<AgentToolUsageVO | null>(null)
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
    loadToolUsage()
  } finally {
    loading.value = false
  }
}

const loadToolUsage = async () => {
  if (!agentId.value) return
  usageLoading.value = true
  try {
    const res = await getAgentToolUsage(agentId.value, usageDays.value)
    toolUsage.value = res.data || null
  } catch {
    toolUsage.value = null
  } finally {
    usageLoading.value = false
  }
}

const rateType = (rate?: number | null) => {
  if (rate == null) return 'info'
  if (rate >= 95) return 'success'
  if (rate >= 80) return 'warning'
  return 'danger'
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
    ElMessage.warning(t('agent.form.taskDescRequired'))
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
    color: var(--lumina-text-secondary);
    font-size: 13px;
  }
}

.prompt-card,
.chat-card,
.task-card,
.usage-card {
  margin-top: 16px;
}

.task-result {
  margin-top: 16px;
}

.usage-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.unused-alert {
  margin-bottom: 12px;
}

.unused-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}

.tool-name {
  font-family: var(--lumina-font-mono, monospace);
  font-size: 12px;
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
