<template>
  <div class="system-menu-page">
    <PageHeader :title="t('system.menu.title')" :description="t('system.menu.description')" />

    <el-card shadow="never">
      <div style="margin-bottom: 16px; display: flex; gap: 8px">
        <el-button type="primary" @click="handleCreate(null)">
          <el-icon><Plus /></el-icon>
          {{ t('system.menu.addTop') }}
        </el-button>
        <el-button @click="expandAll">{{ t('system.menu.expandAll') }}</el-button>
        <el-button @click="collapseAll">{{ t('system.menu.collapseAll') }}</el-button>
      </div>

      <el-table
        :data="menuTree"
        v-loading="loading"
        row-key="permissionId"
        :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
        :default-expand-all="false"
        ref="tableRef"
        stripe
      >
        <el-table-column prop="permissionName" :label="t('system.menu.name')" width="200" />
        <el-table-column prop="icon" :label="t('system.menu.icon')" width="80">
          <template #default="{ row }">
            <el-icon v-if="row.icon"><component :is="row.icon" /></el-icon>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="resourcePath" :label="t('system.menu.path')" min-width="180" show-overflow-tooltip />
        <el-table-column prop="sortOrder" :label="t('system.menu.sort')" width="80" />
        <el-table-column prop="status" :label="t('common.status')" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? t('common.enable') : t('common.disable') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.actions')" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleCreate(row)">{{ t('system.menu.addChild') }}</el-button>
            <el-button link type="primary" @click="handleEdit(row)">{{ t('common.edit') }}</el-button>
            <el-button link type="danger" @click="handleDelete(row)">{{ t('common.delete') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 创建/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px" @close="handleDialogClose">
      <el-form :model="formData" :rules="formRules" ref="formRef" label-width="100px">
        <el-form-item :label="t('system.menu.parentMenu')">
          <el-input :model-value="formData.parentName" disabled />
        </el-form-item>
        <el-form-item :label="t('system.menu.name')" prop="permissionName" required>
          <el-input v-model="formData.permissionName" :placeholder="t('system.menu.namePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('system.menu.icon')" prop="icon">
          <el-input v-model="formData.icon" placeholder="如 Odometer / Setting / User" />
        </el-form-item>
        <el-form-item :label="t('system.menu.path')" prop="resourcePath">
          <el-input v-model="formData.resourcePath" placeholder="如 /system/user" />
        </el-form-item>
        <el-form-item :label="t('system.menu.code')" prop="permissionCode" required>
          <el-input v-model="formData.permissionCode" placeholder="如 system:user" />
        </el-form-item>
        <el-form-item :label="t('system.menu.sort')" prop="sortOrder">
          <el-input-number v-model="formData.sortOrder" :min="0" :max="999" />
        </el-form-item>
        <el-form-item :label="t('common.status')">
          <el-switch v-model="formData.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="saving">{{ t('common.ok') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import {
  getPermissionTree, createPermission, updatePermission, deletePermission
} from '@/api/modules/system-permission'
import type { PermissionVO } from '@/types/api'
import { PageHeader } from '@/components/common'

const { t } = useI18n()
const loading = ref(false)
const tableRef = ref()
const menuTree = ref<PermissionVO[]>([])

const loadData = async () => {
  loading.value = true
  try {
    const res = await getPermissionTree()
    const filterMenu = (items: PermissionVO[]): PermissionVO[] => {
      return items
        .filter(item => item.type === 1)
        .map(item => ({
          ...item,
          children: item.children ? filterMenu(item.children) : []
        }))
    }
    menuTree.value = filterMenu(res.data || [])
  } catch {
    menuTree.value = []
  } finally {
    loading.value = false
  }
}

const expandAll = () => {
  toggleAll(true)
}
const collapseAll = () => {
  toggleAll(false)
}
const toggleAll = (expand: boolean) => {
  const toggle = (data: PermissionVO[]) => {
    data.forEach(item => {
      tableRef.value?.toggleRowExpansion(item, expand)
      if (item.children) toggle(item.children)
    })
  }
  toggle(menuTree.value)
}

// Dialog
const dialogVisible = ref(false)
const dialogTitle = ref('')
const isEdit = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const saving = ref(false)

const formData = reactive({
  parentId: 0,
  parentName: '',
  permissionName: '',
  permissionCode: '',
  icon: '',
  resourcePath: '',
  sortOrder: 99,
  status: 1,
  type: 1
})

const formRules: FormRules = {
  permissionName: [{ required: true, message: t('system.menu.nameRequired'), trigger: 'blur' }],
  permissionCode: [{ required: true, message: t('system.menu.codeRequired'), trigger: 'blur' }]
}

const handleCreate = (parent: PermissionVO | null) => {
  dialogTitle.value = t('system.menu.create')
  isEdit.value = false
  editingId.value = null
  formData.parentId = parent?.permissionId || 0
  formData.parentName = parent?.permissionName || t('system.menu.topLevel')
  formData.permissionName = ''
  formData.permissionCode = ''
  formData.icon = ''
  formData.resourcePath = ''
  formData.sortOrder = 99
  formData.status = 1
  formData.type = 1
  dialogVisible.value = true
}

const handleEdit = (row: PermissionVO) => {
  dialogTitle.value = t('system.menu.edit')
  isEdit.value = true
  editingId.value = row.permissionId
  formData.parentId = row.parentId || 0
  formData.parentName = t('system.menu.topLevel')
  formData.permissionName = row.permissionName
  formData.permissionCode = row.permissionCode
  formData.icon = row.icon || ''
  formData.resourcePath = row.resourcePath || ''
  formData.sortOrder = row.sortOrder || 99
  formData.status = row.status ?? 1
  formData.type = 1
  dialogVisible.value = true
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      if (isEdit.value && editingId.value) {
        await updatePermission(editingId.value, formData as any)
        ElMessage.success(t('common.updateSuccess'))
      } else {
        await createPermission(formData as any)
        ElMessage.success(t('common.createSuccess'))
      }
      dialogVisible.value = false
      loadData()
    } catch {
      // handled by interceptor
    } finally {
      saving.value = false
    }
  })
}

const handleDialogClose = () => {
  formRef.value?.resetFields()
  editingId.value = null
}

const handleDelete = async (row: PermissionVO) => {
  try {
    await ElMessageBox.confirm(
      t('system.menu.deleteConfirm', { name: row.permissionName }),
      t('common.tip'),
      { type: 'warning' }
    )
    await deletePermission(row.permissionId)
    ElMessage.success(t('common.deleteSuccess'))
    loadData()
  } catch {
    // 用户取消
  }
}

onMounted(() => loadData())
</script>
