<template>
  <div class="agent-list-page">
    <PageHeader :title="t('agent.list')" />

    <LumTablePanel
      :search-model="queryForm"
      :data="tableData"
      :loading="loading"
      :pagination="pagination"
      :search-fields="searchFields"
      @search="reloadSearch"
      @reset="handleReset"
      @page-change="handlePageChange"
      @size-change="handleSizeChange"
    >
      <template #toolbar-left>
        <el-button type="primary" @click="handleCreate">
          <el-icon><Plus /></el-icon>
          {{ t('agent.create') }}
        </el-button>
      </template>

      <el-table-column prop="agentId" label="ID" width="80" />
      <el-table-column prop="agentName" :label="t('agent.name')" />
      <el-table-column prop="agentType" :label="t('agent.type')" width="130" />
      <el-table-column :label="t('agent.runtimePrompt')" min-width="220">
        <template #default="{ row }">
          <div class="prompt-cell">
            <template v-if="activePromptMap[getPromptKey(row.agentType)]">
              <el-tag size="small" type="success">{{ t('agent.promptActive') }}</el-tag>
              <span class="prompt-name">
                {{ activePromptMap[getPromptKey(row.agentType)]?.name }}
                v{{ activePromptMap[getPromptKey(row.agentType)]?.version }}
              </span>
            </template>
            <template v-else>
              <el-tag size="small" type="info">{{ t('agent.promptFallback') }}</el-tag>
              <span class="prompt-name">prompts/{{ getPromptKey(row.agentType) }}.txt</span>
            </template>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="description" :label="t('agent.description')" show-overflow-tooltip />
      <el-table-column prop="status" :label="t('common.status')" width="90">
        <template #default="{ row }">
          <el-switch
            :model-value="row.status === 1"
            :loading="row._switching"
            @change="(val: boolean) => handleStatusToggle(row, val)"
          />
        </template>
      </el-table-column>
      <el-table-column prop="createTime" :label="t('common.createTime')" width="180" />
      <el-table-column :label="t('common.actions')" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="handleView(row)">{{ t('common.view') }}</el-button>
          <el-button link type="primary" @click="handleEdit(row)">{{ t('common.edit') }}</el-button>
          <el-button link type="danger" @click="handleDelete(row)">{{ t('common.delete') }}</el-button>
        </template>
      </el-table-column>
    </LumTablePanel>
  </div>
</template>

<script setup lang="ts">

defineOptions({ name: 'AgentList' })
import { reactive, ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { listAgents, deleteAgent, updateAgent } from '@/api/modules/agent'
import { getActivePrompt, type PromptVO } from '@/api/modules/prompt'
import type { AgentVO, QueryAgentDTO } from '@/types/api'
import { useTable } from '@/composables/useTable'
import { PageHeader, LumTablePanel, type SearchField } from '@/components/common'

const router = useRouter()
const { t } = useI18n()

const queryForm = reactive<QueryAgentDTO>({
  agentName: '',
  agentType: ''
})

const searchFields = computed<SearchField[]>(() => [
  { prop: 'agentName', label: t('agent.name'), type: 'input', placeholder: t('common.pleaseInput') },
  {
    prop: 'agentType',
    label: t('agent.type'),
    type: 'select',
    placeholder: t('common.pleaseSelect'),
    options: [
      { label: 'ReAct', value: 'ReAct' },
      { label: 'Simple', value: 'simple' },
      { label: 'Tool', value: 'tool' },
      { label: 'PlanAndExecute', value: 'PlanAndExecute' }
    ]
  }
])

const { loading, tableData, pagination, loadData } = useTable<AgentVO>(
  (params) => listAgents({ ...queryForm, ...params })
)

const activePromptMap = ref<Record<string, PromptVO | null>>({})

const getPromptKey = (agentType?: string) => (agentType || '').toLowerCase()

const loadPromptUsage = async () => {
  const promptNames = Array.from(new Set(tableData.value.map((item) => getPromptKey(item.agentType)).filter(Boolean)))
  await Promise.all(promptNames.map(async (name) => {
    try {
      const res = await getActivePrompt(name)
      activePromptMap.value[name] = res.data || null
    } catch {
      activePromptMap.value[name] = null
    }
  }))
}

const reloadData = async () => {
  await loadData()
  await loadPromptUsage()
}

// 搜索/重置：pageNum 归 1 后重新加载列表与 prompt 用量（等价于 useTable.search 再附加 prompt 刷新）
const reloadSearch = async () => {
  pagination.pageNum = 1
  await reloadData()
}

const handlePageChange = async (page: number) => {
  pagination.pageNum = page
  await reloadData()
}

const handleSizeChange = async (size: number) => {
  pagination.pageSize = size
  pagination.pageNum = 1
  await reloadData()
}

const handleCreate = () => router.push('/agent/create')
const handleView = (row: AgentVO) => router.push(`/agent/detail/${row.agentId}`)
const handleEdit = (row: AgentVO) => router.push(`/agent/edit/${row.agentId}`)

const handleDelete = async (row: AgentVO) => {
  try {
    await ElMessageBox.confirm(t('agent.deleteConfirm'), t('common.tip'), { type: 'warning' })
  } catch {
    return // 用户取消
  }
  try {
    await deleteAgent(row.agentId)
    ElMessage.success(t('common.success'))
    reloadData()
  } catch {
    // 拦截器已弹错
  }
}

const handleReset = () => {
  queryForm.agentName = ''
  queryForm.agentType = ''
  reloadSearch()
}

const handleStatusToggle = async (row: any, val: boolean) => {
  row._switching = true
  try {
    await updateAgent(row.agentId, { status: val ? 1 : 0 })
    row.status = val ? 1 : 0
    ElMessage.success(val ? t('common.enableSuccess') : t('common.disableSuccess'))
  } catch {
    // handled by interceptor
  } finally {
    row._switching = false
  }
}

onMounted(() => reloadData())
</script>

<style scoped>
.prompt-cell {
  display: flex;
  align-items: center;
  gap: var(--lumina-spacing-sm);
}

.prompt-name {
  color: var(--lumina-text-secondary);
  font-size: var(--lumina-font-size-sm);
}
</style>
