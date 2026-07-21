<template>
  <div class="workflow-page">
    <PageHeader :title="t('workflow.title')" :description="t('workflow.description')" />

    <LumTablePanel
      :search-model="queryForm"
      :data="list"
      :loading="loading"
      :search-fields="searchFields"
      @search="loadList"
      @reset="handleReset"
    >
      <template #toolbar-left>
        <el-button type="primary" @click="$router.push('/workflow/designer')">{{ t('workflow.visualEdit') }}</el-button>
        <el-button @click="showCreateDialog">YAML {{ t('common.create') }}</el-button>
        <el-button @click="showTemplateDialog">{{ t('workflow.templates') }}</el-button>
      </template>

      <el-table-column prop="name" :label="t('workflow.name')" min-width="150" />
        <el-table-column prop="description" :label="t('common.description')" min-width="200" show-overflow-tooltip />
        <el-table-column prop="version" :label="t('prompt.version')" width="80" />
        <el-table-column :label="t('common.status')" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? t('workflow.published') : t('workflow.draft') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" :label="t('common.createTime')" width="170">
          <template #default="{ row }">{{ formatDate(row.createTime) }}</template>
        </el-table-column>
        <el-table-column :label="t('common.actions')" width="200" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="showInstances(row)">{{ t('workflow.instances') }}</el-button>
            <el-button size="small" @click="$router.push(`/workflow/designer/${row.id}`)">{{ t('workflow.visualEdit') }}</el-button>
            <el-dropdown trigger="click" @command="(cmd: string) => handleRowCommand(cmd, row)">
              <el-button size="small" link>{{ t('common.more') }}</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-if="row.status === 0" command="publish">{{ t('prompt.publish') }}</el-dropdown-item>
                  <el-dropdown-item v-if="row.status === 1" command="execute">{{ t('workflow.execute') }}</el-dropdown-item>
                  <el-dropdown-item command="edit">{{ t('common.edit') }}</el-dropdown-item>
                  <el-dropdown-item command="delete" divided>{{ t('common.delete') }}</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
    </LumTablePanel>

    <!-- 新建/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="editingId ? t('workflow.edit') : t('workflow.create')" width="800px" :close-on-click-modal="false">
      <el-form :model="formData" label-width="100px">
        <el-form-item :label="t('common.description')" required>
          <el-input v-model="formData.name" :placeholder="t('workflow.namePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('common.description')">
          <el-input v-model="formData.description" :placeholder="t('workflow.descriptionPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('workflow.yamlDefinition')" required>
          <el-input v-model="formData.definitionYaml" type="textarea" :rows="18" :placeholder="t('workflow.yamlPlaceholder')" class="yaml-editor" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <!-- 模板选择对话框 -->
    <el-dialog v-model="templateDialogVisible" :title="t('workflow.selectTemplate')" width="650px">
      <div v-loading="templatesLoading">
        <el-card v-for="tpl in templates" :key="tpl.name" shadow="hover" class="template-card">
          <div class="template-header">
            <div>
              <div class="template-name">{{ tpl.name }}</div>
              <div class="template-desc">{{ tpl.description }}</div>
            </div>
            <div class="template-actions">
              <el-button size="small" @click="useTemplate(tpl)">{{ t('workflow.editYaml') }}</el-button>
              <el-button size="small" type="primary" @click="openFromTemplateDialog(tpl)">{{ t('workflow.oneClickCreate') }}</el-button>
            </div>
          </div>
          <div v-if="tpl.requiredAgents && tpl.requiredAgents.length > 0" class="template-agents">
            <el-tag v-for="r in tpl.requiredAgents" :key="r.placeholder" size="small" type="info">
              {{ r.placeholder }}: {{ r.description }}
            </el-tag>
          </div>
        </el-card>
        <el-empty v-if="!templatesLoading && templates.length === 0" :description="t('workflow.noTemplate')" />
      </div>
    </el-dialog>

    <!-- 从模板一键创建对话框 -->
    <el-dialog v-model="fromTemplateDialogVisible" :title="t('workflow.createFromTemplate')" width="500px">
      <el-form :model="fromTemplateForm" label-width="100px">
        <el-form-item :label="t('workflow.name')">
          <el-input v-model="fromTemplateForm.workflowName" :placeholder="t('workflow.namePlaceholder')" />
        </el-form-item>
        <el-form-item v-for="role in fromTemplateForm.roles" :key="role.placeholder" :label="role.placeholder">
          <el-input-number v-model="role.agentId" :min="1" placeholder="Agent ID" controls-position="right" />
          <span class="role-desc">{{ role.description }}</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="fromTemplateDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="fromTemplateLoading" @click="confirmFromTemplate">{{ t('workflow.createAndPublish') }}</el-button>
      </template>
    </el-dialog>

    <!-- 执行对话框 -->
    <el-dialog v-model="executeDialogVisible" :title="t('workflow.execute')" width="700px" :close-on-click-modal="false">
      <el-form :model="executeForm" label-width="80px">
        <el-form-item :label="t('workflow.title')">
          <span>{{ executeTarget?.name }}</span>
        </el-form-item>
        <el-form-item :label="t('workflow.inputParams')">
          <el-input v-model="executeForm.inputsJson" type="textarea" :rows="4" :placeholder="t('workflow.executeInputsPlaceholder')" />
        </el-form-item>
      </el-form>

      <!-- 多 Agent 对话过程 -->
      <div v-if="streamEvents.length > 0 || streaming" class="agent-conversation">
        <div class="conversation-header">
          <el-tag v-if="streaming" type="warning" size="small">{{ t('workflow.statusRunning') }}</el-tag>
          <el-tag v-else type="success" size="small">{{ t('workflow.statusDone') }}</el-tag>
          <span class="conv-step-count">{{ t('workflow.bubbleNodeCount', { n: agentBubbles.length }) }}</span>
        </div>

        <!-- 用户输入气泡 -->
        <div class="conv-bubble conv-user">
          <div class="bubble-avatar">👤</div>
          <div class="bubble-body">
            <div class="bubble-role">{{ t('workflow.userInput') }}</div>
            <div class="bubble-content">{{ executeForm.inputsJson }}</div>
          </div>
        </div>

        <!-- Agent / 节点气泡 -->
        <template v-for="(bubble, idx) in agentBubbles" :key="idx">
          <div class="conv-arrow">↓</div>
          <div :class="['conv-bubble', `conv-${bubble.nodeType || 'unknown'}`, { 'conv-error': bubble.status === 'failed' }]">
            <div class="bubble-avatar">{{ nodeIcon(bubble.nodeType) }}</div>
            <div class="bubble-body">
              <div class="bubble-header">
                <span class="bubble-name">{{ bubble.nodeName || bubble.nodeId }}</span>
                <el-tag v-if="bubble.agentId" size="small" type="primary">Agent #{{ bubble.agentId }}</el-tag>
                <el-tag size="small" type="info">{{ bubble.nodeType || 'node' }}</el-tag>
                <span v-if="bubble.status === 'completed'" class="bubble-status completed">✓ {{ bubble.durationMs }}ms</span>
                <span v-else-if="bubble.status === 'running'" class="bubble-status running">⏳ {{ t('workflow.statusRunning') }}</span>
                <span v-else-if="bubble.status === 'failed'" class="bubble-status failed">{{ t('workflow.statusFailed') }}</span>
              </div>
              <div v-if="bubble.result && bubble.status === 'completed'" class="bubble-result">
                {{ bubble.resultPreview }}
              </div>
              <div v-if="bubble.error" class="bubble-error">{{ bubble.error }}</div>
            </div>
          </div>
        </template>
      </div>

      <template #footer>
        <el-button @click="executeDialogVisible = false">{{ t('workflow.close') }}</el-button>
        <el-button v-if="!streaming && streamEvents.length === 0" type="primary" @click="handleExecute" :loading="executing">{{ t('workflow.execute') }}</el-button>
        <el-button v-if="!streaming && streamEvents.length > 0" type="primary" @click="viewResult">{{ t('workflow.viewResult') }}</el-button>
      </template>
    </el-dialog>

    <!-- 实例列表抽屉 -->
    <el-drawer v-model="instanceDrawerVisible" :title="t('workflow.instanceExecTitle', { name: instanceTarget?.name })" size="70%">
      <el-table :data="instances" v-loading="instancesLoading" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column :label="t('common.status')" width="120">
          <template #default="{ row }">
            <el-tag :type="instanceStatusType(row.status)" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="currentNodeId" :label="t('workflow.currentNode')" width="120" />
        <el-table-column prop="errorMessage" :label="t('workflow.error')" min-width="200" show-overflow-tooltip />
        <el-table-column prop="createTime" :label="t('workflow.execTime')" width="170">
          <template #default="{ row }">{{ formatDate(row.createTime) }}</template>
        </el-table-column>
        <el-table-column :label="t('common.actions')" width="100">
          <template #default="{ row }">
            <el-button size="small" @click="viewInstanceDetail(row)">{{ t('common.detail') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { PageHeader, LumTablePanel, type SearchField } from '@/components/common'
import {
  listWorkflows, createWorkflow, updateWorkflow, deleteWorkflow,
  publishWorkflow, streamExecuteWorkflow, listInstances, getWorkflowTemplates,
  createWorkflowFromTemplate,
  type WorkflowDefinitionVO, type WorkflowTemplateVO, type WorkflowDTO, type WorkflowInstanceVO,
  type WorkflowStreamEvent, type AgentRole
} from '@/api/modules/workflow'

const router = useRouter()
const { t } = useI18n()

const list = ref<WorkflowDefinitionVO[]>([])
const loading = ref(false)
const queryForm = reactive({ name: '', status: undefined as number | undefined })

const searchFields = computed<SearchField[]>(() => [
  { prop: 'name', label: t('workflow.name'), type: 'input', placeholder: t('common.search') },
  {
    prop: 'status',
    label: t('common.status'),
    type: 'select',
    placeholder: t('common.all'),
    options: [
      { label: t('workflow.draft'), value: 0 },
      { label: t('workflow.published'), value: 1 }
    ]
  }
])

const loadList = async () => {
  loading.value = true
  try {
    const res = await listWorkflows({
      name: queryForm.name || undefined,
      status: queryForm.status,
      pageNum: 1,
      pageSize: 50
    })
    // 后端返回 R<PageResult<...>>，数据在 data.list
    const page = res.data as any
    list.value = (page && Array.isArray(page.list)) ? page.list : (Array.isArray(page) ? page : [])
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

const handleReset = () => {
  queryForm.name = ''
  queryForm.status = undefined
  loadList()
}

// 对话框
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const saving = ref(false)
const formData = reactive<WorkflowDTO>({ name: '', description: '', definitionYaml: '' })

const showCreateDialog = () => {
  editingId.value = null
  formData.name = ''
  formData.description = ''
  formData.definitionYaml = ''
  dialogVisible.value = true
}

const showEditDialog = async (row: WorkflowDefinitionVO) => {
  editingId.value = row.id
  formData.name = row.name
  formData.description = row.description || ''
  formData.definitionYaml = row.definitionYaml
  dialogVisible.value = true
}

const handleSave = async () => {
  if (!formData.name.trim() || !formData.definitionYaml.trim()) {
    ElMessage.warning(t('workflow.nameAndYamlRequired'))
    return
  }
  saving.value = true
  try {
    if (editingId.value) {
      await updateWorkflow(editingId.value, { ...formData })
      ElMessage.success(t('common.updateSuccess'))
    } else {
      await createWorkflow({ ...formData })
      ElMessage.success(t('common.createSuccess'))
    }
    dialogVisible.value = false
    loadList()
  } catch (e: any) {
    ElMessage.error(e.message || t('common.failed'))
  } finally {
    saving.value = false
  }
}

const handleDelete = async (id: number) => {
  try {
    await ElMessageBox.confirm(t('workflow.deleteConfirm'), t('common.tip'), { type: 'warning' })
    await deleteWorkflow(id)
    ElMessage.success(t('common.deleteSuccess'))
    loadList()
  } catch { /* cancelled */ }
}

const handlePublish = async (id: number) => {
  try {
    await publishWorkflow(id)
    ElMessage.success(t('workflow.published'))
    loadList()
  } catch (e: any) {
    ElMessage.error(e.message || t('workflow.publishFailed'))
  }
}

// 模板
const templateDialogVisible = ref(false)
const templatesLoading = ref(false)
const templates = ref<WorkflowTemplateVO[]>([])

const showTemplateDialog = async () => {
  templateDialogVisible.value = true
  templatesLoading.value = true
  try {
    const res = await getWorkflowTemplates()
    templates.value = res.data || []
  } catch {
    templates.value = []
  } finally {
    templatesLoading.value = false
  }
}

const useTemplate = (tpl: WorkflowTemplateVO) => {
  formData.name = tpl.name
  formData.description = tpl.description || ''
  formData.definitionYaml = tpl.definitionYaml
  editingId.value = null
  templateDialogVisible.value = false
  dialogVisible.value = true
}

// 从模板一键创建
const fromTemplateDialogVisible = ref(false)
const fromTemplateLoading = ref(false)
const fromTemplateForm = reactive<{
  templateName: string
  workflowName: string
  roles: Array<{ placeholder: string; description: string; agentId: number | undefined }>
}>({
  templateName: '',
  workflowName: '',
  roles: []
})

const openFromTemplateDialog = (tpl: WorkflowTemplateVO) => {
  fromTemplateForm.templateName = tpl.name
  fromTemplateForm.workflowName = tpl.name + '-instance'
  fromTemplateForm.roles = (tpl.requiredAgents || []).map((r: AgentRole) => ({
    placeholder: r.placeholder,
    description: r.description || '',
    agentId: undefined
  }))
  // 无占位符的模板（如 pipeline），直接用默认 agentId=1
  if (fromTemplateForm.roles.length === 0) {
    fromTemplateForm.roles = [{ placeholder: 'agent1', description: t('workflow.defaultRole'), agentId: 1 }]
  }
  fromTemplateDialogVisible.value = true
}

const confirmFromTemplate = async () => {
  if (!fromTemplateForm.workflowName.trim()) {
    ElMessage.warning(t('workflow.workflowNameRequired'))
    return
  }
  const mapping: Record<string, number> = {}
  for (const role of fromTemplateForm.roles) {
    if (!role.agentId) {
      ElMessage.warning(t('workflow.agentIdRequired', { role: role.placeholder }))
      return
    }
    mapping[role.placeholder] = role.agentId
  }

  fromTemplateLoading.value = true
  try {
    await createWorkflowFromTemplate({
      templateName: fromTemplateForm.templateName,
      workflowName: fromTemplateForm.workflowName,
      agentMapping: mapping
    })
    ElMessage.success(t('workflow.createPublishSuccess'))
    fromTemplateDialogVisible.value = false
    templateDialogVisible.value = false
    loadList()
  } catch (e: any) {
    ElMessage.error(e.message || t('workflow.createFailed'))
  } finally {
    fromTemplateLoading.value = false
  }
}

// 执行
const executeDialogVisible = ref(false)
const executeTarget = ref<WorkflowDefinitionVO | null>(null)
const executing = ref(false)
const streaming = ref(false)
const streamEvents = ref<WorkflowStreamEvent[]>([])
const resultInstanceId = ref<number | null>(null)
const executeForm = reactive({ inputsJson: '{}' })

const showExecuteDialog = (row: WorkflowDefinitionVO) => {
  executeTarget.value = row
  executeForm.inputsJson = '{}'
  streamEvents.value = []
  resultInstanceId.value = null
  executeDialogVisible.value = true
}

function handleRowCommand(cmd: string, row: WorkflowDefinitionVO) {
  if (cmd === 'publish') handlePublish(row.id)
  else if (cmd === 'execute') showExecuteDialog(row)
  else if (cmd === 'edit') showEditDialog(row)
  else if (cmd === 'delete') handleDelete(row.id)
}

const handleExecute = async () => {
  if (!executeTarget.value) return
  streaming.value = true
  streamEvents.value = []

  let inputs: Record<string, unknown> = {}
  try { inputs = JSON.parse(executeForm.inputsJson) } catch { /* ignore */ }

  streamExecuteWorkflow(
    executeTarget.value.id,
    { inputs },
    {
      onEvent: (event: WorkflowStreamEvent) => {
        streamEvents.value.push(event)
        if (event.instanceId) {
          resultInstanceId.value = event.instanceId
        }
      },
      onError: (err: Error) => {
        ElMessage.error(err.message || t('workflow.executeFailed'))
        streaming.value = false
      },
      onClose: () => {
        streaming.value = false
        if (resultInstanceId.value) {
          ElMessage.success(t('workflow.executeDone'))
        }
      }
    }
  )
}

const viewResult = () => {
  executeDialogVisible.value = false
  if (resultInstanceId.value) {
    router.push(`/workflow/detail/${resultInstanceId.value}`)
  }
}

// 多 Agent 对话气泡
interface AgentBubble {
  nodeId: string
  nodeName: string
  nodeType?: string
  agentId?: number
  status: 'running' | 'completed' | 'failed'
  result?: string
  resultPreview: string
  durationMs?: number
  error?: string
}

const agentBubbles = computed<AgentBubble[]>(() => {
  const bubbles = new Map<string, AgentBubble>()
  for (const ev of streamEvents.value) {
    if (!ev.nodeId || ev.event === 'WORKFLOW_COMPLETED' || ev.event === 'WORKFLOW_FAILED') continue

    if (ev.event === 'NODE_STARTED') {
      bubbles.set(ev.nodeId, {
        nodeId: ev.nodeId,
        nodeName: ev.nodeName || ev.nodeId,
        nodeType: ev.nodeType,
        agentId: ev.agentId,
        status: 'running',
        resultPreview: ''
      })
    } else if (ev.event === 'NODE_COMPLETED') {
      const existing = bubbles.get(ev.nodeId)
      const resultStr = ev.result || ''
      bubbles.set(ev.nodeId, {
        nodeId: ev.nodeId,
        nodeName: existing?.nodeName || ev.nodeId,
        nodeType: existing?.nodeType || ev.nodeType,
        agentId: existing?.agentId || ev.agentId,
        status: 'completed',
        result: resultStr,
        resultPreview: truncateResult(resultStr),
        durationMs: ev.durationMs
      })
    } else if (ev.event === 'NODE_FAILED') {
      const existing = bubbles.get(ev.nodeId)
      bubbles.set(ev.nodeId, {
        nodeId: ev.nodeId,
        nodeName: existing?.nodeName || ev.nodeId,
        nodeType: existing?.nodeType || ev.nodeType,
        agentId: existing?.agentId || ev.agentId,
        status: 'failed',
        resultPreview: '',
        error: ev.error
      })
    }
  }
  return Array.from(bubbles.values())
})

const truncateResult = (text: string): string => {
  if (!text) return ''
  const cleaned = text.replace(/^"|"$/g, '').replace(/\\n/g, '\n')
  if (cleaned.length <= 300) return cleaned
  return cleaned.substring(0, 300) + '...'
}

const nodeIcon = (nodeType?: string): string => {
  const icons: Record<string, string> = {
    agent: '🤖',
    condition: '🔀',
    parallel: '⚡',
    loop: '🔁',
    transform: '🔄',
    human: '✋'
  }
  return nodeType ? (icons[nodeType] || '📦') : '📦'
}

// 实例
const instanceDrawerVisible = ref(false)
const instanceTarget = ref<WorkflowDefinitionVO | null>(null)
const instances = ref<WorkflowInstanceVO[]>([])
const instancesLoading = ref(false)

const showInstances = async (row: WorkflowDefinitionVO) => {
  instanceTarget.value = row
  instanceDrawerVisible.value = true
  instancesLoading.value = true
  try {
    const res = await listInstances({ definitionId: row.id, pageNum: 1, pageSize: 50 })
    // 后端返回 R<PageResult<...>>，数据在 data.list
    const page = res.data as any
    instances.value = (page && Array.isArray(page.list)) ? page.list : (Array.isArray(page) ? page : [])
  } catch {
    instances.value = []
  } finally {
    instancesLoading.value = false
  }
}

const viewInstanceDetail = (row: WorkflowInstanceVO) => {
  instanceDrawerVisible.value = false
  router.push(`/workflow/detail/${row.id}`)
}

// 工具函数
const formatDate = (dt?: string) => {
  if (!dt) return '-'
  return dt.replace('T', ' ').substring(0, 19)
}

const instanceStatusType = (status: string) => {
  const map: Record<string, string> = {
    COMPLETED: 'success', FAILED: 'danger', RUNNING: 'warning',
    PAUSED: 'info', PENDING: '', CANCELLED: 'info'
  }
  return map[status] || ''
}

loadList()
</script>

<style scoped>
.workflow-page { padding: 0; }
.search-card { margin-bottom: 12px; }
.list-card { margin-bottom: 12px; }
.template-card {
  margin-bottom: 10px;
  transition: border-color 0.2s;
  &:hover { border-color: var(--el-color-primary); }
  .template-header { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; }
  .template-name { font-size: 15px; font-weight: 600; margin-bottom: 4px; }
  .template-desc { font-size: 13px; color: var(--el-text-color-secondary); }
  .template-actions { display: flex; gap: 8px; flex-shrink: 0; }
  .template-agents { margin-top: 8px; display: flex; gap: 4px; flex-wrap: wrap; }
}
.role-desc { margin-left: 8px; font-size: 12px; color: var(--el-text-color-secondary); }
.yaml-editor :deep(.el-textarea__inner) {
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 13px;
}

/* 多 Agent 对话气泡 */
.agent-conversation {
  margin-top: 16px;
  max-height: 450px;
  overflow-y: auto;
  background: var(--el-fill-color-light);
  border-radius: 8px;
  padding: 16px;
}
.conversation-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  font-size: 13px;
}
.conv-step-count {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.conv-bubble {
  display: flex;
  gap: 10px;
  padding: 10px;
  border-radius: 8px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  transition: border-color 0.2s;
}
.conv-bubble.conv-agent {
  border-left: 3px solid var(--el-color-primary);
}
.conv-bubble.conv-condition {
  border-left: 3px solid var(--el-color-warning);
}
.conv-bubble.conv-parallel {
  border-left: 3px solid var(--el-color-success);
}
.conv-bubble.conv-user {
  border-left: 3px solid var(--el-color-info);
}
.conv-bubble.conv-error {
  border-left: 3px solid var(--el-color-danger);
}
.bubble-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  flex-shrink: 0;
  background: var(--el-fill-color-light);
}
.bubble-body {
  flex: 1;
  min-width: 0;
}
.bubble-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
  flex-wrap: wrap;
}
.bubble-name {
  font-weight: 600;
  font-size: 14px;
}
.bubble-status {
  font-size: 12px;
  margin-left: auto;
}
.bubble-status.completed { color: var(--el-color-success); }
.bubble-status.running { color: var(--el-color-warning); }
.bubble-status.failed { color: var(--el-color-danger); }
.bubble-result {
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-regular);
  white-space: pre-wrap;
  word-break: break-word;
  background: var(--el-fill-color-lighter);
  padding: 8px;
  border-radius: 4px;
  max-height: 120px;
  overflow-y: auto;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 12px;
}
.bubble-error {
  color: var(--el-color-danger);
  font-size: 12px;
  margin-top: 4px;
}
.conv-arrow {
  text-align: center;
  color: var(--el-text-color-placeholder);
  font-size: 16px;
  padding: 4px 0;
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
