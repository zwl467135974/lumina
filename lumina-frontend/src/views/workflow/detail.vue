<template>
  <div class="workflow-detail-page">
    <PageHeader title="执行详情" :description="`实例 #${instanceId}`">
      <template #actions>
        <el-button @click="router.back()">返回</el-button>
        <el-button @click="loadData">刷新</el-button>
      </template>
    </PageHeader>

    <!-- 实例概览 -->
    <el-card shadow="never" class="overview-card" v-loading="loading">
      <el-descriptions :column="4" border>
        <el-descriptions-item label="工作流">{{ instance?.definitionName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="版本">v{{ instance?.definitionVersion || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusType(instance?.status)" size="small">{{ instance?.status || '-' }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="当前节点">{{ instance?.currentNodeId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="执行时间">{{ formatDate(instance?.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ formatDate(instance?.updateTime) }}</el-descriptions-item>
        <el-descriptions-item label="输入" :span="2">
          <pre class="json-output">{{ formatJson(instance?.input) }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="输出" :span="2">
          <pre class="json-output">{{ formatJson(instance?.output) }}</pre>
        </el-descriptions-item>
        <el-descriptions-item v-if="instance?.errorMessage" label="错误" :span="4">
          <span class="error-text">{{ instance.errorMessage }}</span>
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 多 Agent 对话视图 -->
    <el-card shadow="never" class="timeline-card">
      <template #header>
        <div class="card-header-row">
          <span class="card-title">多 Agent 执行过程（{{ agentLogs.length }} 个节点）</span>
        </div>
      </template>

      <div v-if="agentLogs.length === 0 && !loading" class="empty-tip">暂无执行日志</div>

      <div v-else class="agent-conversation">
        <!-- 用户输入 -->
        <div class="conv-bubble conv-user">
          <div class="bubble-avatar">👤</div>
          <div class="bubble-body">
            <div class="bubble-role">用户输入</div>
            <div class="bubble-content-text">{{ formatJson(instance?.input) }}</div>
          </div>
        </div>

        <template v-for="log in agentLogs" :key="log.id">
          <div class="conv-arrow">↓</div>
          <div :class="['conv-bubble', `conv-${log.nodeType}`, { 'conv-error': log.status === 'FAILED' }]">
            <div class="bubble-avatar">{{ nodeIcon(log.nodeType) }}</div>
            <div class="bubble-body">
              <div class="bubble-header">
                <span class="bubble-name">{{ log.nodeName || log.nodeId }}</span>
                <el-tag size="small" type="info">{{ log.nodeType }}</el-tag>
                <span v-if="log.status === 'COMPLETED'" class="bubble-status completed">✓ {{ log.durationMs }}ms</span>
                <span v-else-if="log.status === 'FAILED'" class="bubble-status failed">❌ 失败</span>
                <span v-else class="bubble-status">{{ log.status }}</span>
              </div>
              <div v-if="log.output" class="bubble-result">{{ truncateOutput(log.output) }}</div>
              <div v-if="log.errorMessage" class="bubble-error">{{ log.errorMessage }}</div>
            </div>
          </div>
        </template>
      </div>
    </el-card>

    <!-- 原始执行日志（折叠） -->
    <el-card shadow="never" class="timeline-card">
      <template #header>原始执行日志</template>

      <div v-if="logs.length === 0 && !loading" class="empty-tip">暂无执行日志</div>

      <el-timeline v-else>
        <el-timeline-item
          v-for="log in logs"
          :key="log.id"
          :type="logStatusType(log.status)"
          :timestamp="formatDate(log.createTime)"
          placement="top"
        >
          <div class="log-node">
            <div class="log-header">
              <el-tag :type="logStatusType(log.status)" size="small">{{ log.status }}</el-tag>
              <span class="log-node-id">{{ log.nodeId }}</span>
              <el-tag size="small" type="info">{{ log.nodeType }}</el-tag>
              <span v-if="log.durationMs" class="log-duration">{{ log.durationMs }}ms</span>
            </div>
            <div v-if="log.nodeName" class="log-name">{{ log.nodeName }}</div>
            <div v-if="log.input" class="log-io">
              <span class="io-label">输入：</span>
              <pre class="json-output">{{ formatJson(log.input) }}</pre>
            </div>
            <div v-if="log.output" class="log-io">
              <span class="io-label">输出：</span>
              <pre class="json-output">{{ formatJson(log.output) }}</pre>
            </div>
            <div v-if="log.errorMessage" class="error-text">{{ log.errorMessage }}</div>
          </div>
        </el-timeline-item>
      </el-timeline>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageHeader from '@/components/common/PageHeader.vue'
import { getInstanceLogs, listInstances, type WorkflowInstanceVO, type WorkflowExecutionLogVO } from '@/api/modules/workflow'

const route = useRoute()
const router = useRouter()
const instanceId = Number(route.params.id)

const instance = ref<WorkflowInstanceVO | null>(null)
const logs = ref<WorkflowExecutionLogVO[]>([])
const loading = ref(false)

const loadData = async () => {
  loading.value = true
  try {
    const instRes = await listInstances({ pageNum: 1, pageSize: 1 })
    const all = instRes.data || []
    instance.value = all.find(i => i.id === instanceId) || null

    if (!instance.value) {
      // 尝试直接按 ID 查（后端目前只支持列表查询）
      instance.value = { id: instanceId } as WorkflowInstanceVO
    }

    const logRes = await getInstanceLogs(instanceId)
    logs.value = logRes.data || []
  } catch {
    // ignore
  } finally {
    loading.value = false
  }
}

const formatDate = (dt?: string) => {
  if (!dt) return '-'
  return dt.replace('T', ' ').substring(0, 19)
}

const formatJson = (str?: string) => {
  if (!str) return '-'
  try { return JSON.stringify(JSON.parse(str), null, 2) } catch { return str }
}

const statusType = (status?: string) => {
  const map: Record<string, string> = {
    COMPLETED: 'success', FAILED: 'danger', RUNNING: 'warning',
    PAUSED: 'info', PENDING: '', CANCELLED: 'info'
  }
  return status ? (map[status] || '') : ''
}

const logStatusType = (status: string) => {
  const map: Record<string, string> = {
    COMPLETED: 'success', FAILED: 'danger', RUNNING: 'warning',
    SKIPPED: 'info', WAITING: 'info'
  }
  return map[status] || ''
}

const agentLogs = computed(() => logs.value.filter(l => l.output || l.errorMessage || l.nodeType === 'agent'))

const truncateOutput = (text: string): string => {
  if (!text) return ''
  const cleaned = text.replace(/^"|"$/g, '').replace(/\\n/g, '\n')
  return cleaned.length > 300 ? cleaned.substring(0, 300) + '...' : cleaned
}

const nodeIcon = (nodeType?: string): string => {
  const icons: Record<string, string> = {
    agent: '🤖', condition: '🔀', parallel: '⚡', loop: '🔁', transform: '🔄', human: '✋'
  }
  return nodeType ? (icons[nodeType] || '📦') : '📦'
}

onMounted(loadData)
</script>

<style scoped>
.workflow-detail-page { padding: 0; }
.overview-card { margin-bottom: 12px; }
.timeline-card { margin-bottom: 12px; }
.card-title { font-weight: 600; font-size: 15px; }
.empty-tip { text-align: center; padding: 40px; color: var(--el-text-color-placeholder); }
.json-output {
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 12px;
  max-height: 200px;
  overflow-y: auto;
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
}
.error-text {
  color: var(--el-color-danger);
  font-size: 13px;
}
.card-header-row { display: flex; align-items: center; }

/* 多 Agent 对话气泡 */
.agent-conversation {
  max-height: 600px;
  overflow-y: auto;
  padding: 4px;
}
.conv-bubble {
  display: flex;
  gap: 10px;
  padding: 10px;
  border-radius: 8px;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
}
.conv-bubble.conv-agent { border-left: 3px solid var(--el-color-primary); }
.conv-bubble.conv-condition { border-left: 3px solid var(--el-color-warning); }
.conv-bubble.conv-parallel { border-left: 3px solid var(--el-color-success); }
.conv-bubble.conv-user { border-left: 3px solid var(--el-color-info); }
.conv-bubble.conv-error { border-left: 3px solid var(--el-color-danger); }
.bubble-avatar {
  width: 32px; height: 32px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-size: 18px; flex-shrink: 0; background: var(--el-fill-color);
}
.bubble-body { flex: 1; min-width: 0; }
.bubble-header {
  display: flex; align-items: center; gap: 6px; margin-bottom: 4px; flex-wrap: wrap;
}
.bubble-name { font-weight: 600; font-size: 14px; }
.bubble-role { font-size: 12px; color: var(--el-text-color-secondary); margin-bottom: 4px; }
.bubble-status { font-size: 12px; margin-left: auto; }
.bubble-status.completed { color: var(--el-color-success); }
.bubble-status.failed { color: var(--el-color-danger); }
.bubble-content-text {
  font-size: 12px; line-height: 1.6; white-space: pre-wrap; word-break: break-word;
  font-family: 'Consolas', 'Monaco', monospace; max-height: 100px; overflow-y: auto;
}
.bubble-result {
  font-size: 12px; line-height: 1.6; color: var(--el-text-color-regular);
  white-space: pre-wrap; word-break: break-word;
  background: var(--el-fill-color); padding: 8px; border-radius: 4px;
  max-height: 120px; overflow-y: auto;
  font-family: 'Consolas', 'Monaco', monospace;
}
.bubble-error { color: var(--el-color-danger); font-size: 12px; margin-top: 4px; }
.conv-arrow { text-align: center; color: var(--el-text-color-placeholder); font-size: 16px; padding: 4px 0; }
.log-node {
  .log-header {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 4px;
  }
  .log-node-id { font-weight: 600; font-size: 14px; }
  .log-duration { font-size: 12px; color: var(--el-text-color-secondary); }
  .log-name { font-size: 13px; color: var(--el-text-color-secondary); margin-bottom: 4px; }
  .log-io { margin-top: 4px; }
  .io-label { font-size: 12px; color: var(--el-text-color-placeholder); }
}
</style>
