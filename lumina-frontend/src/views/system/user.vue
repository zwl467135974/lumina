<template>
  <div class="system-user-page">
    <page-header title="用户管理">
      <el-button type="primary" v-permission="'user:create'" @click="handleCreate">
        <el-icon><Plus /></el-icon>
        新建用户
      </el-button>
    </page-header>

    <el-card>
      <el-form :model="queryForm" inline>
        <el-form-item label="用户名">
          <el-input v-model="queryForm.username" placeholder="请输入用户名" clearable />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="queryForm.nickname" placeholder="请输入昵称" clearable />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="queryForm.email" placeholder="请输入邮箱" clearable />
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
        <el-table-column prop="userId" label="ID" width="80" />
        <el-table-column prop="username" label="用户名" width="150" />
        <el-table-column prop="nickname" label="昵称" width="150" />
        <el-table-column prop="email" label="邮箱" width="200" />
        <el-table-column prop="phone" label="手机号" width="150" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="tenantName" label="租户" width="150" />
        <el-table-column prop="roles" label="角色" width="200">
          <template #default="{ row }">
            <el-tag
              v-for="role in row.roles"
              :key="role.roleId"
              type="info"
              size="small"
              style="margin-right: 5px"
            >
              {{ role.roleName }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="320" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" v-permission="'user:update'" @click="handleEdit(row)">
              编辑
            </el-button>
            <el-button link type="primary" v-permission="'user:role'" @click="handleAssignRoles(row)">
              分配角色
            </el-button>
            <el-button link type="warning" v-permission="'user:reset'" @click="handleResetPassword(row)">
              重置密码
            </el-button>
            <el-button link type="primary" v-permission="'user:status'" @click="handleToggleStatus(row)">
              {{ row.status === 1 ? '禁用' : '启用' }}
            </el-button>
            <el-button link type="danger" v-permission="'user:delete'" @click="handleDelete(row)">
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

    <!-- 创建/编辑用户对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="600px"
      @close="handleDialogClose"
    >
      <el-form :model="formData" :rules="formRules" ref="formRef" label-width="100px">
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="formData.username"
            placeholder="请输入用户名"
            :disabled="isEdit"
          />
        </el-form-item>
        <el-form-item label="密码" prop="password" v-if="!isEdit">
          <el-input v-model="formData.password" type="password" placeholder="请输入密码" show-password />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="formData.nickname" placeholder="请输入昵称" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="formData.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="formData.phone" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="角色" prop="roleIds" v-if="!isEdit">
          <el-select v-model="formData.roleIds" multiple placeholder="请选择角色" style="width: 100%">
            <el-option
              v-for="role in allRoles"
              :key="role.roleId"
              :label="role.roleName"
              :value="role.roleId"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 分配角色对话框 -->
    <el-dialog v-model="roleDialogVisible" title="分配角色" width="500px">
      <el-form :model="roleForm" label-width="100px">
        <el-form-item label="用户">
          <span>{{ currentUser?.username }}</span>
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="roleForm.roleIds" multiple placeholder="请选择角色" style="width: 100%">
            <el-option
              v-for="role in allRoles"
              :key="role.roleId"
              :label="role.roleName"
              :value="role.roleId"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleAssignRolesSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 重置密码对话框 -->
    <el-dialog v-model="passwordDialogVisible" title="重置密码" width="500px">
      <el-form :model="passwordForm" :rules="passwordRules" ref="passwordFormRef" label-width="100px">
        <el-form-item label="用户">
          <span>{{ currentUser?.username }}</span>
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="passwordForm.newPassword" type="password" placeholder="请输入新密码" show-password />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="passwordForm.confirmPassword" type="password" placeholder="请再次输入新密码" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleResetPasswordSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import {
  getUserList,
  createUser,
  updateUser,
  deleteUser,
  resetPassword,
  assignRoles,
  updateUserStatus
} from '@/api/modules/system-user'
import { getAllRoles } from '@/api/modules/system-role'
import { useTable } from '@/composables/useTable'
import type { UserVO, CreateUserDTO, UpdateUserDTO, QueryUserDTO, RoleVO } from '@/types/api'
import PageHeader from '@/components/common/PageHeader.vue'

const queryForm = reactive<QueryUserDTO>({
  username: '',
  nickname: '',
  email: '',
  status: undefined
})

const { loading, tableData, pagination, loadData, handlePageChange, handleSizeChange } = useTable<UserVO>(
  (params) => getUserList({ ...queryForm, ...params })
)

// 对话框相关
const dialogVisible = ref(false)
const dialogTitle = ref('')
const isEdit = ref(false)
const editingUserId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const formData = reactive<CreateUserDTO & { confirmPassword?: string }>({
  username: '',
  password: '',
  nickname: '',
  email: '',
  phone: '',
  roleIds: []
})

const formRules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度在 3 到 20 个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度在 6 到 20 个字符', trigger: 'blur' }
  ],
  email: [
    { type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' }
  ],
  phone: [
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' }
  ]
}

