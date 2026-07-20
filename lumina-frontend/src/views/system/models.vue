<template>
  <div class="system-models-page">
    <PageHeader :title="t('system.model.title')" :description="t('system.model.description')" />

    <LumTablePanel
      :search-model="queryForm"
      :data="list"
      :loading="loading"
      :search-fields="searchFields"
      @search="loadData"
      @reset="handleReset"
    >
      <template #toolbar-left>
        <el-button type="primary" v-permission="'model:create'" @click="handleCreate">
          <el-icon><Plus /></el-icon>
          {{ t('common.create') }}
        </el-button>
      </template>

      <el-table-column prop="name" :label="t('system.model.name')" min-width="150" />
      <el-table-column prop="provider" :label="t('system.model.provider')" width="120">
        <template #default="{ row }">
          <el-tag size="small">{{ row.provider }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="defaultModel" :label="t('system.model.defaultModel')" width="150" />
      <el-table-column prop="baseUrl" :label="t('system.model.baseUrl')" min-width="200" show-overflow-tooltip />
      <el-table-column :label="t('system.model.apiKey')" width="160">
        <template #default="{ row }">
          <span v-if="row.hasApiKey" class="api-key-masked">{{ row.apiKeyMasked }}</span>
          <span v-else class="text-muted">-</span>
        </template>
      </el-table-column>
      <el-table-column prop="status" :label="t('common.status')" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? t('common.enable') : t('common.disable') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="priority" :label="t('system.model.priority')" width="90" sortable>
        <template #default="{ row }">
          <el-tag :type="row.priority <= 10 ? 'danger' : row.priority <= 50 ? 'warning' : 'info'" size="small">
            {{ row.priority ?? 100 }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('common.actions')" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" v-permission="'model:test'" @click="handleTest(row)" :loading="row._testing">
            {{ t('system.model.testConnection') }}
          </el-button>
          <el-dropdown trigger="click" @command="(cmd: string) => handleRowCommand(cmd, row)">
            <el-button link>{{ t('common.more') }}</el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item v-permission="'model:update'" command="edit">{{ t('common.edit') }}</el-dropdown-item>
                <el-dropdown-item v-permission="'model:delete'" command="delete" divided>{{ t('common.delete') }}</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
      </el-table-column>
    </LumTablePanel>

    <!-- 创建/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px" @close="handleDialogClose">
      <el-form :model="formData" :rules="formRules" ref="formRef" label-width="120px">
        <el-form-item :label="t('system.model.name')" prop="name" required>
          <el-input v-model="formData.name" :placeholder="t('system.model.namePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('system.model.provider')" prop="provider" required>
          <el-select v-model="formData.provider" :placeholder="t('common.pleaseSelect')" style="width: 100%">
            <el-option label="OpenAI" value="openai" />
            <el-option label="Anthropic (Claude)" value="anthropic" />
            <el-option label="DashScope (Qwen)" value="dashscope" />
            <el-option :label="t('system.model.providerGlm')" value="glm" />
            <el-option label="DeepSeek" value="deepseek" />
            <el-option :label="t('system.model.providerKimi')" value="kimi" />
            <el-option :label="t('system.model.providerOllama')" value="ollama" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('system.model.baseUrl')" prop="baseUrl">
          <el-input v-model="formData.baseUrl" :placeholder="t('system.model.baseUrlPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('system.model.apiKey')" prop="apiKey">
          <el-input
            v-model="formData.apiKey"
            type="password"
            show-password
            :placeholder="isEdit ? t('system.model.apiKeyKeepHint') : t('system.model.apiKeyPlaceholder')"
          />
        </el-form-item>
        <el-form-item :label="t('system.model.defaultModel')" prop="defaultModel">
          <el-input v-model="formData.defaultModel" :placeholder="t('system.model.modelPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('system.model.defaultParams')" prop="defaultParams">
          <el-input
            v-model="formData.defaultParams"
            type="textarea"
            :rows="3"
            placeholder='{"temperature": 0.7, "maxTokens": 2000}'
          />
        </el-form-item>
        <el-form-item :label="t('common.status')" prop="status">
          <el-switch v-model="formData.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item :label="t('system.model.priority')" prop="priority">
          <el-input-number v-model="formData.priority" :min="1" :max="999" />
          <span class="form-tip">{{ t('system.model.priorityHint') }}</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="saving">{{ t('common.ok') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import {
  getLlmProviderList, createLlmProvider, updateLlmProvider, deleteLlmProvider, testLlmProvider,
  type LlmProviderVO, type CreateLlmProviderDTO
} from '@/api/modules/llm-provider'
import { PageHeader, LumTablePanel, type SearchField } from '@/components/common'

const { t } = useI18n()

const queryForm = reactive({ name: '', provider: '', status: undefined as number | undefined })

const searchFields = computed<SearchField[]>(() => [
  { prop: 'name', label: t('system.model.name'), type: 'input', placeholder: t('common.pleaseInput') },
  {
    prop: 'provider',
    label: t('system.model.provider'),
    type: 'select',
    placeholder: t('common.pleaseSelect'),
    options: [
      { label: 'OpenAI', value: 'openai' },
      { label: 'Anthropic', value: 'anthropic' },
      { label: 'DashScope', value: 'dashscope' },
      { label: 'GLM', value: 'glm' },
      { label: 'DeepSeek', value: 'deepseek' },
      { label: 'Kimi', value: 'kimi' },
      { label: 'Ollama', value: 'ollama' }
    ]
  }
])

const loading = ref(false)
const list = ref<LlmProviderVO[]>([])

const loadData = async () => {
  loading.value = true
  try {
    const res = await getLlmProviderList({ ...queryForm })
    list.value = res.data || []
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

const handleReset = () => {
  queryForm.name = ''
  queryForm.provider = ''
  queryForm.status = undefined
  loadData()
}

// Dialog
const dialogVisible = ref(false)
const dialogTitle = ref('')
const isEdit = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const saving = ref(false)
const formData = reactive<CreateLlmProviderDTO>({
  name: '', provider: '', baseUrl: '', apiKey: '', defaultModel: '', defaultParams: '', status: 1, priority: 100
})

const formRules: FormRules = {
  name: [{ required: true, message: t('system.model.nameRequired'), trigger: 'blur' }],
  provider: [{ required: true, message: t('system.model.providerRequired'), trigger: 'change' }]
}

const handleCreate = () => {
  dialogTitle.value = t('system.model.create')
  isEdit.value = false
  editingId.value = null
  Object.assign(formData, { name: '', provider: '', baseUrl: '', apiKey: '', defaultModel: '', defaultParams: '', status: 1, priority: 100 })
  dialogVisible.value = true
}

const handleEdit = (row: LlmProviderVO) => {
  dialogTitle.value = t('system.model.edit')
  isEdit.value = true
  editingId.value = row.id
  Object.assign(formData, {
    name: row.name, provider: row.provider, baseUrl: row.baseUrl || '',
    apiKey: '', defaultModel: row.defaultModel || '', defaultParams: row.defaultParams || '',
    status: row.status, priority: row.priority ?? 100
  })
  dialogVisible.value = true
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      if (isEdit.value && editingId.value) {
        await updateLlmProvider(editingId.value, formData)
        ElMessage.success(t('common.updateSuccess'))
      } else {
        await createLlmProvider(formData)
        ElMessage.success(t('common.createSuccess'))
      }
      dialogVisible.value = false
      loadData()
    } catch {
      // handled by interceptor
    } finally {
      saving.value = false
    }
  })
}

const handleDialogClose = () => {
  formRef.value?.resetFields()
  editingId.value = null
}

const handleDelete = async (row: LlmProviderVO) => {
  try {
    await ElMessageBox.confirm(
      t('system.model.deleteConfirm', { name: row.name }),
      t('common.tip'),
      { type: 'warning' }
    )
    await deleteLlmProvider(row.id)
    ElMessage.success(t('common.deleteSuccess'))
    loadData()
  } catch {
    // 用户取消
  }
}

const handleTest = async (row: LlmProviderVO) => {
  row._testing = true
  try {
    await testLlmProvider(row.id)
    ElMessage.success(t('system.model.testSuccess'))
  } catch (err: any) {
    ElMessage.error(t('system.model.testFailed') + ': ' + (err?.message || ''))
  } finally {
    row._testing = false
  }
}

function handleRowCommand(cmd: string, row: LlmProviderVO) {
  if (cmd === 'edit') handleEdit(row)
  else if (cmd === 'delete') handleDelete(row)
}

onMounted(() => loadData())
</script>

<style scoped>
.api-key-masked {
  font-family: var(--lumina-font-mono);
  font-size: var(--lumina-font-size-sm);
  color: var(--lumina-text-secondary);
}

.text-muted {
  color: var(--lumina-text-muted);
}
</style>
