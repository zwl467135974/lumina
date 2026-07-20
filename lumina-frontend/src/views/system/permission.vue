<template>
  <div class="system-permission-page">
    <page-header :title="t('system.permission.title')">
      <el-button type="primary" v-permission="'permission:create'" @click="handleCreate">
        <el-icon><Plus /></el-icon>
        {{ t('common.create') }}
      </el-button>
    </page-header>

    <el-card>
      <el-form :model="queryForm" inline>
        <el-form-item :label="t('system.permission.permissionName')">
          <el-input v-model="queryForm.permissionName" :placeholder="t('system.permission.permissionNamePlaceholder')" clearable />
        </el-form-item>
        <el-form-item :label="t('system.permission.permissionCode')">
          <el-input v-model="queryForm.permissionCode" :placeholder="t('system.permission.permissionCodePlaceholder')" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadPermissionTree">{{ t('common.query') }}</el-button>
          <el-button @click="handleReset">{{ t('common.reset') }}</el-button>
          <el-button @click="expandAll">{{ t('system.permission.expandAll') }}</el-button>
          <el-button @click="collapseAll">{{ t('system.permission.collapseAll') }}</el-button>
        </el-form-item>
      </el-form>

      <el-table
        :data="permissionTree"
        v-loading="loading"
        border
        style="margin-top: 20px"
        row-key="permissionId"
        :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
        :default-expand-all="false"
        ref="tableRef"
      >
        <el-table-column prop="permissionId" label="ID" width="80" />
        <el-table-column prop="permissionName" :label="t('system.permission.permissionName')" width="200" />
        <el-table-column prop="permissionCode" :label="t('system.permission.permissionCode')" width="200" />
        <el-table-column prop="resourcePath" :label="t('system.permission.resourcePath')" show-overflow-tooltip />
        <el-table-column prop="description" :label="t('common.description')" show-overflow-tooltip />
        <el-table-column prop="createTime" :label="t('common.createTime')" width="180" />
        <el-table-column :label="t('common.actions')" width="280" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" v-permission="'permission:create'" @click="handleCreateChild(row)">
              {{ t('system.permission.addChild') }}
            </el-button>
            <el-button link type="primary" v-permission="'permission:update'" @click="handleEdit(row)">
              {{ t('common.edit') }}
            </el-button>
            <el-button link type="danger" v-permission="'permission:delete'" @click="handleDelete(row)">
              {{ t('common.delete') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 创建/编辑权限对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="600px"
      @close="handleDialogClose"
    >
      <el-form :model="formData" :rules="formRules" ref="formRef" label-width="100px">
        <el-form-item :label="t('system.permission.permissionName')" prop="permissionName">
          <el-input v-model="formData.permissionName" :placeholder="t('system.permission.permissionNamePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('system.permission.permissionCode')" prop="permissionCode">
          <el-input
            v-model="formData.permissionCode"
            :placeholder="t('system.permission.permissionCodeEditPlaceholder')"
            :disabled="isEdit"
          />
        </el-form-item>
        <el-form-item :label="t('system.permission.resourcePath')" prop="resourcePath">
          <el-input v-model="formData.resourcePath" :placeholder="t('system.permission.resourcePathPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('system.permission.parent')" prop="parentId">
          <el-tree-select
            v-model="formData.parentId"
            :data="parentPermissionTree"
            :props="{ label: 'permissionName', value: 'permissionId' }"
            :placeholder="t('system.permission.parentPlaceholder')"
            clearable
            check-strictly
            :render-after-expand="false"
          />
        </el-form-item>
        <el-form-item :label="t('common.description')" prop="description">
          <el-input
            v-model="formData.description"
            type="textarea"
            :rows="3"
            :placeholder="t('system.permission.descriptionPlaceholder')"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">{{ t('common.ok') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import {
  getPermissionTree,
  createPermission,
  updatePermission,
  deletePermission
} from '@/api/modules/system-permission'
import type { PermissionVO, CreatePermissionDTO, UpdatePermissionDTO, QueryPermissionDTO } from '@/types/api'
import PageHeader from '@/components/common/PageHeader.vue'

const { t } = useI18n()

const queryForm = reactive<QueryPermissionDTO>({
  permissionName: '',
  permissionCode: '',
  resourcePath: ''
})

const loading = ref(false)
const submitting = ref(false)
const permissionTree = ref<PermissionVO[]>([])
const tableRef = ref()

// 对话框相关
const dialogVisible = ref(false)
const dialogTitle = ref('')
const isEdit = ref(false)
const editingPermissionId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const formData = reactive<CreatePermissionDTO & { description?: string }>({
  permissionName: '',
  permissionCode: '',
  resourcePath: '',
  parentId: undefined,
  description: ''
})

const formRules: FormRules = {
  permissionName: [
    { required: true, message: t('system.permission.permissionNameRequired'), trigger: 'blur' }
  ],
  permissionCode: [
    { required: true, message: t('system.permission.permissionCodeRequired'), trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9:_-]+$/, message: t('system.permission.permissionCodeInvalid'), trigger: 'blur' }
  ],
  resourcePath: [
    { required: true, message: t('system.permission.resourcePathRequired'), trigger: 'blur' }
  ]
}

