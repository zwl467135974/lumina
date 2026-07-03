<template>
  <div class="prompt-page">
    <PageHeader title="Prompt 管理" description="版本化管理 Agent Prompt 模板，支持 {变量名} 占位符">
      <template #actions>
        <el-button type="primary" @click="showCreateDialog">新建 Prompt</el-button>
      </template>
    </PageHeader>

    <el-card shadow="never" class="search-card">
      <el-alert
        type="info"
        show-icon
        :closable="false"
        class="usage-alert"
        title="Prompt 生效规则"
        description="Agent 执行时会将 Agent 类型转为小写匹配 Prompt 名称，例如 ReAct -> react、simple -> simple、tool -> tool。发布并激活后会立即影响后续执行；未匹配到激活版本时使用 agent-core 内置 Prompt。"
      />
      <el-form inline>
        <el-form-item label="名称">
          <el-input v-model="searchName" placeholder="搜索" clearable style="width: 200px" @keyup.enter="loadList" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadList">查询</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="name" label="名称" min-width="150">
          <template #default="{ row }">
            <span>{{ row.name }}</span>
            <el-tag v-if="row.isActive === 1" size="small" type="success" style="margin-left: 6px">激活</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="version" label="版本" width="70">
          <template #default="{ row }">v{{ row.version }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '已发布' : '草稿' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="variables" label="变量" width="150" show-overflow-tooltip />
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column prop="updateTime" label="更新时间" width="170">
          <template #default="{ row }">{{ formatDate(row.updateTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="showVersions(row)">版本</el-button>
            <el-button v-if="row.status === 0" size="small" @click="showEditDialog(row)">编辑</el-button>
            <el-button v-if="row.status === 0" size="small" type="success" @click="handlePublish(row.id)">发布</el-button>
            <el-button size="small" type="primary" plain @click="showNewVersionDialog(row)">新版本</el-button>
            <el-button size="small" @click="copyContent(row)">复制</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新建/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="800px" :close-on-click-modal="false">
      <el-form :model="formData" label-width="80px">
        <el-form-item label="名称" required>
          <el-input v-model="formData.name" :disabled="!!editingId" placeholder="如 react / customer-service" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="formData.description" placeholder="简短描述" />
        </el-form-item>
        <el-form-item label="变量">
          <el-input v-model="formData.variables" placeholder="逗号分隔，如 task,context,language" />
        </el-form-item>
        <el-form-item label="内容" required>
          <el-input v-model="formData.content" type="textarea" :rows="14" placeholder="Prompt 模板内容，支持 {变量名} 占位符" class="prompt-editor" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">保存</el-button>
      </template>
    </el-dialog>

    <!-- 版本历史抽屉 -->
    <el-drawer v-model="versionDrawerVisible" :title="`${versionTarget} - 版本历史`" size="600px">
      <el-table :data="versions" v-loading="versionsLoading" stripe>
        <el-table-column prop="version" label="版本" width="70">
          <template #default="{ row }">v{{ row.version }}</template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.isActive === 1" type="success" size="small">激活</el-tag>
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '已发布' : '草稿' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updateTime" label="更新时间" width="170">
          <template #default="{ row }">{{ formatDate(row.updateTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <el-button v-if="row.isActive === 0 && row.status === 1" size="small" type="success" @click="handleActivate(row.id)">激活</el-button>
            <el-button size="small" @click="copyContent(row)">复制</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import {
  listPrompts, createPrompt, updatePrompt, publishPrompt,
  newPromptVersion, deletePrompt, getPromptVersions,
  type PromptVO, type PromptDTO
} from '@/api/modules/prompt'

const list = ref<PromptVO[]>([])
const loading = ref(false)
const searchName = ref('')

const loadList = async () => {
  loading.value = true
  try {
    const res = await listPrompts({ name: searchName.value || undefined, pageNum: 1, pageSize: 50 })
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
const newVersionSourceId = ref<number | null>(null)
const saving = ref(false)
const formData = reactive<PromptDTO>({ name: '', content: '', description: '', variables: '' })

const dialogTitle = computed(() => {
  if (newVersionSourceId.value) return '新建版本'
  return editingId.value ? '编辑 Prompt' : '新建 Prompt'
})

const showCreateDialog = () => {
  editingId.value = null
  newVersionSourceId.value = null
  Object.assign(formData, { name: '', content: '', description: '', variables: '' })
  dialogVisible.value = true
}

const showEditDialog = (row: PromptVO) => {
  editingId.value = row.id
  newVersionSourceId.value = null
  Object.assign(formData, { name: row.name, content: row.content, description: row.description || '', variables: row.variables || '' })
  dialogVisible.value = true
}

const showNewVersionDialog = (row: PromptVO) => {
  editingId.value = null
  newVersionSourceId.value = row.id
  Object.assign(formData, { name: row.name, content: row.content, description: row.description || '', variables: row.variables || '' })
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
      ElMessage.success('新版本已创建')
    } else if (editingId.value) {
      await updatePrompt(editingId.value, { ...formData })
      ElMessage.success('更新成功')
    } else {
      await createPrompt({ ...formData })
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

const handlePublish = async (id: number) => {
  try {
    await publishPrompt(id)
    ElMessage.success('已发布并激活')
    loadList()
  } catch (e: any) {
    ElMessage.error(e.message || '发布失败')
  }
}

const handleActivate = async (id: number) => {
  try {
    await publishPrompt(id)
    ElMessage.success('已激活')
    if (versionTarget.value) loadVersions(versionTarget.value)
  } catch (e: any) {
    ElMessage.error(e.message || '激活失败')
  }
}

const handleDelete = async (id: number) => {
  try {
    await ElMessageBox.confirm('确认删除此 Prompt 版本？', '提示', { type: 'warning' })
    await deletePrompt(id)
    ElMessage.success('已删除')
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
</style>
