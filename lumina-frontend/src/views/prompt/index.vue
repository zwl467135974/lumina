<template>
  <div class="prompt-page">
    <PageHeader :title="t('prompt.title')" :description="t('prompt.description')" />

    <el-alert
      type="info"
      show-icon
      :closable="false"
      style="margin-bottom: var(--lumina-spacing-md)"
      :title="t('prompt.ruleTitle')"
      :description="t('prompt.ruleDesc')"
    />

    <LumTablePanel
      :search-model="queryForm"
      :data="list"
      :loading="loading"
      :search-fields="searchFields"
      @search="loadList"
      @reset="handleReset"
    >
      <template #toolbar-left>
        <el-button type="primary" @click="showCreateDialog">{{ t('prompt.create') }}</el-button>
      </template>

      <el-table-column prop="name" :label="t('prompt.name')" min-width="150">
          <template #default="{ row }">
            <span>{{ row.name }}</span>
            <el-tag v-if="row.isActive === 1" size="small" type="success" style="margin-left: 6px">{{ t('prompt.activate') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="version" :label="t('prompt.version')" width="70">
          <template #default="{ row }">v{{ row.version }}</template>
        </el-table-column>
        <el-table-column :label="t('prompt.status')" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? t('prompt.published') : t('prompt.draft') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="variables" :label="t('prompt.variables')" width="150" show-overflow-tooltip />
        <el-table-column prop="agentType" :label="t('prompt.agentType')" width="130">
          <template #default="{ row }">
            <el-tag v-if="row.agentType" size="small" type="warning">{{ row.agentType }}</el-tag>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="description" :label="t('common.description')" min-width="200" show-overflow-tooltip />
        <el-table-column prop="updateTime" :label="t('prompt.updateTime')" width="170">
          <template #default="{ row }">{{ formatDate(row.updateTime) }}</template>
        </el-table-column>
        <el-table-column :label="t('common.actions')" width="180" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="showVersions(row)">{{ t('prompt.versions') }}</el-button>
            <el-button v-if="row.status === 0" size="small" @click="showEditDialog(row)">{{ t('common.edit') }}</el-button>
            <el-dropdown trigger="click" @command="(cmd: string) => handleRowCommand(cmd, row)">
              <el-button size="small" link>{{ t('common.more') }}</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-if="row.status === 0" command="publish">{{ t('prompt.publish') }}</el-dropdown-item>
                  <el-dropdown-item command="newVersion">{{ t('prompt.newVersion') }}</el-dropdown-item>
                  <el-dropdown-item command="copy">{{ t('prompt.copyContent') }}</el-dropdown-item>
                  <el-dropdown-item command="delete" divided>{{ t('common.delete') }}</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
    </LumTablePanel>

    <!-- 新建/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="800px" :close-on-click-modal="false">
      <el-form :model="formData" label-width="80px">
        <el-form-item :label="t('prompt.name')" required>
          <el-input v-model="formData.name" :disabled="!!editingId" :placeholder="t('prompt.namePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('prompt.agentType')">
          <el-input v-model="formData.agentType" :placeholder="t('prompt.agentTypePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('common.description')">
          <el-input v-model="formData.description" :placeholder="t('prompt.descPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('prompt.variables')">
          <el-input v-model="formData.variables" :placeholder="t('prompt.variablesPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('prompt.content')" required>
          <el-input v-model="formData.content" type="textarea" :rows="14" :placeholder="t('prompt.contentPlaceholder')" class="prompt-editor" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <!-- 版本历史抽屉 -->
    <el-drawer v-model="versionDrawerVisible" :title="`${versionTarget} - ${t('prompt.versions')}`" size="600px">
      <el-table :data="versions" v-loading="versionsLoading" stripe>
        <el-table-column prop="version" :label="t('prompt.version')" width="70">
          <template #default="{ row }">v{{ row.version }}</template>
        </el-table-column>
        <el-table-column :label="t('prompt.status')" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.isActive === 1" type="success" size="small">{{ t('prompt.activate') }}</el-tag>
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? t('prompt.published') : t('prompt.draft') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updateTime" :label="t('prompt.updateTime')" width="170">
          <template #default="{ row }">{{ formatDate(row.updateTime) }}</template>
        </el-table-column>
        <el-table-column :label="t('common.actions')" width="160">
          <template #default="{ row }">
            <el-button v-if="row.isActive === 0 && row.status === 1" size="small" type="success" @click="handleActivate(row.id)">{{ t('prompt.activate') }}</el-button>
            <el-button size="small" @click="copyContent(row)">{{ t('prompt.copyContent') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">

defineOptions({ name: 'Prompt' })
import { ref, reactive, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { PageHeader, LumTablePanel, type SearchField } from '@/components/common'
import {
  listPrompts, createPrompt, updatePrompt, publishPrompt,
  newPromptVersion, deletePrompt, getPromptVersions,
  type PromptVO, type PromptDTO
} from '@/api/modules/prompt'

const { t } = useI18n()

const list = ref<PromptVO[]>([])
const loading = ref(false)
const queryForm = reactive({ name: '' })

const searchFields = computed<SearchField[]>(() => [
  { prop: 'name', label: t('prompt.name'), type: 'input', placeholder: t('common.search') }
])

const loadList = async () => {
  loading.value = true
  try {
    const res = await listPrompts({ name: queryForm.name || undefined, pageNum: 1, pageSize: 50 })
    list.value = res.data || []
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

const handleReset = () => {
  queryForm.name = ''
  loadList()
}

// 对话框
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const newVersionSourceId = ref<number | null>(null)
const saving = ref(false)
const formData = reactive<PromptDTO>({ name: '', content: '', description: '', agentType: '', variables: '' })

const dialogTitle = computed(() => {
  if (newVersionSourceId.value) return t('prompt.newVersion')
  return editingId.value ? t('common.edit') : t('prompt.create')
})

const showCreateDialog = () => {
  editingId.value = null
  newVersionSourceId.value = null
  Object.assign(formData, { name: '', content: '', description: '', agentType: '', variables: '' })
  dialogVisible.value = true
}

const showEditDialog = (row: PromptVO) => {
  editingId.value = row.id
  newVersionSourceId.value = null
  Object.assign(formData, { name: row.name, content: row.content, description: row.description || '', agentType: row.agentType || '', variables: row.variables || '' })
  dialogVisible.value = true
}

const showNewVersionDialog = (row: PromptVO) => {
  editingId.value = null
  newVersionSourceId.value = row.id
  Object.assign(formData, { name: row.name, content: row.content, description: row.description || '', agentType: row.agentType || '', variables: row.variables || '' })
  dialogVisible.value = true
}

const handleSave = async () => {
  if (!formData.name.trim()) {
    ElMessage.warning('名称不能为空')
    return
  }
  saving.value = true
  try {
    if (newVersionSourceId.value) {
      await newPromptVersion(newVersionSourceId.value, { ...formData })
      ElMessage.success(t('common.createSuccess'))
    } else if (editingId.value) {
      await updatePrompt(editingId.value, { ...formData })
      ElMessage.success(t('common.updateSuccess'))
    } else {
      await createPrompt({ ...formData })
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

const handlePublish = async (id: number) => {
  try {
    await publishPrompt(id)
    ElMessage.success(t('prompt.activate'))
    loadList()
  } catch (e: any) {
    ElMessage.error(e.message || t('common.failed'))
  }
}

const handleActivate = async (id: number) => {
  try {
    await publishPrompt(id)
    ElMessage.success(t('prompt.activate'))
    if (versionTarget.value) loadVersions(versionTarget.value)
  } catch (e: any) {
    ElMessage.error(e.message || t('common.failed'))
  }
}

const handleDelete = async (id: number) => {
  try {
    await ElMessageBox.confirm(t('prompt.deleteConfirm'), t('common.tip'), { type: 'warning' })
    await deletePrompt(id)
    ElMessage.success(t('common.deleteSuccess'))
    loadList()
  } catch { /* cancelled */ }
}

// 版本历史
const versionDrawerVisible = ref(false)
const versionTarget = ref('')
const versions = ref<PromptVO[]>([])
const versionsLoading = ref(false)

const showVersions = async (row: PromptVO) => {
  versionTarget.value = row.name
  versionDrawerVisible.value = true
  loadVersions(row.name)
}

const loadVersions = async (name: string) => {
  versionsLoading.value = true
  try {
    const res = await getPromptVersions(name)
    versions.value = res.data || []
  } catch {
    versions.value = []
  } finally {
    versionsLoading.value = false
  }
}

// 工具函数
const copyContent = (row: PromptVO) => {
  navigator.clipboard.writeText(row.content).then(() => {
    ElMessage.success('已复制到剪贴板')
  }).catch(() => {
    ElMessage.warning('复制失败')
  })
}

function handleRowCommand(cmd: string, row: PromptVO) {
  if (cmd === 'publish') handlePublish(row.id)
  else if (cmd === 'newVersion') showNewVersionDialog(row)
  else if (cmd === 'copy') copyContent(row)
  else if (cmd === 'delete') handleDelete(row.id)
}

const formatDate = (dt?: string) => {
  if (!dt) return '-'
  return dt.replace('T', ' ').substring(0, 19)
}

loadList()
</script>

<style scoped>
.prompt-page { padding: 0; }
.search-card { margin-bottom: 12px; }
.usage-alert { margin-bottom: 12px; }
.prompt-editor :deep(.el-textarea__inner) {
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 13px;
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