// 父级权限树（用于选择）
const parentPermissionTree = computed(() => {
  // 如果是编辑模式，需要过滤掉当前节点及其子节点
  if (isEdit.value && editingPermissionId.value) {
    const filterTree = (tree: PermissionVO[]): PermissionVO[] => {
      return tree.filter(node => {
        if (node.permissionId === editingPermissionId.value) {
          return false
        }
        if (node.children) {
          node.children = filterTree(node.children)
        }
        return true
      })
    }
    return filterTree(JSON.parse(JSON.stringify(permissionTree.value)))
  }
  return permissionTree.value
})

// 加载权限树
const loadPermissionTree = async () => {
  loading.value = true
  try {
    const res = await getPermissionTree(queryForm)
    permissionTree.value = res.data
  } catch (error) {
    console.error('加载权限树失败:', error)
    ElMessage.error(t('system.permission.loadTreeFailed'))
    permissionTree.value = []
  } finally {
    loading.value = false
  }
}

// 重置查询表单
const handleReset = () => {
  queryForm.permissionName = ''
  queryForm.permissionCode = ''
  queryForm.resourcePath = ''
  loadPermissionTree()
}

// 展开全部
const expandAll = () => {
  const expandRows = (rows: PermissionVO[]) => {
    rows.forEach(row => {
      tableRef.value.toggleRowExpansion(row, true)
      if (row.children) {
        expandRows(row.children)
      }
    })
  }
  expandRows(permissionTree.value)
}

// 折叠全部
const collapseAll = () => {
  const collapseRows = (rows: PermissionVO[]) => {
    rows.forEach(row => {
      tableRef.value.toggleRowExpansion(row, false)
      if (row.children) {
        collapseRows(row.children)
      }
    })
  }
  collapseRows(permissionTree.value)
}

// 创建权限
const handleCreate = () => {
  dialogTitle.value = t('system.permission.create')
  isEdit.value = false
  formData.parentId = undefined
  dialogVisible.value = true
}

// 创建子权限
const handleCreateChild = (row: PermissionVO) => {
  dialogTitle.value = t('system.permission.createChild')
  isEdit.value = false
  formData.parentId = row.permissionId
  dialogVisible.value = true
}

// 编辑权限
const handleEdit = (row: PermissionVO) => {
  dialogTitle.value = t('system.permission.edit')
  isEdit.value = true
  editingPermissionId.value = row.permissionId
  formData.permissionName = row.permissionName
  formData.permissionCode = row.permissionCode
  formData.resourcePath = row.resourcePath
  formData.parentId = row.parentId
  formData.description = row.description || ''
  dialogVisible.value = true
}

// 提交表单
const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (valid) {
      submitting.value = true
      try {
        if (isEdit.value && editingPermissionId.value) {
          const updateData: UpdatePermissionDTO = {
            permissionName: formData.permissionName,
            resourcePath: formData.resourcePath,
            description: formData.description,
            parentId: formData.parentId
          }
          await updatePermission(editingPermissionId.value, updateData)
          ElMessage.success(t('common.updateSuccess'))
        } else {
          await createPermission(formData as CreatePermissionDTO)
          ElMessage.success(t('common.createSuccess'))
        }
        dialogVisible.value = false
        loadPermissionTree()
      } catch (error) {
        console.error('操作失败:', error)
      } finally {
        submitting.value = false
      }
    }
  })
}

// 关闭对话框
const handleDialogClose = () => {
  formRef.value?.resetFields()
  editingPermissionId.value = null
  Object.assign(formData, {
    permissionName: '',
    permissionCode: '',
    resourcePath: '',
    parentId: undefined,
    description: ''
  })
}

// 删除权限
const handleDelete = async (row: PermissionVO) => {
  // 检查是否有子权限
  if (row.children && row.children.length > 0) {
    ElMessage.warning(t('system.permission.hasChildren'))
    return
  }

  try {
    await ElMessageBox.confirm(
      t('system.permission.deleteConfirm', { name: row.permissionName }),
      t('common.tip'),
      {
        type: 'warning'
      }
    )
    await deletePermission(row.permissionId)
    ElMessage.success(t('common.deleteSuccess'))
    loadPermissionTree()
  } catch (error) {
    // 用户取消
  }
}

onMounted(() => {
  loadPermissionTree()
})
</script>

<style scoped lang="scss">
.system-permission-page {
  :deep(.el-table) {
    .el-table__row {
      .el-table__expand-icon {
        margin-right: 8px;
      }
    }
  }
}

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
