<template>
  <div class="kb-page">
    <PageHeader :title="t('knowledgeBase.title')" :description="t('knowledgeBase.description')" />

    <el-card shadow="never">
      <div class="card-header">
        <el-input v-model="queryName" placeholder="按名称搜索" clearable style="width: 200px" @change="loadKbs" />
        <el-button type="primary" @click="openDialog">{{ t('knowledgeBase.create') }}</el-button>
      </div>

      <el-table v-loading="loading" :data="kbs" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="name" :label="t('knowledgeBase.name')" min-width="150" />
        <el-table-column prop="description" :label="t('common.description')" min-width="200" show-overflow-tooltip />
        <el-table-column :label="t('knowledgeBase.visibility')" width="100">
          <template #default="{ row }">
            <el-tag :type="visibilityType(row.visibility)" size="small">{{ visibilityLabel(row.visibility) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" :label="t('common.createTime')" width="170" />
        <el-table-column :label="t('common.actions')" width="80" fixed="right">
          <template #default="{ row }">
            <el-button link type="danger" @click="handleDelete(row.id)">{{ t('common.delete') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never" class="mount-card">
      <template #header>Agent 知识库挂载</template>
      <el-form :inline="true">
        <el-form-item label="Agent">
          <el-select v-model="mountAgentId" style="width: 240px" placeholder="Select Agent">
            <el-option v-for="a in agents" :key="a.agentId" :label="`${a.agentName} (#${a.agentId})`" :value="a.agentId" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadAgentKbs">查询已挂载</el-button>
        </el-form-item>
      </el-form>

      <el-table v-if="agentKbs.length > 0" :data="agentKbs" stripe size="small">
        <el-table-column prop="id" label="KB ID" width="80" />
        <el-table-column prop="name" label="知识库名称" min-width="150" />
        <el-table-column :label="t('knowledgeBase.visibility')" width="100">
          <template #default="{ row }">
            <el-tag :type="visibilityType(row.visibility)" size="small">{{ visibilityLabel(row.visibility) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.actions')" width="80">
          <template #default="{ row }">
            <el-button link type="danger" @click="handleUnmount(row.id)">{{ t('knowledgeBase.unmount') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-else description="输入 Agent ID 查询已挂载的知识库" :image-size="40" />

      <el-divider v-if="agentKbs.length > 0" />
      <el-form v-if="agentKbs.length > 0" :inline="true">
        <el-form-item :label="t('knowledgeBase.mountKb')">
          <el-select v-model="mountKbId" placeholder="选择知识库" style="width: 200px">
            <el-option v-for="kb in kbs" :key="kb.id" :label="kb.name" :value="kb.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="success" @click="handleMount">挂载</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="t('knowledgeBase.create')" width="500px">
      <el-form :model="form" label-width="80px">
        <el-form-item :label="t('knowledgeBase.name')" required>
          <el-input v-model="form.name" placeholder="例如：产品文档库" />
        </el-form-item>
        <el-form-item :label="t('common.description')">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item :label="t('knowledgeBase.visibility')">
          <el-radio-group v-model="form.visibility">
            <el-radio value="PRIVATE">私有（仅创建者）</el-radio>
            <el-radio value="TEAM">团队（同租户）</el-radio>
            <el-radio value="PUBLIC">公共（所有 Agent 可用）</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="handleCreate">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import {
  createKnowledgeBase,
  deleteKnowledgeBase,
  getAgentKnowledgeBases,
  listKnowledgeBases,
  mountKnowledgeBase,
  unmountKnowledgeBase,
  type KnowledgeBaseVO
} from '@/api/modules/knowledge-base'
import { listAgents } from '@/api/modules/agent'
import type { AgentVO } from '@/types/api'

const { t } = useI18n()

const queryName = ref('')
const loading = ref(false)
const kbs = ref<KnowledgeBaseVO[]>([])
const agents = ref<AgentVO[]>([])
const dialogVisible = ref(false)
const saving = ref(false)
const mountAgentId = ref(1)
const mountKbId = ref<number | undefined>(undefined)
const agentKbs = ref<KnowledgeBaseVO[]>([])

const form = reactive({ name: '', description: '', visibility: 'PRIVATE' })

const visibilityType = (v: string) => ({ PRIVATE: 'info', TEAM: 'warning', PUBLIC: 'success' }[v] || 'info')
const visibilityLabel = (v: string) => ({ PRIVATE: t('knowledgeBase.private'), TEAM: t('knowledgeBase.team'), PUBLIC: t('knowledgeBase.public') }[v] || v)

const loadKbs = async () => {
  loading.value = true
  try {
    const res = await listKnowledgeBases({ name: queryName.value || undefined })
    kbs.value = res.data || []
  } finally {
    loading.value = false
  }
}

const openDialog = () => {
  form.name = ''
  form.description = ''
  form.visibility = 'PRIVATE'
  dialogVisible.value = true
}

const handleCreate = async () => {
  if (!form.name.trim()) { ElMessage.warning('请填写名称'); return }
  saving.value = true
  try {
    await createKnowledgeBase({ ...form })
    ElMessage.success('知识库已创建')
    dialogVisible.value = false
    await loadKbs()
  } finally {
    saving.value = false
  }
}

const handleDelete = async (id: number) => {
  await ElMessageBox.confirm(t('knowledgeBase.deleteConfirm'), t('common.tip'), { type: 'warning' })
  await deleteKnowledgeBase(id)
  ElMessage.success('已删除')
  await loadKbs()
}

const loadAgentKbs = async () => {
  const res = await getAgentKnowledgeBases(mountAgentId.value)
  agentKbs.value = res.data || []
}

const handleMount = async () => {
  if (!mountKbId.value) { ElMessage.warning('请选择知识库'); return }
  await mountKnowledgeBase(mountAgentId.value, mountKbId.value)
  ElMessage.success(t('knowledgeBase.mounted'))
  await loadAgentKbs()
}

const handleUnmount = async (kbId: number) => {
  await unmountKnowledgeBase(mountAgentId.value, kbId)
  ElMessage.success('已卸载')
  await loadAgentKbs()
}

onMounted(() => {
  loadKbs()
  listAgents({ pageNum: 1, pageSize: 100 }).then(res => { agents.value = res.data.list || [] }).catch(() => {})
})
</script>

<style scoped>
.kb-page { padding: 0; }
.card-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.mount-card { margin-top: 16px; }

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
