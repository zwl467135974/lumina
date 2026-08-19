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
      </template>

      <el-table-column prop="name" :label="t('skill.name')" min-width="160">
        <template #default="{ row }">
          <span class="skill-name">{{ row.name }}</span>
        </template>
      </el-table-column>
      <el-table-column :label="t('skill.enabledCol')" width="90">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
            {{ row.enabled ? t('skill.enabledOn') : t('skill.enabledOff') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="description" :label="t('common.description')" min-width="220" show-overflow-tooltip />
      <el-table-column prop="whenToUse" :label="t('skill.whenToUse')" min-width="160" show-overflow-tooltip />
      <el-table-column prop="updateTime" :label="t('skill.updateTime')" width="170">
        <template #default="{ row }">{{ formatDate(row.updateTime) }}</template>
      </el-table-column>
      <el-table-column :label="t('common.actions')" width="220" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="showEditDialog(row)">{{ t('common.edit') }}</el-button>
          <el-button size="small" :type="row.enabled ? 'warning' : 'success'" @click="handleToggle(row)">
            {{ row.enabled ? t('skill.disable') : t('skill.enable') }}
          </el-button>
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
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import LumTablePanel from '@/components/LumTablePanel.vue'
import {
  listSkills,
  createSkill,
  updateSkill,
  setSkillEnabled,
  deleteSkill,
  type SkillVO,
  type SkillDTO
} from '@/api/modules/skill'

const { t } = useI18n()

const list = ref<SkillVO[]>([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)

const queryForm = reactive({ name: '' })
const formData = reactive<SkillDTO>({
  name: '',
  description: '',
  whenToUse: '',
  content: '',
  enabled: true
})

const searchFields = [
  { prop: 'name', label: t('skill.name'), type: 'input', placeholder: t('common.pleaseInput') }
]

const dialogTitle = computed(() => (editingId.value ? t('skill.edit') : t('skill.create')))

const formatDate = (val?: string) => (val ? new Date(val).toLocaleString() : '-')

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

onMounted(loadList)
</script>

<style scoped>
.skill-name {
  font-family: var(--lumina-font-mono, monospace);
}
</style>