// 角色相关
const roleDialogVisible = ref(false)
const currentUser = ref<UserVO | null>(null)
const allRoles = ref<RoleVO[]>([])
const roleForm = reactive<{ roleIds: number[] }>({
  roleIds: []
})

// 密码重置相关
const passwordDialogVisible = ref(false)
const passwordFormRef = ref<FormInstance>()
const passwordForm = reactive({
  userId: 0,
  newPassword: '',
  confirmPassword: ''
})

const validateConfirmPassword = (rule: any, value: any, callback: any) => {
  if (value === '') {
    callback(new Error('请再次输入密码'))
  } else if (value !== passwordForm.newPassword) {
    callback(new Error('两次输入密码不一致'))
  } else {
    callback()
  }
}

const passwordRules: FormRules = {
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度在 6 到 20 个字符', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, validator: validateConfirmPassword, trigger: 'blur' }
  ]
}

// 加载所有角色
const loadAllRoles = async () => {
  try {
    const res = await getAllRoles()
    allRoles.value = res.data
  } catch (error) {
    console.error('加载角色列表失败:', error)
  }
}

// 重置查询表单
const handleReset = () => {
  queryForm.username = ''
  queryForm.nickname = ''
  queryForm.email = ''
  queryForm.status = undefined
  loadData()
}

// 创建用户
const handleCreate = () => {
  dialogTitle.value = '新建用户'
  isEdit.value = false
  dialogVisible.value = true
}

// 编辑用户
const handleEdit = (row: UserVO) => {
  dialogTitle.value = '编辑用户'
  isEdit.value = true
  editingUserId.value = row.userId
  formData.username = row.username
  formData.nickname = row.nickname || ''
  formData.email = row.email || ''
  formData.phone = row.phone || ''
  dialogVisible.value = true
}

// 提交表单
const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (valid) {
      try {
        if (isEdit.value && editingUserId.value) {
          const updateData: UpdateUserDTO = {
            nickname: formData.nickname,
            email: formData.email,
            phone: formData.phone
          }
          await updateUser(editingUserId.value, updateData)
          ElMessage.success('更新成功')
        } else {
          await createUser(formData as CreateUserDTO)
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
  editingUserId.value = null
  Object.assign(formData, {
    username: '',
    password: '',
    nickname: '',
    email: '',
    phone: '',
    roleIds: []
  })
}

// 分配角色
const handleAssignRoles = (row: UserVO) => {
  currentUser.value = row
  roleForm.roleIds = row.roles?.map(r => r.roleId) || []
  roleDialogVisible.value = true
}

// 提交角色分配
const handleAssignRolesSubmit = async () => {
  if (!currentUser.value) return
  try {
    await assignRoles(currentUser.value.userId, roleForm.roleIds)
    ElMessage.success('角色分配成功')
    roleDialogVisible.value = false
    loadData()
  } catch (error) {
    console.error('角色分配失败:', error)
  }
}

// 重置密码
const handleResetPassword = (row: UserVO) => {
  currentUser.value = row
  passwordForm.userId = row.userId
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
  passwordDialogVisible.value = true
}

// 提交密码重置
const handleResetPasswordSubmit = async () => {
  if (!passwordFormRef.value) return
  await passwordFormRef.value.validate(async (valid) => {
    if (valid) {
      try {
        await resetPassword({
          userId: passwordForm.userId,
          newPassword: passwordForm.newPassword
        })
        ElMessage.success('密码重置成功')
        passwordDialogVisible.value = false
      } catch (error) {
        console.error('密码重置失败:', error)
      }
    }
  })
}

// 切换用户状态
const handleToggleStatus = async (row: UserVO) => {
  const action = row.status === 1 ? '禁用' : '启用'
  try {
    await ElMessageBox.confirm(`确定要${action}用户 ${row.username} 吗？`, '提示', {
      type: 'warning'
    })
    const newStatus = row.status === 1 ? 0 : 1
    await updateUserStatus(row.userId, newStatus)
    ElMessage.success(`${action}成功`)
    loadData()
  } catch (error) {
    // 用户取消
  }
}

// 删除用户
const handleDelete = async (row: UserVO) => {
  try {
    await ElMessageBox.confirm(`确定要删除用户 ${row.username} 吗？`, '提示', {
      type: 'warning'
    })
    await deleteUser(row.userId)
    ElMessage.success('删除成功')
    loadData()
  } catch (error) {
    // 用户取消
  }
}

onMounted(() => {
  loadData()
  loadAllRoles()
})
</script>

<style scoped lang="scss">
.system-user-page {
  :deep(.el-pagination) {
    display: flex;
  }
}
</style>
