<template>
  <div class="agent-list-page">
    <page-header :title="t('agent.list')">
      <el-button type="primary" @click="handleCreate">
        <el-icon><Plus /></el-icon>
        {{ t('agent.create') }}
      </el-button>
    </page-header>

    <el-card>
      <el-form :model="queryForm" inline>
        <el-form-item :label="t('agent.name')">
          <el-input v-model="queryForm.agentName" placeholder="请输入" clearable />
        </el-form-item>
        <el-form-item :label="t('agent.type')">
          <el-select v-model="queryForm.agentType" placeholder="请选择" clearable>
            <el-option label="ReAct" value="ReAct" />
            <el-option label="Simple" value="simple" />
            <el-option label="Tool" value="tool" />
            <el-option label="PlanAndExecute" value="PlanAndExecute" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="reloadData">{{ t('common.search') }}</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="tableData" v-loading="loading" border style="margin-top: 20px">
        <el-table-column prop="agentId" label="ID" width="80" />
        <el-table-column prop="agentName" :label="t('agent.name')" />
        <el-table-column prop="agentType" :label="t('agent.type')" />
        <el-table-column :label="t('agent.runtimePrompt')" min-width="220">
          <template #default="{ row }">
            <div class="prompt-cell">
              <template v-if="activePromptMap[getPromptKey(row.agentType)]">
                <el-tag size="small" type="success">DB 激活</el-tag>
                <span class="prompt-name">
                  {{ activePromptMap[getPromptKey(row.agentType)]?.name }}
                  v{{ activePromptMap[getPromptKey(row.agentType)]?.version }}
                </span>
              </template>
              <template v-else>
                <el-tag size="small" type="info">内置回退</el-tag>
                <span class="prompt-name">prompts/{{ getPromptKey(row.agentType) }}.txt</span>
              </template>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="description" :label="t('agent.description')" show-overflow-tooltip />
        <el-table-column prop="status" :label="t('common.status')" width="100">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 1"
              :loading="row._switching"
              @change="(val: boolean) => handleStatusToggle(row, val)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column :label="t('common.actions')" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleView(row)">查看</el-button>
            <el-button link type="primary" @click="handleEdit(row)">{{ t('common.edit') }}</el-button>
            <el-button link type="danger" @click="handleDelete(row)">{{ t('common.delete') }}</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pagination.pageNum"
        v-model:page-size="pagination.pageSize"
        :total="pagination.total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        style="margin-top: 20px; justify-content: flex-end"
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { listAgents, deleteAgent, updateAgent } from '@/api/modules/agent'
import { getActivePrompt, type PromptVO } from '@/api/modules/prompt'
import type { AgentVO, QueryAgentDTO } from '@/types/api'
import { useTable } from '@/composables/useTable'
import PageHeader from '@/components/common/PageHeader.vue'

const router = useRouter()
const { t } = useI18n()

const queryForm = reactive<QueryAgentDTO>({
  agentName: '',
  agentType: ''
})

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

const handlePageChange = async (page: number) => {
  pagination.pageNum = page
  await reloadData()
}

const handleSizeChange = async (size: number) => {
  pagination.pageSize = size
  pagination.pageNum = 1
  await reloadData()
}

const handleCreate = () => {
  router.push('/agent/create')
}

const handleView = (row: AgentVO) => {
  router.push(`/agent/detail/${row.agentId}`)
}

const handleEdit = (row: AgentVO) => {
  router.push(`/agent/edit/${row.agentId}`)
}

const handleDelete = async (row: AgentVO) => {
  try {
    await ElMessageBox.confirm(t('agent.deleteConfirm'), '提示', {
      type: 'warning'
    })
    await deleteAgent(row.agentId)
    ElMessage.success(t('common.success'))
    reloadData()
  } catch {
    // 用户取消或删除失败
  }
}

const handleReset = () => {
  queryForm.agentName = ''
  queryForm.agentType = ''
  reloadData()
}

const handleStatusToggle = async (row: any, val: boolean) => {
  row._switching = true
  try {
    await updateAgent(row.agentId, { status: val ? 1 : 0 })
    row.status = val ? 1 : 0
    ElMessage.success(val ? '已启用' : '已禁用')
  } catch {
    // error handled by interceptor
  } finally {
    row._switching = false
  }
}

onMounted(() => {
  reloadData()
})
</script>

<style scoped lang="scss">
.agent-list-page {
  .prompt-cell {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .prompt-name {
    color: #606266;
    font-size: 13px;
  }

  :deep(.el-pagination) {
    display: flex;
  }
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
