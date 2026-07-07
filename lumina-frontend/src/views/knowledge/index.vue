<template>
  <div class="knowledge-page">
    <div class="page-header">
      <h2>{{ t('knowledge.title') }}</h2>
      <div class="actions">
        <el-button @click="loadDocuments" :loading="loading">{{ t('common.refresh') }}</el-button>
      </div>
    </div>

    <!-- 上传区 -->
    <el-card shadow="never" class="section-card">
      <template #header><span>{{ t('knowledge.upload') }}</span></template>
      <el-upload
        ref="uploadRef"
        :auto-upload="true"
        :show-file-list="false"
        :http-request="handleUpload"
        accept=".txt,.md,.pdf,.doc,.docx"
        drag
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">
          拖拽文件到此处，或<em>点击上传</em>
        </div>
        <template #tip>
          <div class="upload-tip">{{ t('knowledge.formatTip') }}</div>
        </template>
      </el-upload>
      <div v-if="uploading" class="upload-progress">
        <el-icon class="is-loading"><Loading /></el-icon>
        正在上传并处理...
      </div>
    </el-card>

    <!-- 文档列表 -->
    <el-card shadow="never" class="section-card">
      <template #header><span>{{ t('knowledge.documents') }}（共 {{ total }} 条）</span></template>
      <el-table :data="documents" v-loading="loading" stripe size="small">
        <el-table-column prop="title" :label="t('knowledge.documentTitle')" min-width="200" show-overflow-tooltip />
        <el-table-column :label="t('knowledge.format')" width="90">
          <template #default="{ row }">
            <el-tag :type="formatTagType(row.format)" size="small">{{ row.format }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="chunkCount" label="切片数" width="90" />
        <el-table-column :label="t('knowledge.fileSize')" width="100">
          <template #default="{ row }">{{ formatFileSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column :label="t('common.status')" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'warning'" size="small">
              {{ statusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="上传时间" width="170">
          <template #default="{ row }">{{ row.createTime }}</template>
        </el-table-column>
        <el-table-column :label="t('common.actions')" width="80" fixed="right">
          <template #default="{ row }">
            <el-button type="danger" link size="small" @click="handleDelete(row)">{{ t('common.delete') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap" v-if="total > 0">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="loadDocuments"
          @current-change="loadDocuments"
        />
      </div>
      <el-empty v-if="!loading && documents.length === 0" description="暂无知识文档，请先上传" :image-size="60" />
    </el-card>

    <!-- 检索测试 -->
    <el-card shadow="never" class="section-card">
      <template #header><span>{{ t('knowledge.search') }}</span></template>
      <div class="search-bar">
        <el-input
          v-model="searchQuery"
          :placeholder="t('knowledge.searchPlaceholder')"
          clearable
          @keyup.enter="handleSearch"
        />
        <el-button type="primary" @click="handleSearch" :loading="searching">检索</el-button>
      </div>
      <div v-if="searchResults.length > 0" class="search-results">
        <div v-for="(item, idx) in searchResults" :key="idx" class="search-item">
          <div class="search-meta">
            <el-tag size="small" type="primary">#{{ idx + 1 }}</el-tag>
            <span class="score">相关度: {{ (item.score * 100).toFixed(1) }}%</span>
          </div>
          <div class="search-content">{{ item.content }}</div>
        </div>
      </div>
      <el-empty v-if="searched && searchResults.length === 0" description="未找到相关内容" :image-size="60" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled, Loading } from '@element-plus/icons-vue'
import {
  uploadDocument,
  listDocuments,
  deleteDocument,
  searchKnowledge,
  type KnowledgeDocumentVO,
  type SearchResult
} from '@/api/modules/knowledge'

const { t } = useI18n()

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

const formatTagType = (format: string) => {
  const map: Record<string, string> = {
    pdf: 'danger',
    doc: 'primary',
    docx: 'primary',
    md: 'success',
    txt: 'info'
  }
  return map[format] || 'info'
}

const statusText = (status: number) => {
  const map: Record<number, string> = { 1: '正常', 0: '处理中', [-1]: '失败' }
  return map[status] || '未知'
}

const formatFileSize = (bytes?: number) => {
  if (!bytes) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

const loadDocuments = async () => {
  loading.value = true
  try {
    const res = await listDocuments({ pageNum: pageNum.value, pageSize: pageSize.value })
    documents.value = res.data?.list || []
    total.value = res.data?.total || 0
  } catch (e: any) {
    ElMessage.error(e.message || '加载文档列表失败')
  } finally {
    loading.value = false
  }
}

const handleUpload = async (options: { file: File }) => {
  const file = options.file
  if (file.size > 50 * 1024 * 1024) {
    ElMessage.error('文件大小不能超过 50MB')
    return
  }

  uploading.value = true
  try {
    await uploadDocument(file)
    ElMessage.success(`文档「${file.name}」上传成功`)
    loadDocuments()
  } catch (e: any) {
    ElMessage.error(e.message || '上传失败')
  } finally {
    uploading.value = false
  }
}

const handleDelete = async (row: KnowledgeDocumentVO) => {
  try {
    await ElMessageBox.confirm(`确认删除文档「${row.title}」？关联的向量数据也将一并清理。`, t('common.tip'), {
      type: 'warning'
    })
    await deleteDocument(row.documentUuid)
    ElMessage.success('已删除')
    loadDocuments()
  } catch {
    // 取消
  }
}

const handleSearch = async () => {
  if (!searchQuery.value.trim()) {
    ElMessage.warning('请输入检索内容')
    return
  }
  searching.value = true
  searched.value = true
  try {
    const res = await searchKnowledge(searchQuery.value, 5)
    searchResults.value = res.data || []
  } catch (e: any) {
    ElMessage.error(e.message || '检索失败')
    searchResults.value = []
  } finally {
    searching.value = false
  }
}

onMounted(loadDocuments)
</script>

<style scoped lang="scss">
.knowledge-page {
  padding: 16px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;

  h2 {
    margin: 0;
    font-size: 18px;
  }
}

.section-card {
  margin-bottom: 16px;
}

.upload-progress {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 12px;
  color: var(--el-text-color-secondary);
}

.upload-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  text-align: center;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

.search-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.search-results {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.search-item {
  padding: 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;

  .search-meta {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 6px;

    .score {
      font-size: 12px;
      color: var(--el-text-color-secondary);
    }
  }

  .search-content {
    font-size: 13px;
    line-height: 1.6;
    color: var(--el-text-color-regular);
    white-space: pre-wrap;
    word-break: break-all;
  }
}
</style>
