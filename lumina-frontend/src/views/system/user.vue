<template>
  <div class="system-user-page">
    <page-header :title="t('system.user.title')">
      <el-button type="primary" v-permission="'user:create'" @click="handleCreate">
        <el-icon><Plus /></el-icon>
        {{ t('common.create') }}
      </el-button>
    </page-header>

    <el-card>
      <el-form :model="queryForm" inline>
        <el-form-item :label="t('system.user.username')">
          <el-input v-model="queryForm.username" :placeholder="t('system.user.usernamePlaceholder')" clearable />
        </el-form-item>
        <el-form-item :label="t('system.user.nickname')">
          <el-input v-model="queryForm.nickname" :placeholder="t('system.user.nicknamePlaceholder')" clearable />
        </el-form-item>
        <el-form-item :label="t('system.user.email')">
          <el-input v-model="queryForm.email" :placeholder="t('system.user.emailPlaceholder')" clearable />
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
        <el-table-column prop="userId" label="ID" width="80" />
        <el-table-column prop="username" :label="t('system.user.username')" width="150" />
        <el-table-column prop="nickname" :label="t('system.user.nickname')" width="150" />
        <el-table-column prop="email" :label="t('system.user.email')" width="200" />
        <el-table-column prop="phone" :label="t('system.user.phone')" width="150" />
        <el-table-column prop="status" :label="t('common.status')" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? t('common.enable') : t('common.disable') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="tenantName" :label="t('system.user.tenant')" width="150" />
        <el-table-column prop="roles" :label="t('system.user.roles')" width="200">
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
        <el-table-column prop="createTime" :label="t('common.createTime')" width="180" />
        <el-table-column :label="t('common.actions')" width="320" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" v-permission="'user:update'" @click="handleEdit(row)">
              {{ t('common.edit') }}
            </el-button>
            <el-button link type="primary" v-permission="'user:role'" @click="handleAssignRoles(row)">
              {{ t('system.user.assignRoles') }}
            </el-button>
            <el-button link type="warning" v-permission="'user:reset'" @click="handleResetPassword(row)">
              {{ t('system.user.resetPassword') }}
            </el-button>
            <el-button link type="primary" v-permission="'user:status'" @click="handleToggleStatus(row)">
              {{ row.status === 1 ? t('common.disable') : t('common.enable') }}
            </el-button>
            <el-button link type="danger" v-permission="'user:delete'" @click="handleDelete(row)">
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

    <!-- 创建/编辑用户对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="600px"
      @close="handleDialogClose"
    >
      <el-form :model="formData" :rules="formRules" ref="formRef" label-width="100px">
        <el-form-item :label="t('system.user.username')" prop="username">
          <el-input
            v-model="formData.username"
            :placeholder="t('system.user.usernamePlaceholder')"
            :disabled="isEdit"
          />
        </el-form-item>
        <el-form-item :label="t('system.user.password')" prop="password" v-if="!isEdit">
          <el-input v-model="formData.password" type="password" :placeholder="t('system.user.passwordPlaceholder')" show-password />
        </el-form-item>
        <el-form-item :label="t('system.user.nickname')" prop="nickname">
          <el-input v-model="formData.nickname" :placeholder="t('system.user.nicknamePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('system.user.email')" prop="email">
          <el-input v-model="formData.email" :placeholder="t('system.user.emailPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('system.user.phone')" prop="phone">
          <el-input v-model="formData.phone" :placeholder="t('system.user.phonePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('system.user.roles')" prop="roleIds" v-if="!isEdit">
          <el-select v-model="formData.roleIds" multiple :placeholder="t('system.user.rolesPlaceholder')" style="width: 100%">
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
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleSubmit">{{ t('common.ok') }}</el-button>
      </template>
    </el-dialog>

    <!-- 分配角色对话框 -->
    <el-dialog v-model="roleDialogVisible" :title="t('system.user.assignRoles')" width="500px">
      <el-form :model="roleForm" label-width="100px">
        <el-form-item :label="t('system.user.user')">
          <span>{{ currentUser?.username }}</span>
        </el-form-item>
        <el-form-item :label="t('system.user.roles')">
          <el-select v-model="roleForm.roleIds" multiple :placeholder="t('system.user.rolesPlaceholder')" style="width: 100%">
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
        <el-button @click="roleDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleAssignRolesSubmit">{{ t('common.ok') }}</el-button>
      </template>
    </el-dialog>

    <!-- 重置密码对话框 -->
    <el-dialog v-model="passwordDialogVisible" :title="t('system.user.resetPassword')" width="500px">
      <el-form :model="passwordForm" :rules="passwordRules" ref="passwordFormRef" label-width="100px">
        <el-form-item :label="t('system.user.user')">
          <span>{{ currentUser?.username }}</span>
        </el-form-item>
        <el-form-item :label="t('system.user.newPassword')" prop="newPassword">
          <el-input v-model="passwordForm.newPassword" type="password" :placeholder="t('system.user.newPasswordPlaceholder')" show-password />
        </el-form-item>
        <el-form-item :label="t('system.user.confirmPassword')" prop="confirmPassword">
          <el-input v-model="passwordForm.confirmPassword" type="password" :placeholder="t('system.user.confirmPasswordPlaceholder')" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleResetPasswordSubmit">{{ t('common.ok') }}</el-button>
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

