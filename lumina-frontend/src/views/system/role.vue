<template>
  <div class="system-role-page">
    <page-header :title="t('system.role.title')">
      <el-button type="primary" v-permission="'role:create'" @click="handleCreate">
        <el-icon><Plus /></el-icon>
        {{ t('common.create') }}
      </el-button>
    </page-header>

    <el-card>
      <el-form :model="queryForm" inline>
        <el-form-item :label="t('system.role.roleName')">
          <el-input v-model="queryForm.roleName" :placeholder="t('system.role.roleNamePlaceholder')" clearable />
        </el-form-item>
        <el-form-item :label="t('system.role.roleCode')">
          <el-input v-model="queryForm.roleCode" :placeholder="t('system.role.roleCodePlaceholder')" clearable />
        </el-form-item>
        <el-form-item :label="t('common.status')">
          <el-select v-model="queryForm.status" :placeholder="t('common.pleaseSelect')" clearable>
            <el-option :label="t('common.enable')" :value="1" />
            <el-option :label="t('common.disable')" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadData">{{ t('common.query') }}</el-button>
          <el-button @click="handleReset">{{ t('common.reset') }}</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="tableData" v-loading="loading" border style="margin-top: 20px">
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
        <el-table-column :label="t('common.actions')" width="280" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" v-permission="'role:update'" @click="handleEdit(row)">
              {{ t('common.edit') }}
            </el-button>
            <el-button link type="primary" v-permission="'role:permission'" @click="handleAssignPermissions(row)">
              {{ t('system.role.assignPermissions') }}
            </el-button>
            <el-button link type="primary" v-permission="'role:status'" @click="handleToggleStatus(row)">
              {{ row.status === 1 ? t('common.disable') : t('common.enable') }}
            </el-button>
            <el-button link type="danger" v-permission="'role:delete'" @click="handleDelete(row)">
              {{ t('common.delete') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pagination.pageNum"
        v-model:page-size="pagination.pageSize"
        :total="pagination.total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        style="margin-top: 20px; justify-content: flex-end"
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
      />
    </el-card>

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
import { reactive, ref, onMounted } from 'vue'
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
import PageHeader from '@/components/common/PageHeader.vue'

const { t } = useI18n()

const queryForm = reactive<QueryRoleDTO>({
  roleName: '',
  roleCode: '',
  status: undefined
})

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
</style>
