<template>
  <div class="knowledge-page">
    <PageHeader :title="t('knowledge.title')" :description="t('knowledge.description')">
      <el-button @click="loadAll" :loading="loading">{{ t('common.refresh') }}</el-button>
    </PageHeader>

    <el-tabs v-model="activeTab" class="knowledge-tabs" @tab-change="onTabChange">
      <!-- 文档管理 -->
      <el-tab-pane :label="t('knowledge.documents')" name="documents">
        <el-card shadow="never">
          <div class="kb-selector">
            <span class="kb-label">{{ t('knowledgeBase.title') }}:</span>
            <el-select v-model="selectedKbId" clearable :placeholder="t('common.all')" style="width: 240px" @change="loadDocuments">
              <el-option v-for="kb in knowledgeBases" :key="kb.id" :label="`${kb.name} (${kb.visibility})`" :value="kb.id" />
            </el-select>
          </div>

          <el-upload ref="uploadRef" :auto-upload="true" :show-file-list="false" :http-request="handleUpload" accept=".txt,.md,.pdf,.doc,.docx" drag class="upload-area">
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">{{ t('knowledge.dragTip') }}</div>
            <template #tip><div class="upload-tip">{{ t('knowledge.formatTip') }}</div></template>
          </el-upload>
          <div v-if="uploading" class="upload-progress"><el-icon class="is-loading"><Loading /></el-icon> {{ t('knowledge.pending') }}...</div>

          <el-table :data="documents" v-loading="loading" stripe size="small" style="margin-top: 16px">
            <el-table-column prop="title" :label="t('knowledge.documentTitle')" min-width="200" show-overflow-tooltip />
            <el-table-column :label="t('knowledge.format')" width="80">
              <template #default="{ row }"><el-tag size="small">{{ row.format }}</el-tag></template>
            </el-table-column>
            <el-table-column prop="chunkCount" label="Chunks" width="70" />
            <el-table-column :label="t('knowledge.fileSize')" width="90">
              <template #default="{ row }">{{ formatFileSize(row.fileSize) }}</template>
            </el-table-column>
            <el-table-column :label="t('knowledge.ingestStatus')" width="80">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'warning'" size="small">{{ statusText(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" :label="t('common.createTime')" width="160" />
            <el-table-column :label="t('common.actions')" width="80" fixed="right">
              <template #default="{ row }">
                <el-button type="danger" link size="small" @click="handleDelete(row)">{{ t('common.delete') }}</el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination-wrap" v-if="total > 0">
            <el-pagination v-model:current-page="pageNum" v-model:page-size="pageSize" :total="total" :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next" @size-change="loadDocuments" @current-change="loadDocuments" />
          </div>
          <el-empty v-if="!loading && documents.length === 0" :description="t('common.noData')" :image-size="60">
            <el-button type="primary" @click="triggerUpload">{{ t('knowledge.upload') }}</el-button>
          </el-empty>
        </el-card>
      </el-tab-pane>

      <!-- 知识库管理 -->
      <el-tab-pane :label="t('knowledgeBase.title')" name="kbs">
        <el-card shadow="never">
          <div class="card-header">
            <el-button type="primary" size="small" @click="openKbDialog">{{ t('knowledgeBase.create') }}</el-button>
          </div>
          <el-table :data="knowledgeBases" stripe size="small" style="margin-top: 12px">
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="name" :label="t('knowledgeBase.name')" min-width="150" />
            <el-table-column prop="description" :label="t('common.description')" min-width="200" show-overflow-tooltip />
            <el-table-column :label="t('knowledgeBase.visibility')" width="100">
              <template #default="{ row }"><el-tag :type="visibilityType(row.visibility)" size="small">{{ visibilityLabel(row.visibility) }}</el-tag></template>
            </el-table-column>
            <el-table-column prop="createTime" :label="t('common.createTime')" width="160" />
            <el-table-column :label="t('common.actions')" width="80" fixed="right">
              <template #default="{ row }"><el-button link type="danger" @click="handleDeleteKb(row.id)">{{ t('common.delete') }}</el-button></template>
            </el-table-column>
          </el-table>
        </el-card>

        <el-card shadow="never" style="margin-top: 16px">
          <template #header>{{ t('knowledgeBase.mount') }}</template>
          <el-form :inline="true">
            <el-form-item label="Agent">
              <el-select v-model="mountAgentId" style="width: 240px" placeholder="Select Agent">
                <el-option v-for="a in agents" :key="a.agentId" :label="`${a.agentName} (#${a.agentId})`" :value="a.agentId" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="loadAgentKbs">{{ t('common.search') }}</el-button>
            </el-form-item>
          </el-form>
          <el-table v-if="agentKbs.length > 0" :data="agentKbs" stripe size="small">
            <el-table-column prop="id" label="KB ID" width="80" />
            <el-table-column prop="name" :label="t('knowledgeBase.name')" min-width="150" />
            <el-table-column :label="t('knowledgeBase.visibility')" width="100">
              <template #default="{ row }"><el-tag :type="visibilityType(row.visibility)" size="small">{{ visibilityLabel(row.visibility) }}</el-tag></template>
            </el-table-column>
            <el-table-column :label="t('common.actions')" width="80">
              <template #default="{ row }"><el-button link type="danger" @click="handleUnmount(row.id)">{{ t('knowledgeBase.unmount') }}</el-button></template>
            </el-table-column>
          </el-table>
          <el-empty v-else :description="t('common.noData')" :image-size="40" />
          <el-divider v-if="agentKbs.length > 0" />
          <el-form v-if="agentKbs.length > 0" :inline="true">
            <el-form-item :label="t('knowledgeBase.mountKb')">
              <el-select v-model="mountKbId" :placeholder="t('knowledgeBase.name')" style="width: 200px">
                <el-option v-for="kb in knowledgeBases" :key="kb.id" :label="kb.name" :value="kb.id" />
              </el-select>
            </el-form-item>
            <el-form-item><el-button type="success" @click="handleMount">{{ t('knowledgeBase.mount') }}</el-button></el-form-item>
          </el-form>
        </el-card>
      </el-tab-pane>

      <!-- 检索测试 -->
      <el-tab-pane :label="t('knowledge.search')" name="search">
        <el-card shadow="never">
          <div class="search-bar">
            <el-input v-model="searchQuery" :placeholder="t('knowledge.searchPlaceholder')" clearable @keyup.enter="handleSearch" />
            <el-button type="primary" @click="handleSearch" :loading="searching">{{ t('knowledge.search') }}</el-button>
          </div>
          <div v-if="searchResults.length > 0" class="search-results">
            <div v-for="(item, idx) in searchResults" :key="idx" class="search-item">
              <div class="search-meta"><el-tag size="small" type="primary">#{{ idx + 1 }}</el-tag><span class="score">{{ (item.score * 100).toFixed(1) }}%</span></div>
              <div class="search-content">{{ item.content }}</div>
            </div>
          </div>
          <el-empty v-if="searched && searchResults.length === 0" :description="t('knowledge.noResult')" :image-size="60" />
        </el-card>
      </el-tab-pane>

      <!-- 知识沉淀审核（自维护 Wiki 飞轮） -->
      <el-tab-pane :label="t('knowledge.deposits')" name="deposits">
        <el-card shadow="never">
          <div class="search-bar">
            <el-select v-model="depositStatusFilter" style="width: 160px" @change="loadDeposits(1)">
              <el-option value="" :label="t('common.all')" />
              <el-option value="PENDING" :label="t('knowledge.depositPending')" />
              <el-option value="APPROVED" :label="t('knowledge.depositApproved')" />
              <el-option value="REJECTED" :label="t('knowledge.depositRejected')" />
            </el-select>
            <el-button type="primary" @click="loadDeposits(1)">{{ t('common.search') }}</el-button>
          </div>
          <el-table :data="deposits" v-loading="depositsLoading" stripe size="small">
            <el-table-column prop="title" :label="t('knowledge.depositTitle')" min-width="180" show-overflow-tooltip />
            <el-table-column :label="t('knowledge.depositSource')" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="row.sourceType === 'TASK' ? 'primary' : 'info'">
                  {{ row.sourceType === 'TASK' ? t('knowledge.depositSourceTask') : t('knowledge.depositSourceManual') }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="t('knowledgeBase.title')" width="90">
              <template #default="{ row }">{{ row.kbId }}</template>
            </el-table-column>
            <el-table-column :label="t('common.status')" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="depositStatusType(row.status)">{{ depositStatusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" :label="t('task.createTime')" width="170" />
            <el-table-column :label="t('common.actions')" width="170" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="viewDeposit(row)">{{ t('common.detail') }}</el-button>
                <template v-if="row.status === 'PENDING'">
                  <el-button link type="success" @click="handleReviewDeposit(row, true)">{{ t('knowledge.depositApprove') }}</el-button>
                  <el-button link type="danger" @click="handleReviewDeposit(row, false)">{{ t('knowledge.depositRejectBtn') }}</el-button>
                </template>
              </template>
            </el-table-column>
          </el-table>
          <div class="deposit-pagination">
            <el-pagination
              layout="total, prev, pager, next"
              :total="depositTotal"
              :page-size="depositPageSize"
              :current-page="depositPageNum"
              @current-change="(p: number) => loadDeposits(p)"
            />
          </div>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 沉淀详情/预览 -->
    <el-dialog v-model="depositDetailVisible" :title="t('knowledge.depositDetail')" width="700px">
      <el-descriptions v-if="depositDetail" :column="2" border>
        <el-descriptions-item :label="t('knowledge.depositTitle')" :span="2">{{ depositDetail.title }}</el-descriptions-item>
        <el-descriptions-item :label="t('common.status')">
          <el-tag size="small" :type="depositStatusType(depositDetail.status)">{{ depositStatusLabel(depositDetail.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item :label="t('knowledgeBase.title')">KB {{ depositDetail.kbId }}</el-descriptions-item>
        <el-descriptions-item :label="t('knowledge.depositContent')" :span="2">
          <div class="deposit-content">{{ depositDetail.content }}</div>
        </el-descriptions-item>
        <el-descriptions-item v-if="depositDetail.reviewComment" :label="t('knowledge.depositComment')" :span="2">
          {{ depositDetail.reviewComment }}
        </el-descriptions-item>
        <el-descriptions-item v-if="depositDetail.docUuid" label="Doc UUID" :span="2">
          {{ depositDetail.docUuid }}
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <el-dialog v-model="kbDialogVisible" :title="t('knowledgeBase.create')" width="500px">
      <el-form :model="kbForm" label-width="80px">
        <el-form-item :label="t('knowledgeBase.name')" required><el-input v-model="kbForm.name" /></el-form-item>
        <el-form-item :label="t('common.description')"><el-input v-model="kbForm.description" type="textarea" :rows="2" /></el-form-item>
        <el-form-item :label="t('knowledgeBase.visibility')">
          <el-radio-group v-model="kbForm.visibility">
            <el-radio value="PRIVATE">{{ t('knowledgeBase.private') }}</el-radio>
            <el-radio value="TEAM">{{ t('knowledgeBase.team') }}</el-radio>
            <el-radio value="PUBLIC">{{ t('knowledgeBase.public') }}</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="kbDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="savingKb" @click="handleCreateKb">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">

defineOptions({ name: 'Knowledge' })
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadInstance } from 'element-plus'
import { UploadFilled, Loading } from '@element-plus/icons-vue'
import PageHeader from '@/components/common/PageHeader.vue'
import { uploadDocument, listDocuments, deleteDocument, searchKnowledge, listKnowledgeDeposits, reviewKnowledgeDeposit, type KnowledgeDocumentVO, type SearchResult, type KnowledgeDepositVO } from '@/api/modules/knowledge'
import { listKnowledgeBases, createKnowledgeBase, deleteKnowledgeBase, getAgentKnowledgeBases, mountKnowledgeBase, unmountKnowledgeBase, type KnowledgeBaseVO } from '@/api/modules/knowledge-base'
import { listAgents } from '@/api/modules/agent'
import type { AgentVO } from '@/types/api'

const { t } = useI18n()
const activeTab = ref('documents')

const onTabChange = (name: string | number) => {
  if (name === 'deposits' && deposits.value.length === 0) {
    loadDeposits(1)
  }
}
const loading = ref(false)
const uploading = ref(false)
const searching = ref(false)
const searched = ref(false)
const documents = ref<KnowledgeDocumentVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const searchQuery = ref('')
const searchResults = ref<SearchResult[]>([])
const selectedKbId = ref<number | undefined>(undefined)
const knowledgeBases = ref<KnowledgeBaseVO[]>([])
const agents = ref<AgentVO[]>([])
const kbDialogVisible = ref(false)
const savingKb = ref(false)
const mountAgentId = ref<number | undefined>(undefined)
const mountKbId = ref<number | undefined>(undefined)
const agentKbs = ref<KnowledgeBaseVO[]>([])
const kbForm = reactive({ name: '', description: '', visibility: 'PRIVATE' })
const uploadRef = ref<UploadInstance>()

const triggerUpload = () => {
  // 触发 el-upload 内部的 input 点击
  const wrapper = (uploadRef.value as any)?.$el as HTMLElement | undefined
  const input = wrapper?.querySelector('input[type="file"]') as HTMLInputElement | null
  input?.click()
}

const statusText = (s: number) => s === 1 ? t('knowledge.ready') : s === 0 ? t('knowledge.pending') : t('knowledge.ingestFailed')
const formatFileSize = (bytes?: number) => { if (!bytes) return '-'; if (bytes < 1024) return bytes + 'B'; if (bytes < 1048576) return (bytes / 1024).toFixed(1) + 'KB'; return (bytes / 1048576).toFixed(1) + 'MB' }
const visibilityType = (v: string) => ({ PRIVATE: 'info', TEAM: 'warning', PUBLIC: 'success' }[v] || 'info')
const visibilityLabel = (v: string) => ({ PRIVATE: t('knowledgeBase.private'), TEAM: t('knowledgeBase.team'), PUBLIC: t('knowledgeBase.public') }[v] || v)

const loadAll = async () => { await Promise.all([loadDocuments(), loadKnowledgeBases()]) }

const loadDocuments = async () => {
  loading.value = true
  try {
    const res = await listDocuments({ kbId: selectedKbId.value, pageNum: pageNum.value, pageSize: pageSize.value })
    documents.value = res.data.list || []
    total.value = res.data.total || 0
  } finally { loading.value = false }
}

const loadKnowledgeBases = async () => {
  try {
    const res = await listKnowledgeBases()
    knowledgeBases.value = res.data || []
  } catch { knowledgeBases.value = [] }
}

const handleUpload = async (options: any) => {
  uploading.value = true
  try {
    await uploadDocument(options.file, undefined, selectedKbId.value)
    ElMessage.success(t('common.success'))
    await loadDocuments()
  } catch { /* interceptor handles */ } finally { uploading.value = false }
}

const handleDelete = async (row: KnowledgeDocumentVO) => {
  await ElMessageBox.confirm(t('knowledge.deleteConfirm'), '', { type: 'warning' })
  await deleteDocument(row.documentUuid)
  ElMessage.success(t('common.success'))
  await loadDocuments()
}

const handleSearch = async () => {
  if (!searchQuery.value.trim()) return
  searching.value = true; searched.value = true
  try {
    const res = await searchKnowledge(searchQuery.value)
    searchResults.value = res.data || []
  } finally { searching.value = false }
}

const openKbDialog = () => { kbForm.name = ''; kbForm.description = ''; kbForm.visibility = 'PRIVATE'; kbDialogVisible.value = true }

const handleCreateKb = async () => {
  if (!kbForm.name.trim()) { ElMessage.warning(t('knowledgeBase.name')); return }
  savingKb.value = true
  try { await createKnowledgeBase({ ...kbForm }); ElMessage.success(t('common.success')); kbDialogVisible.value = false; await loadKnowledgeBases() } finally { savingKb.value = false }
}

const handleDeleteKb = async (id: number) => {
  await ElMessageBox.confirm(t('knowledgeBase.deleteConfirm'), '', { type: 'warning' })
  await deleteKnowledgeBase(id); ElMessage.success(t('common.success')); await loadKnowledgeBases()
}

const loadAgentKbs = async () => {
  if (!mountAgentId.value) return
  const res = await getAgentKnowledgeBases(mountAgentId.value)
  agentKbs.value = res.data || []
}

const handleMount = async () => {
  if (!mountKbId.value || !mountAgentId.value) { ElMessage.warning(t('knowledgeBase.name')); return }
  await mountKnowledgeBase(mountAgentId.value, mountKbId.value); ElMessage.success(t('common.success')); await loadAgentKbs()
}

const handleUnmount = async (kbId: number) => {
  if (!mountAgentId.value) return
  await unmountKnowledgeBase(mountAgentId.value, kbId); ElMessage.success(t('common.success')); await loadAgentKbs()
}

// ==================== 知识沉淀审核（自维护 Wiki 飞轮） ====================
const deposits = ref<KnowledgeDepositVO[]>([])
const depositsLoading = ref(false)
const depositStatusFilter = ref('PENDING')
const depositPageNum = ref(1)
const depositPageSize = 10
const depositTotal = ref(0)
const depositDetailVisible = ref(false)
const depositDetail = ref<KnowledgeDepositVO | null>(null)

const depositStatusType = (s: string) =>
  ({ PENDING: 'warning', APPROVED: 'success', REJECTED: 'info' }[s] || 'info')

const depositStatusLabel = (s: string) =>
  ({
    PENDING: t('knowledge.depositPending'),
    APPROVED: t('knowledge.depositApproved'),
    REJECTED: t('knowledge.depositRejected')
  }[s] || s)

const loadDeposits = async (page = depositPageNum.value) => {
  depositsLoading.value = true
  try {
    const res = await listKnowledgeDeposits({
      status: depositStatusFilter.value || undefined,
      pageNum: page,
      pageSize: depositPageSize
    })
    deposits.value = res.data.list || []
    depositTotal.value = res.data.total || 0
    depositPageNum.value = page
  } finally {
    depositsLoading.value = false
  }
}

const viewDeposit = (row: KnowledgeDepositVO) => {
  depositDetail.value = row
  depositDetailVisible.value = true
}

const handleReviewDeposit = async (row: KnowledgeDepositVO, approved: boolean) => {
  let comment = ''
  if (!approved) {
    try {
      const { value } = await ElMessageBox.prompt(t('knowledge.depositRejectPrompt'), t('knowledge.depositRejectBtn'), {
        inputPlaceholder: t('knowledge.depositComment')
      })
      comment = value || ''
    } catch {
      return
    }
  }
  try {
    await reviewKnowledgeDeposit(row.id, approved, comment)
    ElMessage.success(approved ? t('knowledge.depositApprovedDone') : t('knowledge.depositRejectedDone'))
    await loadDeposits(depositPageNum.value)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message ?? t('common.saveFailed'))
  }
}

onMounted(() => {
  loadAll()
  listAgents({ pageNum: 1, pageSize: 100 }).then(res => { agents.value = res.data.list || [] }).catch(() => {})
})
</script>

<style scoped lang="scss">
.knowledge-page { padding: 0; }
.knowledge-tabs { margin-top: 8px; }
.kb-selector { display: flex; align-items: center; gap: 8px; margin-bottom: 16px; }
.kb-label { font-size: 14px; color: var(--lumina-text-secondary); white-space: nowrap; }
.upload-area { width: 100%; }
.upload-progress { display: flex; align-items: center; gap: 8px; color: var(--lumina-text-secondary); margin-top: 8px; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
.search-bar { display: flex; gap: 8px; margin-bottom: 16px; }
.deposit-pagination { margin-top: 12px; display: flex; justify-content: flex-end; }
.deposit-content {
  max-height: 260px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-all;
  font-size: 13px;
  line-height: 1.6;
}
.search-results { display: flex; flex-direction: column; gap: 12px; }
.search-item { border: 1px solid var(--lumina-border); border-radius: var(--lumina-radius-sm); padding: 12px; }
.search-meta { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.score { color: var(--lumina-primary-light); font-size: 13px; font-weight: 600; }
.search-content { color: var(--lumina-text-regular); font-size: 13px; line-height: 1.6; }
.card-header { display: flex; justify-content: flex-end; }
.upload-tip { color: var(--lumina-text-muted); font-size: 12px; }

@media (max-width: 768px) {
  :deep(.el-col) { max-width: 100%; flex: 0 0 100%; }
  :deep(.el-form--inline .el-form-item) { display: block; margin-right: 0; }
  :deep(.el-table) { font-size: 12px; }
}
</style>