const { t } = useI18n()

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
    { required: true, message: t('system.user.usernameRequired'), trigger: 'blur' },
    { min: 3, max: 20, message: t('system.user.usernameLength'), trigger: 'blur' }
  ],
  password: [
    { required: true, message: t('system.user.passwordRequired'), trigger: 'blur' },
    { min: 6, max: 20, message: t('system.user.passwordLength'), trigger: 'blur' }
  ],
  email: [
    { type: 'email', message: t('common.emailInvalid'), trigger: 'blur' }
  ],
  phone: [
    { pattern: /^1[3-9]\d{9}$/, message: t('common.phoneInvalid'), trigger: 'blur' }
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

const validateConfirmPassword = (_rule: any, value: any, callback: any) => {
  if (value === '') {
    callback(new Error(t('system.user.confirmPasswordRequired')))
  } else if (value !== passwordForm.newPassword) {
    callback(new Error(t('system.user.passwordMismatch')))
  } else {
    callback()
  }
}

const passwordRules: FormRules = {
  newPassword: [
    { required: true, message: t('system.user.newPasswordPlaceholder'), trigger: 'blur' },
    { min: 6, max: 20, message: t('system.user.passwordLength'), trigger: 'blur' }
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
  dialogTitle.value = t('system.user.create')
  isEdit.value = false
  dialogVisible.value = true
}

// 编辑用户
const handleEdit = (row: UserVO) => {
  dialogTitle.value = t('system.user.edit')
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
          ElMessage.success(t('common.updateSuccess'))
        } else {
          await createUser(formData as CreateUserDTO)
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
    ElMessage.success(t('system.user.assignRolesSuccess'))
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
        ElMessage.success(t('system.user.resetPasswordSuccess'))
        passwordDialogVisible.value = false
      } catch (error) {
        console.error('密码重置失败:', error)
      }
    }
  })
}

// 切换用户状态
const handleToggleStatus = async (row: UserVO) => {
  const action = row.status === 1 ? t('common.disable') : t('common.enable')
  try {
    await ElMessageBox.confirm(
      t('system.user.toggleStatusConfirm', { action, name: row.username }),
      t('common.tip'),
      { type: 'warning' }
    )
    const newStatus = row.status === 1 ? 0 : 1
    await updateUserStatus(row.userId, newStatus)
    ElMessage.success(row.status === 1 ? t('common.disableSuccess') : t('common.enableSuccess'))
    loadData()
  } catch (error) {
    // 用户取消
  }
}

// 删除用户
const handleDelete = async (row: UserVO) => {
  try {
    await ElMessageBox.confirm(
      t('system.user.deleteConfirm', { name: row.username }),
      t('common.tip'),
      { type: 'warning' }
    )
    await deleteUser(row.userId)
    ElMessage.success(t('common.deleteSuccess'))
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
