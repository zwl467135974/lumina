<template>
  <div class="workflow-page">
    <PageHeader title="工作流管理" description="创建、管理和执行多 Agent 协作工作流">
      <template #actions>
        <el-button type="primary" @click="showCreateDialog">新建工作流</el-button>
        <el-button @click="showTemplateDialog">从模板创建</el-button>
      </template>
    </PageHeader>

    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <el-form inline>
        <el-form-item label="名称">
          <el-input v-model="searchName" placeholder="搜索工作流名称" clearable style="width: 200px" @keyup.enter="loadList" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchStatus" placeholder="全部" clearable style="width: 120px">
            <el-option label="草稿" :value="0" />
            <el-option label="已发布" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadList">查询</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never" class="list-card">
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="name" label="名称" min-width="150" />
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column prop="version" label="版本" width="80" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '已发布' : '草稿' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170">
          <template #default="{ row }">{{ formatDate(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="showInstances(row)">实例</el-button>
            <el-button v-if="row.status === 0" size="small" type="success" @click="handlePublish(row.id)">发布</el-button>
            <el-button v-if="row.status === 1" size="small" type="primary" @click="showExecuteDialog(row)">执行</el-button>
            <el-button size="small" @click="showEditDialog(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新建/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑工作流' : '新建工作流'" width="800px" :close-on-click-modal="false">
      <el-form :model="formData" label-width="100px">
        <el-form-item label="名称" required>
          <el-input v-model="formData.name" placeholder="工作流名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="formData.description" placeholder="简短描述" />
        </el-form-item>
        <el-form-item label="YAML 定义" required>
          <el-input v-model="formData.definitionYaml" type="textarea" :rows="18" placeholder="输入工作流 YAML..." class="yaml-editor" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">保存</el-button>
      </template>
    </el-dialog>

    <!-- 模板选择对话框 -->
    <el-dialog v-model="templateDialogVisible" title="选择模板" width="600px">
      <div v-loading="templatesLoading">
        <el-card v-for="tpl in templates" :key="tpl.name" shadow="hover" class="template-card" @click="useTemplate(tpl)">
          <div class="template-name">{{ tpl.name }}</div>
          <div class="template-desc">{{ tpl.description }}</div>
        </el-card>
        <el-empty v-if="!templatesLoading && templates.length === 0" description="暂无模板" />
      </div>
    </el-dialog>

    <!-- 执行对话框 -->
    <el-dialog v-model="executeDialogVisible" title="执行工作流" width="600px" :close-on-click-modal="false">
      <el-form :model="executeForm" label-width="80px">
        <el-form-item label="工作流">
          <span>{{ executeTarget?.name }}</span>
        </el-form-item>
        <el-form-item label="输入参数">
          <el-input v-model="executeForm.inputsJson" type="textarea" :rows="6" placeholder='JSON 格式，如 {"task": "分析这段代码"}' />
        </el-form-item>
      </el-form>

      <!-- SSE 执行进度 -->
      <div v-if="streamEvents.length > 0" class="stream-progress">
        <div class="stream-header">
          <el-tag v-if="streaming" type="warning" size="small">执行中…</el-tag>
          <el-tag v-else type="success" size="small">已完成</el-tag>
          <span class="stream-step">{{ streamEvents.length }} 步</span>
        </div>
        <el-timeline class="stream-timeline">
          <el-timeline-item
            v-for="(ev, idx) in streamEvents"
            :key="idx"
            :type="streamEventType(ev.event)"
            :timestamp="ev.nodeId || ''"
          >
            <div class="stream-event">
              <el-tag :type="streamEventType(ev.event)" size="small">{{ ev.event }}</el-tag>
              <span v-if="ev.nodeName" class="event-name">{{ ev.nodeName }}</span>
              <span v-if="ev.durationMs" class="event-duration">{{ ev.durationMs }}ms</span>
              <span v-if="ev.error" class="event-error">{{ ev.error }}</span>
            </div>
          </el-timeline-item>
        </el-timeline>
      </div>

      <template #footer>
        <el-button @click="executeDialogVisible = false">关闭</el-button>
        <el-button v-if="!streaming && streamEvents.length === 0" type="primary" @click="handleExecute" :loading="executing">执行</el-button>
        <el-button v-if="!streaming && streamEvents.length > 0" type="primary" @click="viewResult">查看详情</el-button>
      </template>
    </el-dialog>

    <!-- 实例列表抽屉 -->
    <el-drawer v-model="instanceDrawerVisible" :title="`${instanceTarget?.name} - 执行实例`" size="70%">
      <el-table :data="instances" v-loading="instancesLoading" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="instanceStatusType(row.status)" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="currentNodeId" label="当前节点" width="120" />
        <el-table-column prop="errorMessage" label="错误" min-width="200" show-overflow-tooltip />
        <el-table-column prop="createTime" label="执行时间" width="170">
          <template #default="{ row }">{{ formatDate(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button size="small" @click="viewInstanceDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import {
  listWorkflows, createWorkflow, updateWorkflow, deleteWorkflow,
  publishWorkflow, streamExecuteWorkflow, listInstances, getWorkflowTemplates,
  type WorkflowDefinitionVO, type WorkflowTemplateVO, type WorkflowDTO, type WorkflowInstanceVO,
  type WorkflowStreamEvent
} from '@/api/modules/workflow'

