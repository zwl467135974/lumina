<template>
  <div class="kb-page">
    <PageHeader title="知识库联邦" description="三级可见性知识库管理（私有 / 团队 / 公共）+ Agent 挂载" />

    <el-card shadow="never">
      <div class="card-header">
        <el-input v-model="queryName" placeholder="按名称搜索" clearable style="width: 200px" @change="loadKbs" />
        <el-button type="primary" @click="openDialog">新建知识库</el-button>
      </div>

      <el-table v-loading="loading" :data="kbs" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="name" label="名称" min-width="150" />
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column label="可见性" width="100">
          <template #default="{ row }">
            <el-tag :type="visibilityType(row.visibility)" size="small">{{ visibilityLabel(row.visibility) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button link type="danger" @click="handleDelete(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never" class="mount-card">
      <template #header>Agent 知识库挂载</template>
      <el-form :inline="true">
        <el-form-item label="Agent ID">
          <el-input-number v-model="mountAgentId" :min="1" :controls="false" style="width: 80px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadAgentKbs">查询已挂载</el-button>
        </el-form-item>
      </el-form>

      <el-table v-if="agentKbs.length > 0" :data="agentKbs" stripe size="small">
        <el-table-column prop="id" label="KB ID" width="80" />
        <el-table-column prop="name" label="知识库名称" min-width="150" />
        <el-table-column label="可见性" width="100">
          <template #default="{ row }">
            <el-tag :type="visibilityType(row.visibility)" size="small">{{ visibilityLabel(row.visibility) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button link type="danger" @click="handleUnmount(row.id)">卸载</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-else description="输入 Agent ID 查询已挂载的知识库" :image-size="40" />

      <el-divider v-if="agentKbs.length > 0" />
      <el-form v-if="agentKbs.length > 0" :inline="true">
        <el-form-item label="挂载知识库">
          <el-select v-model="mountKbId" placeholder="选择知识库" style="width: 200px">
            <el-option v-for="kb in kbs" :key="kb.id" :label="kb.name" :value="kb.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="success" @click="handleMount">挂载</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-dialog v-model="dialogVisible" title="新建知识库" width="500px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="例如：产品文档库" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="可见性">
          <el-radio-group v-model="form.visibility">
            <el-radio value="PRIVATE">私有（仅创建者）</el-radio>
            <el-radio value="TEAM">团队（同租户）</el-radio>
            <el-radio value="PUBLIC">公共（所有 Agent 可用）</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleCreate">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
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

const queryName = ref('')
const loading = ref(false)
const kbs = ref<KnowledgeBaseVO[]>([])
const dialogVisible = ref(false)
const saving = ref(false)
const mountAgentId = ref(1)
const mountKbId = ref<number | undefined>(undefined)
const agentKbs = ref<KnowledgeBaseVO[]>([])

const form = reactive({ name: '', description: '', visibility: 'PRIVATE' })

const visibilityType = (v: string) => ({ PRIVATE: 'info', TEAM: 'warning', PUBLIC: 'success' }[v] || 'info')
const visibilityLabel = (v: string) => ({ PRIVATE: '私有', TEAM: '团队', PUBLIC: '公共' }[v] || v)

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
  await ElMessageBox.confirm('删除知识库将解除所有 Agent 挂载，确认？', '提示', { type: 'warning' })
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
  ElMessage.success('已挂载')
  await loadAgentKbs()
}

const handleUnmount = async (kbId: number) => {
  await unmountKnowledgeBase(mountAgentId.value, kbId)
  ElMessage.success('已卸载')
  await loadAgentKbs()
}

onMounted(() => { loadKbs() })
</script>

<style scoped>
.kb-page { padding: 0; }
.card-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.mount-card { margin-top: 16px; }
</style>
