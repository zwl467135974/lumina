<template>
  <div class="system-role-page">
    <PageHeader :title="t('system.role.title')" />

    <LumTablePanel
      :search-model="queryForm"
      :data="tableData"
      :loading="loading"
      :pagination="pagination"
      :search-fields="searchFields"
      @search="loadData"
      @reset="handleReset"
      @page-change="handlePageChange"
      @size-change="handleSizeChange"
    >
      <template #toolbar-left>
        <el-button type="primary" v-permission="'role:create'" @click="handleCreate">
          <el-icon><Plus /></el-icon>
          {{ t('common.create') }}
        </el-button>
      </template>

      <el-table-column prop="roleId" label="ID" width="80" />
        <el-table-column prop="roleName" :label="t('system.role.roleName')" width="200" />
        <el-table-column prop="roleCode" :label="t('system.role.roleCode')" width="200" />
        <el-table-column prop="description" :label="t('common.description')" show-overflow-tooltip />
        <el-table-column prop="status" :label="t('common.status')" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? t('common.enable') : t('common.disable') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" :label="t('common.createTime')" width="180" />
        <el-table-column :label="t('common.actions')" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" v-permission="'role:update'" @click="handleEdit(row)">
              {{ t('common.edit') }}
            </el-button>
            <el-button link type="primary" v-permission="'role:permission'" @click="handleAssignPermissions(row)">
              {{ t('system.role.assignPermissions') }}
            </el-button>
            <el-dropdown trigger="click" @command="(cmd: string) => handleRowCommand(cmd, row)">
              <el-button link>{{ t('common.more') }}</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-permission="'role:status'" command="status">
                    {{ row.status === 1 ? t('common.disable') : t('common.enable') }}
                  </el-dropdown-item>
                  <el-dropdown-item v-permission="'role:delete'" command="delete" divided>{{ t('common.delete') }}</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
    </LumTablePanel>

    <!-- 创建/编辑角色对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="600px"
      @close="handleDialogClose"
    >
      <el-form :model="formData" :rules="formRules" ref="formRef" label-width="100px">
        <el-form-item :label="t('system.role.roleName')" prop="roleName">
          <el-input v-model="formData.roleName" :placeholder="t('system.role.roleNamePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('system.role.roleCode')" prop="roleCode">
          <el-input
            v-model="formData.roleCode"
            :placeholder="t('system.role.roleCodePlaceholder')"
            :disabled="isEdit"
          />
        </el-form-item>
        <el-form-item :label="t('common.description')" prop="description">
          <el-input
            v-model="formData.description"
            type="textarea"
            :rows="3"
            :placeholder="t('system.role.descriptionPlaceholder')"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleSubmit">{{ t('common.ok') }}</el-button>
      </template>
    </el-dialog>

    <!-- 分配权限对话框 -->
    <el-dialog v-model="permissionDialogVisible" :title="t('system.role.assignPermissions')" width="600px">
      <el-form label-width="100px">
        <el-form-item :label="t('system.role.role')">
          <span>{{ currentRole?.roleName }}</span>
        </el-form-item>
        <el-form-item :label="t('system.role.permissions')">
          <el-tree
            ref="permissionTreeRef"
            :data="permissionTree"
            :props="treeProps"
            show-checkbox
            node-key="permissionId"
            :default-checked-keys="checkedPermissions"
            style="border: 1px solid #dcdfe6; border-radius: 4px; padding: 10px"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="permissionDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleAssignPermissionsSubmit">{{ t('common.ok') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import {
  getRoleList,
  createRole,
  updateRole,
  deleteRole,
  assignPermissions,
  updateRoleStatus
} from '@/api/modules/system-role'
import { getPermissionTree } from '@/api/modules/system-permission'
import { useTable } from '@/composables/useTable'
import type { RoleVO, CreateRoleDTO, UpdateRoleDTO, QueryRoleDTO, PermissionVO } from '@/types/api'
import { PageHeader, LumTablePanel, type SearchField } from '@/components/common'

const { t } = useI18n()

const queryForm = reactive<QueryRoleDTO>({
  roleName: '',
  roleCode: '',
  status: undefined
})

const searchFields = computed<SearchField[]>(() => [
  { prop: 'roleName', label: t('system.role.roleName'), type: 'input', placeholder: t('system.role.roleNamePlaceholder') },
  { prop: 'roleCode', label: t('system.role.roleCode'), type: 'input', placeholder: t('system.role.roleCodePlaceholder') },
  {
    prop: 'status',
    label: t('common.status'),
    type: 'select',
    placeholder: t('common.pleaseSelect'),
    options: [
      { label: t('common.enable'), value: 1 },
      { label: t('common.disable'), value: 0 }
    ]
  }
])

const { loading, tableData, pagination, loadData, handlePageChange, handleSizeChange } = useTable<RoleVO>(
  (params) => getRoleList({ ...queryForm, ...params })
)

// 对话框相关
const dialogVisible = ref(false)
const dialogTitle = ref('')
const isEdit = ref(false)
const editingRoleId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const formData = reactive<CreateRoleDTO & { description?: string }>({
  roleName: '',
  roleCode: '',
  description: ''
})

const formRules: FormRules = {
  roleName: [
    { required: true, message: t('system.role.roleNameRequired'), trigger: 'blur' }
  ],
  roleCode: [
    { required: true, message: t('system.role.roleCodeRequired'), trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_-]+$/, message: t('system.role.roleCodePattern'), trigger: 'blur' }
  ]
}

// 权限相关
const permissionDialogVisible = ref(false)
const currentRole = ref<RoleVO | null>(null)
const permissionTree = ref<PermissionVO[]>([])
const permissionTreeRef = ref()
const checkedPermissions = ref<number[]>([])
const treeProps = {
  children: 'children',
  label: 'permissionName'
}

// 加载权限树
const loadPermissionTree = async () => {
  try {
    const res = await getPermissionTree()
    permissionTree.value = res.data
  } catch (error) {
    console.error('加载权限树失败:', error)
  }
}

// 重置查询表单
const handleReset = () => {
  queryForm.roleName = ''
  queryForm.roleCode = ''
  queryForm.status = undefined
  loadData()
}

// 创建角色
const handleCreate = () => {
  dialogTitle.value = t('system.role.create')
  isEdit.value = false
  dialogVisible.value = true
}

// 编辑角色
const handleEdit = (row: RoleVO) => {
  dialogTitle.value = t('system.role.edit')
  isEdit.value = true
  editingRoleId.value = row.roleId
  formData.roleName = row.roleName
  formData.roleCode = row.roleCode
  formData.description = row.description || ''
  dialogVisible.value = true
}

// 提交表单
const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (valid) {
      try {
        if (isEdit.value && editingRoleId.value) {
          const updateData: UpdateRoleDTO = {
            roleName: formData.roleName,
            description: formData.description
          }
          await updateRole(editingRoleId.value, updateData)
          ElMessage.success(t('common.updateSuccess'))
        } else {
          await createRole(formData as CreateRoleDTO)
          ElMessage.success(t('common.createSuccess'))
        }
        dialogVisible.value = false
        loadData()
      } catch (error) {
        console.error('操作失败:', error)
      }
    }
  })
}

