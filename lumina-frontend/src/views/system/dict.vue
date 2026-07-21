<template>
  <div class="system-dict-page">
    <PageHeader :title="t('system.dict.title')" :description="t('system.dict.description')" />

    <el-row :gutter="16">
      <!-- 左侧：字典类型 -->
      <el-col :xs="24" :sm="10" :md="10">
        <el-card shadow="never">
          <template #header>
            <div style="display: flex; justify-content: space-between; align-items: center">
              <span>{{ t('system.dict.dictType') }}</span>
              <el-button type="primary" size="small" @click="handleCreateType">{{ t('system.dict.addType') }}</el-button>
            </div>
          </template>
          <el-table
            :data="typeList"
            v-loading="typeLoading"
            highlight-current-row
            @current-change="handleTypeSelect"
            size="small"
          >
            <el-table-column prop="dictName" :label="t('system.dict.dictLabel')" min-width="100" />
            <el-table-column prop="dictType" :label="t('system.dict.dictType')" min-width="120" show-overflow-tooltip />
            <el-table-column :label="t('common.actions')" width="150" fixed="right">
              <template #default="{ row }">
                <div class="dict-actions">
                  <el-button link type="primary" size="small" @click.stop="handleEditType(row)">{{ t('common.edit') }}</el-button>
                  <el-button link type="danger" size="small" @click.stop="handleDeleteType(row)">{{ t('common.delete') }}</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <!-- 右侧：字典项 -->
      <el-col :xs="24" :sm="14" :md="14">
        <el-card shadow="never">
          <template #header>
            <div style="display: flex; justify-content: space-between; align-items: center">
              <span>{{ selectedType ? `${selectedType.dictName} (${selectedType.dictType})` : t('system.dict.dictValue') }}</span>
              <el-button type="primary" size="small" :disabled="!selectedType" @click="handleCreateItem">{{ t('system.dict.addItem') }}</el-button>
            </div>
          </template>
          <el-table :data="itemList" v-loading="itemLoading" stripe size="small">
            <el-table-column prop="dictLabel" :label="t('system.dict.dictLabel')" min-width="120" />
            <el-table-column prop="dictValue" :label="t('system.dict.dictValue')" min-width="120" />
            <el-table-column prop="sortOrder" :label="t('system.dict.sort')" width="60" />
            <el-table-column prop="status" :label="t('common.status')" width="70">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
                  {{ row.status === 1 ? t('common.enable') : t('common.disable') }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="remark" :label="t('system.dict.remark')" min-width="100" show-overflow-tooltip />
            <el-table-column :label="t('common.actions')" width="150" fixed="right">
              <template #default="{ row }">
                <div class="dict-actions">
                  <el-button link type="primary" size="small" @click="handleEditItem(row)">{{ t('common.edit') }}</el-button>
                  <el-button link type="danger" size="small" @click="handleDeleteItem(row)">{{ t('common.delete') }}</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!selectedType" :description="t('common.noData')" :image-size="60" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 字典类型 Dialog -->
    <el-dialog v-model="typeDialogVisible" :title="typeDialogTitle" width="500px">
      <el-form :model="typeForm" label-width="100px">
        <el-form-item :label="t('system.dict.dictType')" required>
          <el-input v-model="typeForm.dictType" :placeholder="t('system.dict.typePlaceholder')" :disabled="isEditType" />
        </el-form-item>
        <el-form-item :label="t('system.dict.dictLabel')" required>
          <el-input v-model="typeForm.dictName" :placeholder="t('system.dict.labelPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('common.status')">
          <el-switch v-model="typeForm.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item :label="t('system.dict.remark')">
          <el-input v-model="typeForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="typeDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleSaveType" :loading="typeSaving">{{ t('common.ok') }}</el-button>
      </template>
    </el-dialog>

    <!-- 字典项 Dialog -->
    <el-dialog v-model="itemDialogVisible" :title="itemDialogTitle" width="500px">
      <el-form :model="itemForm" label-width="100px">
        <el-form-item :label="t('system.dict.dictLabel')" required>
          <el-input v-model="itemForm.dictLabel" :placeholder="t('system.dict.labelPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('system.dict.dictValue')" required>
          <el-input v-model="itemForm.dictValue" :placeholder="t('system.dict.valuePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('system.dict.sort')">
          <el-input-number v-model="itemForm.sortOrder" :min="0" />
        </el-form-item>
        <el-form-item :label="t('common.status')">
          <el-switch v-model="itemForm.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item :label="t('system.dict.remark')">
          <el-input v-model="itemForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="itemDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleSaveItem" :loading="itemSaving">{{ t('common.ok') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">

defineOptions({ name: 'SystemDict' })
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listDictTypes, createDictType, updateDictType, deleteDictType,
  listDictItems, createDictItem, updateDictItem, deleteDictItem,
  type DictTypeVO, type DictItemVO
} from '@/api/modules/system-dict'
import { PageHeader } from '@/components/common'

const { t } = useI18n()