const router = useRouter()

const list = ref<WorkflowDefinitionVO[]>([])
const loading = ref(false)
const searchName = ref('')
const searchStatus = ref<number | undefined>(undefined)

const loadList = async () => {
  loading.value = true
  try {
    const res = await listWorkflows({
      name: searchName.value || undefined,
      status: searchStatus.value,
      pageNum: 1,
      pageSize: 50
    })
    list.value = res.data || []
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
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
    ElMessage.warning('名称和 YAML 定义不能为空')
    return
  }
  saving.value = true
  try {
    if (editingId.value) {
      await updateWorkflow(editingId.value, { ...formData })
      ElMessage.success('更新成功')
    } else {
      await createWorkflow({ ...formData })
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadList()
  } catch (e: any) {
    ElMessage.error(e.message || '操作失败')
  } finally {
    saving.value = false
  }
}

const handleDelete = async (id: number) => {
  try {
    await ElMessageBox.confirm('确认删除此工作流？', '提示', { type: 'warning' })
    await deleteWorkflow(id)
    ElMessage.success('已删除')
    loadList()
  } catch { /* cancelled */ }
}

const handlePublish = async (id: number) => {
  try {
    await publishWorkflow(id)
    ElMessage.success('已发布')
    loadList()
  } catch (e: any) {
    ElMessage.error(e.message || '发布失败')
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
        ElMessage.error(err.message || '执行失败')
        streaming.value = false
      },
      onClose: () => {
        streaming.value = false
        if (resultInstanceId.value) {
          ElMessage.success('执行完成')
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

const streamEventType = (event: string) => {
  if (event === 'WORKFLOW_COMPLETED') return 'success'
  if (event === 'WORKFLOW_FAILED' || event === 'NODE_FAILED') return 'danger'
  if (event === 'NODE_STARTED') return 'primary'
  return ''
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
    instances.value = res.data || []
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
  cursor: pointer;
  margin-bottom: 10px;
  transition: border-color 0.2s;
  &:hover { border-color: var(--el-color-primary); }
  .template-name { font-size: 15px; font-weight: 600; margin-bottom: 4px; }
  .template-desc { font-size: 13px; color: var(--el-text-color-secondary); }
}
.yaml-editor :deep(.el-textarea__inner) {
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 13px;
}
.stream-progress {
  margin-top: 16px;
  max-height: 300px;
  overflow-y: auto;
  background: var(--el-fill-color-light);
  border-radius: 6px;
  padding: 12px;
}
.stream-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  font-size: 13px;
}
.stream-timeline {
  padding-left: 8px;
}
.stream-event {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  .event-name { font-weight: 500; }
  .event-duration { color: var(--el-text-color-secondary); }
  .event-error { color: var(--el-color-danger); }
}
</style>