// 关闭对话框
const handleDialogClose = () => {
  formRef.value?.resetFields()
  editingRoleId.value = null
  Object.assign(formData, {
    roleName: '',
    roleCode: '',
    description: ''
  })
}

// 分配权限
const handleAssignPermissions = (row: RoleVO) => {
  currentRole.value = row
  // 获取当前角色的权限ID列表
  const extractPermissionIds = (permissions: PermissionVO[] | undefined): number[] => {
    if (!permissions) return []
    const ids: number[] = []
    permissions.forEach(p => {
      ids.push(p.permissionId)
      if (p.children) {
        ids.push(...extractPermissionIds(p.children))
      }
    })
    return ids
  }
  checkedPermissions.value = extractPermissionIds(row.permissions)
  permissionDialogVisible.value = true
}

// 提交权限分配
const handleAssignPermissionsSubmit = async () => {
  if (!currentRole.value) return
  try {
    // 获取选中的权限ID（包括半选中的父节点）
    const checkedKeys = permissionTreeRef.value.getCheckedKeys()
    const halfCheckedKeys = permissionTreeRef.value.getHalfCheckedKeys()
    const allPermissionIds = [...checkedKeys, ...halfCheckedKeys] as number[]

    await assignPermissions(currentRole.value.roleId, allPermissionIds)
    ElMessage.success(t('system.role.assignPermissionsSuccess'))
    permissionDialogVisible.value = false
    loadData()
  } catch (error) {
    console.error('权限分配失败:', error)
  }
}

// 切换角色状态
const handleToggleStatus = async (row: RoleVO) => {
  const action = row.status === 1 ? t('common.disable') : t('common.enable')
  try {
    await ElMessageBox.confirm(
      t('system.role.toggleStatusConfirm', { action, name: row.roleName }),
      t('common.tip'),
      { type: 'warning' }
    )
    const newStatus = row.status === 1 ? 0 : 1
    await updateRoleStatus(row.roleId, newStatus)
    ElMessage.success(row.status === 1 ? t('common.disableSuccess') : t('common.enableSuccess'))
    loadData()
  } catch (error) {
    // 用户取消
  }
}

// 删除角色
const handleDelete = async (row: RoleVO) => {
  try {
    await ElMessageBox.confirm(
      t('system.role.deleteConfirm', { name: row.roleName }),
      t('common.tip'),
      { type: 'warning' }
    )
    await deleteRole(row.roleId)
    ElMessage.success(t('common.deleteSuccess'))
    loadData()
  } catch (error) {
    // 用户取消
  }
}

function handleRowCommand(cmd: string, row: RoleVO) {
  if (cmd === 'status') handleToggleStatus(row)
  else if (cmd === 'delete') handleDelete(row)
}

onMounted(() => {
  loadData()
  loadPermissionTree()
})
</script>

<style scoped lang="scss">
.system-role-page {
  :deep(.el-pagination) {
    display: flex;
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