// 字典类型
const typeLoading = ref(false)
const typeList = ref<DictTypeVO[]>([])
const selectedType = ref<DictTypeVO | null>(null)

const loadTypes = async () => {
  typeLoading.value = true
  try {
    const res = await listDictTypes()
    typeList.value = res.data || []
  } finally {
    typeLoading.value = false
  }
}

const handleTypeSelect = (row: DictTypeVO | null) => {
  selectedType.value = row
  if (row) loadItems(row.dictType)
}

// 字典项
const itemLoading = ref(false)
const itemList = ref<DictItemVO[]>([])

const loadItems = async (dictType: string) => {
  itemLoading.value = true
  try {
    const res = await listDictItems(dictType)
    itemList.value = res.data || []
  } finally {
    itemLoading.value = false
  }
}

// 类型 Dialog
const typeDialogVisible = ref(false)
const typeDialogTitle = ref('')
const isEditType = ref(false)
const editingTypeId = ref<number | null>(null)
const typeSaving = ref(false)
const typeForm = reactive({ dictType: '', dictName: '', status: 1, remark: '' })

const handleCreateType = () => {
  typeDialogTitle.value = t('system.dict.addType')
  isEditType.value = false
  editingTypeId.value = null
  Object.assign(typeForm, { dictType: '', dictName: '', status: 1, remark: '' })
  typeDialogVisible.value = true
}

const handleEditType = (row: DictTypeVO) => {
  typeDialogTitle.value = t('system.dict.editType')
  isEditType.value = true
  editingTypeId.value = row.id
  Object.assign(typeForm, { dictType: row.dictType, dictName: row.dictName, status: row.status, remark: row.remark || '' })
  typeDialogVisible.value = true
}

const handleSaveType = async () => {
  typeSaving.value = true
  try {
    if (isEditType.value && editingTypeId.value) {
      await updateDictType(editingTypeId.value, typeForm)
      ElMessage.success(t('common.updateSuccess'))
    } else {
      await createDictType(typeForm)
      ElMessage.success(t('common.createSuccess'))
    }
    typeDialogVisible.value = false
    loadTypes()
  } finally {
    typeSaving.value = false
  }
}

const handleDeleteType = async (row: DictTypeVO) => {
  try {
    await ElMessageBox.confirm(t('system.dict.deleteTypeConfirm', { type: row.dictType }), t('common.tip'), { type: 'warning' })
    await deleteDictType(row.id)
    ElMessage.success(t('common.deleteSuccess'))
    if (selectedType.value?.id === row.id) {
      selectedType.value = null
      itemList.value = []
    }
    loadTypes()
  } catch { /* cancelled */ }
}

// 项 Dialog
const itemDialogVisible = ref(false)
const itemDialogTitle = ref('')
const editingItemId = ref<number | null>(null)
const itemSaving = ref(false)
const itemForm = reactive({ dictLabel: '', dictValue: '', sortOrder: 0, status: 1, remark: '' })

const handleCreateItem = () => {
  itemDialogTitle.value = t('system.dict.addItem')
  editingItemId.value = null
  Object.assign(itemForm, { dictLabel: '', dictValue: '', sortOrder: 0, status: 1, remark: '' })
  itemDialogVisible.value = true
}

const handleEditItem = (row: DictItemVO) => {
  itemDialogTitle.value = t('system.dict.editItem')
  editingItemId.value = row.id
  Object.assign(itemForm, { dictLabel: row.dictLabel, dictValue: row.dictValue, sortOrder: row.sortOrder, status: row.status, remark: row.remark || '' })
  itemDialogVisible.value = true
}

const handleSaveItem = async () => {
  if (!selectedType.value) return
  itemSaving.value = true
  try {
    const data = { ...itemForm, dictType: selectedType.value.dictType }
    if (editingItemId.value) {
      await updateDictItem(editingItemId.value, data)
      ElMessage.success(t('common.updateSuccess'))
    } else {
      await createDictItem(data)
      ElMessage.success(t('common.createSuccess'))
    }
    itemDialogVisible.value = false
    loadItems(selectedType.value.dictType)
  } finally {
    itemSaving.value = false
  }
}

const handleDeleteItem = async (row: DictItemVO) => {
  try {
    await ElMessageBox.confirm(t('system.dict.deleteItemConfirm', { label: row.dictLabel }), t('common.tip'), { type: 'warning' })
    await deleteDictItem(row.id)
    ElMessage.success(t('common.deleteSuccess'))
    if (selectedType.value) loadItems(selectedType.value.dictType)
  } catch { /* cancelled */ }
}

onMounted(() => loadTypes())
</script>

<style scoped>
.dict-actions {
  display: inline-flex;
  align-items: center;
  gap: var(--lumina-spacing-xs);
  white-space: nowrap;
}

.dict-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

@media (max-width: 767px) {
  .system-dict-page :deep(.el-card) {
    margin-bottom: var(--lumina-spacing-md);
  }
}
</style>
