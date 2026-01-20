<template>
  <div class="system-role-page">
    <page-header title="角色管理">
      <el-button type="primary" v-permission="'role:create'" @click="handleCreate">
        <el-icon><Plus /></el-icon>
        新建角色
      </el-button>
    </page-header>

    <el-card>
      <el-form :model="queryForm" inline>
        <el-form-item label="角色名称">
          <el-input v-model="queryForm.roleName" placeholder="请输入角色名称" clearable />
        </el-form-item>
        <el-form-item label="角色编码">
          <el-input v-model="queryForm.roleCode" placeholder="请输入角色编码" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryForm.status" placeholder="请选择" clearable>
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadData">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="tableData" v-loading="loading" border style="margin-top: 20px">
        <el-table-column prop="roleId" label="ID" width="80" />
        <el-table-column prop="roleName" label="角色名称" width="200" />
        <el-table-column prop="roleCode" label="角色编码" width="200" />
        <el-table-column prop="description" label="描述" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" v-permission="'role:update'" @click="handleEdit(row)">
              编辑
            </el-button>
            <el-button link type="primary" v-permission="'role:permission'" @click="handleAssignPermissions(row)">
              分配权限
            </el-button>
            <el-button link type="primary" v-permission="'role:status'" @click="handleToggleStatus(row)">
              {{ row.status === 1 ? '禁用' : '启用' }}
            </el-button>
            <el-button link type="danger" v-permission="'role:delete'" @click="handleDelete(row)">
              删除
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
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="formData.roleName" placeholder="请输入角色名称" />
        </el-form-item>
        <el-form-item label="角色编码" prop="roleCode">
          <el-input
            v-model="formData.roleCode"
            placeholder="请输入角色编码"
            :disabled="isEdit"
          />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="formData.description"
            type="textarea"
            :rows="3"
            placeholder="请输入角色描述"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 分配权限对话框 -->
    <el-dialog v-model="permissionDialogVisible" title="分配权限" width="600px">
      <el-form label-width="100px">
        <el-form-item label="角色">
          <span>{{ currentRole?.roleName }}</span>
        </el-form-item>
        <el-form-item label="权限">
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
        <el-button @click="permissionDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleAssignPermissionsSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
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
    { required: true, message: '请输入角色名称', trigger: 'blur' }
  ],
  roleCode: [
    { required: true, message: '请输入角色编码', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_-]+$/, message: '角色编码只能包含字母、数字、下划线和连字符', trigger: 'blur' }
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
  dialogTitle.value = '新建角色'
  isEdit.value = false
  dialogVisible.value = true
}

// 编辑角色
const handleEdit = (row: RoleVO) => {
  dialogTitle.value = '编辑角色'
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
          ElMessage.success('更新成功')
        } else {
          await createRole(formData as CreateRoleDTO)
          ElMessage.success('创建成功')
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
    ElMessage.success('权限分配成功')
    permissionDialogVisible.value = false
    loadData()
  } catch (error) {
    console.error('权限分配失败:', error)
  }
}

// 切换角色状态
const handleToggleStatus = async (row: RoleVO) => {
  const action = row.status === 1 ? '禁用' : '启用'
  try {
    await ElMessageBox.confirm(`确定要${action}角色 ${row.roleName} 吗？`, '提示', {
      type: 'warning'
    })
    const newStatus = row.status === 1 ? 0 : 1
    await updateRoleStatus(row.roleId, newStatus)
    ElMessage.success(`${action}成功`)
    loadData()
  } catch (error) {
    // 用户取消
  }
}

// 删除角色
const handleDelete = async (row: RoleVO) => {
  try {
    await ElMessageBox.confirm(`确定要删除角色 ${row.roleName} 吗？`, '提示', {
      type: 'warning'
    })
    await deleteRole(row.roleId)
    ElMessage.success('删除成功')
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
