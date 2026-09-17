<template>
  <div class="skill-page">
    <PageHeader :title="t('skill.title')" :description="t('skill.description')" />

    <el-alert
      type="info"
      show-icon
      :closable="false"
      style="margin-bottom: var(--lumina-spacing-md)"
      :title="t('skill.howTitle')"
      :description="t('skill.howDesc')"
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
        <el-button type="primary" @click="showCreateDialog">{{ t('skill.create') }}</el-button>
        <el-button @click="importVisible = true">{{ t('skill.importAction') }}</el-button>
        <el-button :loading="exportingAll" @click="handleExportAll">{{ t('skill.exportAll') }}</el-button>
      </template>

      <el-table-column prop="name" :label="t('skill.name')" min-width="150">
        <template #default="{ row }">
          <span class="skill-name">{{ row.name }}</span>
        </template>
      </el-table-column>
      <el-table-column :label="t('skill.enabledCol')" width="85">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
            {{ row.enabled ? t('skill.enabledOn') : t('skill.enabledOff') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('skill.scanCol')" width="120">
        <template #default="{ row }">
          <el-tooltip v-if="scanFindings(row)" :content="scanFindings(row)" placement="top">
            <el-tag :type="scanTagType(row.scanStatus)" size="small">{{ scanText(row.scanStatus) }}</el-tag>
          </el-tooltip>
          <el-tag v-else :type="scanTagType(row.scanStatus)" size="small">{{ scanText(row.scanStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="description" :label="t('common.description')" min-width="200" show-overflow-tooltip />
      <el-table-column prop="whenToUse" :label="t('skill.whenToUse')" min-width="150" show-overflow-tooltip />
      <el-table-column prop="updateTime" :label="t('skill.updateTime')" width="160">
        <template #default="{ row }">{{ formatDate(row.updateTime) }}</template>
      </el-table-column>
      <el-table-column :label="t('common.actions')" width="300" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="showEditDialog(row)">{{ t('common.edit') }}</el-button>
          <el-button size="small" :type="row.enabled ? 'warning' : 'success'" @click="handleToggle(row)">
            {{ row.enabled ? t('skill.disable') : t('skill.enable') }}
          </el-button>
          <el-button size="small" @click="handleExport(row)">{{ t('skill.exportOne') }}</el-button>
          <el-button size="small" @click="handleRescan(row)">{{ t('skill.rescan') }}</el-button>
          <el-button size="small" type="danger" @click="handleDelete(row)">{{ t('common.delete') }}</el-button>
        </template>
      </el-table-column>
    </LumTablePanel>

    <!-- 新建/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="800px" :close-on-click-modal="false">
      <el-form :model="formData" label-width="110px">
        <el-form-item :label="t('skill.name')" required>
          <el-input
            v-model="formData.name"
            :disabled="!!editingId"
            :placeholder="t('skill.namePlaceholder')"
          />
        </el-form-item>
        <el-form-item :label="t('common.description')" required>
          <el-input v-model="formData.description" :placeholder="t('skill.descPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('skill.whenToUse')">
          <el-input v-model="formData.whenToUse" :placeholder="t('skill.whenToUsePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('skill.content')" required>
          <el-input
            v-model="formData.content"
            type="textarea"
            :rows="14"
            :placeholder="t('skill.contentPlaceholder')"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <!-- 导入 SKILL.md 对话框 -->
    <el-dialog v-model="importVisible" :title="t('skill.importAction')" width="640px" :close-on-click-modal="false">
      <el-alert
        type="warning"
        show-icon
        :closable="false"
        style="margin-bottom: var(--lumina-spacing-md)"
        :title="t('skill.scanTitle')"
        :description="t('skill.scanDesc')"
      />
      <el-upload
        drag
        :auto-upload="true"
        :show-file-list="false"
        :http-request="handleImport"
        accept=".md,.markdown,.zip"
        class="skill-import-upload"
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">{{ t('skill.importDragTip') }}</div>
        <template #tip>
          <div class="el-upload__tip">{{ t('skill.importFormatTip') }}</div>
        </template>
      </el-upload>

      <div v-if="importResult" class="import-result">
        <el-divider content-position="left">{{ t('skill.importResult') }}</el-divider>
        <div v-for="item in importResult.imported" :key="item.id" class="import-row import-ok">
          <el-tag size="small" type="success">{{ item.name }}</el-tag>
          <el-tag size="small" :type="item.scanStatus === 'PASSED' ? 'success' : 'warning'">
            {{ scanText(item.scanStatus) }}
          </el-tag>
          <span class="import-note">{{ item.enabled ? t('skill.enabledOn') : t('skill.flaggedNote') }}</span>
        </div>
        <div v-for="item in importResult.rejected" :key="item.name" class="import-row import-rejected">
          <el-tag size="small" type="danger">{{ item.name }}</el-tag>
          <span class="import-note">{{ item.reason }}</span>
        </div>
      </div>

      <template #footer>
        <el-button type="primary" @click="closeImport">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { PageHeader, LumTablePanel, type SearchField } from '@/components/common'
import {
  listSkills,
  createSkill,
  updateSkill,
  setSkillEnabled,
  deleteSkill,
  importSkills,
  rescanSkill,
  exportSkill,
  exportAllSkills,
  type SkillVO,
  type SkillDTO,
  type SkillImportResult
} from '@/api/modules/skill'

const { t } = useI18n()

const list = ref<SkillVO[]>([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const importVisible = ref(false)
const importResult = ref<SkillImportResult | null>(null)
const exportingAll = ref(false)

const queryForm = reactive({ name: '' })
const formData = reactive<SkillDTO>({
  name: '',
  description: '',
  whenToUse: '',
  content: '',
  enabled: true
})

const searchFields = computed<SearchField[]>(() => [
  { prop: 'name', label: t('skill.name'), type: 'input', placeholder: t('common.pleaseInput') }
])

const dialogTitle = computed(() => (editingId.value ? t('skill.edit') : t('skill.create')))

const formatDate = (val?: string) => (val ? new Date(val).toLocaleString() : '-')

const scanTagType = (s?: string) =>
  ({ PASSED: 'success', FLAGGED: 'warning', REJECTED: 'danger', NONE: 'info' }[s ?? 'NONE'] || 'info')

const scanText = (s?: string) =>
  ({
    PASSED: t('skill.scanPASSED'),
    FLAGGED: t('skill.scanFLAGGED'),
    REJECTED: t('skill.scanREJECTED'),
    NONE: t('skill.scanNONE')
  }[s ?? 'NONE'] || s || '-')

/** 体检报告 findings 摘要（悬浮展示） */
const scanFindings = (row: SkillVO): string => {
  if (!row.scanReport) return ''
  try {
    const report = JSON.parse(row.scanReport)
    if (!Array.isArray(report.findings) || report.findings.length === 0) return ''
    return report.findings
      .map((f: { severity: string; rule: string }) => `${f.severity}: ${f.rule}`)
      .join('\n')
  } catch {
    return ''
  }
}

async function loadList() {
  loading.value = true
  try {
    const res = await listSkills({ name: queryForm.name || undefined, pageSize: 100 })
    list.value = res.data || []
  } catch (e) {
    ElMessage.error(t('common.loadFailed'))
  } finally {
    loading.value = false
  }
}

function handleReset() {
  queryForm.name = ''
  loadList()
}

function showCreateDialog() {
  editingId.value = null
  Object.assign(formData, { name: '', description: '', whenToUse: '', content: '', enabled: true })
  dialogVisible.value = true
}

function showEditDialog(row: SkillVO) {
  editingId.value = row.id
  Object.assign(formData, {
    name: row.name,
    description: row.description,
    whenToUse: row.whenToUse ?? '',
    content: row.content,
    enabled: row.enabled
  })
  dialogVisible.value = true
}

async function handleSave() {
  if (!formData.name || !formData.description || !formData.content) {
    ElMessage.warning(t('skill.formRequired'))
    return
  }
  saving.value = true
  try {
    if (editingId.value) {
      await updateSkill(editingId.value, formData)
    } else {
      await createSkill(formData)
    }
    ElMessage.success(t('common.saveSuccess'))
    dialogVisible.value = false
    loadList()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message ?? t('common.saveFailed'))
  } finally {
    saving.value = false
  }
}

async function handleToggle(row: SkillVO) {
  try {
    await setSkillEnabled(row.id, !row.enabled)
    ElMessage.success(t('common.saveSuccess'))
    loadList()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message ?? t('common.saveFailed'))
  }
}

async function handleDelete(row: SkillVO) {
  try {
    await ElMessageBox.confirm(t('skill.deleteConfirm', { name: row.name }), t('common.confirm'), {
      type: 'warning'
    })
    await deleteSkill(row.id)
    ElMessage.success(t('common.deleteSuccess'))
    loadList()
  } catch {
    // 用户取消
  }
}

// ==================== 导入 / 导出 / 体检（SKILL.md 开放标准） ====================

async function handleImport(option: { file: File }) {
  try {
    const res = await importSkills(option.file)
    importResult.value = res.data
    const ok = res.data?.imported?.length ?? 0
    const bad = res.data?.rejected?.length ?? 0
    ElMessage.success(t('skill.importDone', { ok, bad }))
    loadList()
  } catch (e: any) {
    ElMessage.error(e?.message ?? t('common.saveFailed'))
  }
}

function closeImport() {
  importVisible.value = false
  importResult.value = null
}

function triggerDownload(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  URL.revokeObjectURL(url)
}

async function handleExport(row: SkillVO) {
  try {
    const res: any = await exportSkill(row.id)
    triggerDownload(res.data as Blob, `${row.name}.SKILL.md`)
  } catch (e: any) {
    ElMessage.error(e?.message ?? t('common.saveFailed'))
  }
}

async function handleExportAll() {
  exportingAll.value = true
  try {
    const res: any = await exportAllSkills()
    triggerDownload(res.data as Blob, 'lumina-skills.zip')
  } catch (e: any) {
    ElMessage.error(e?.message ?? t('common.saveFailed'))
  } finally {
    exportingAll.value = false
  }
}

async function handleRescan(row: SkillVO) {
  try {
    const res = await rescanSkill(row.id)
    const status = res.data?.scanStatus ?? 'NONE'
    ElMessage.success(`${t('skill.rescanDone')}: ${scanText(status)}`)
    loadList()
  } catch (e: any) {
    ElMessage.error(e?.message ?? t('common.saveFailed'))
  }
}

onMounted(loadList)
</script>

<style scoped>
.skill-name {
  font-family: var(--lumina-font-mono, monospace);
}

.skill-import-upload {
  width: 100%;
}

.import-result {
  max-height: 240px;
  overflow-y: auto;
  padding: 0 var(--lumina-spacing-xs, 4px);
}

.import-row {
  display: flex;
  align-items: center;
  gap: var(--lumina-spacing-sm, 8px);
  padding: var(--lumina-spacing-xs, 4px) 0;
}

.import-note {
  color: var(--lumina-text-secondary);
  font-size: 12px;
}
</style>
