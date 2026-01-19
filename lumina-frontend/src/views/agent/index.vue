<template>
  <div class="agent-list-page">
    <page-header title="Agent 列表">
      <el-button type="primary" @click="handleCreate">
        <el-icon><Plus /></el-icon>
        新建 Agent
      </el-button>
    </page-header>

    <el-card>
      <el-form :model="queryForm" inline>
        <el-form-item label="Agent 名称">
          <el-input v-model="queryForm.agentName" placeholder="请输入" clearable />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="queryForm.agentType" placeholder="请选择" clearable>
            <el-option label="ReAct" value="ReAct" />
            <el-option label="PlanAndExecute" value="PlanAndExecute" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadData">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="tableData" v-loading="loading" border style="margin-top: 20px">
        <el-table-column prop="agentId" label="ID" width="80" />
        <el-table-column prop="agentName" label="Agent 名称" />
        <el-table-column prop="agentType" label="类型" />
        <el-table-column prop="description" label="描述" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleView(row)">查看</el-button>
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
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
import { reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { listAgents, deleteAgent, type AgentVO, type QueryAgentDTO } from '@/api/modules/agent'
import { useTable } from '@/composables/useTable'
import PageHeader from '@/components/common/PageHeader.vue'

const router = useRouter()

const queryForm = reactive<QueryAgentDTO>({
  agentName: '',
  agentType: ''
})

const { loading, tableData, pagination, loadData, handlePageChange, handleSizeChange } = useTable<AgentVO>(
  (params) => listAgents({ ...queryForm, ...params })
)

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
    await ElMessageBox.confirm('确定要删除该 Agent 吗？', '提示', {
      type: 'warning'
    })
    await deleteAgent(row.agentId)
    ElMessage.success('删除成功')
    loadData()
  } catch {
    // 用户取消或删除失败
  }
}

const handleReset = () => {
  queryForm.agentName = ''
  queryForm.agentType = ''
  loadData()
}

onMounted(() => {
  loadData()
})
</script>

<style scoped lang="scss">
.agent-list-page {
  :deep(.el-pagination) {
    display: flex;
  }
}
</style>
