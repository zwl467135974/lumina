<template>
  <div class="system-tenant-page">
    <page-header title="租户管理">
      <el-button type="primary" v-permission="'tenant:create'" @click="handleCreate">
        <el-icon><Plus /></el-icon>
        新建租户
      </el-button>
    </page-header>

    <el-card>
      <el-form :model="queryForm" inline>
        <el-form-item label="租户名称">
          <el-input v-model="queryForm.tenantName" placeholder="请输入租户名称" clearable />
        </el-form-item>
        <el-form-item label="租户编码">
          <el-input v-model="queryForm.tenantCode" placeholder="请输入租户编码" clearable />
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
        <el-table-column prop="tenantId" label="ID" width="80" />
        <el-table-column prop="tenantName" label="租户名称" width="200" />
        <el-table-column prop="tenantCode" label="租户编码" width="200" />
        <el-table-column prop="contact" label="联系人" width="150" />
        <el-table-column prop="phone" label="联系电话" width="150" />
        <el-table-column prop="email" label="邮箱" width="200" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" v-permission="'tenant:update'" @click="handleEdit(row)">
              编辑
            </el-button>
            <el-button link type="primary" v-permission="'tenant:status'" @click="handleToggleStatus(row)">
              {{ row.status === 1 ? '禁用' : '启用' }}
            </el-button>
            <el-button link type="danger" v-permission="'tenant:delete'" @click="handleDelete(row)">
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

    <!-- 创建/编辑租户对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="600px"
      @close="handleDialogClose"
    >
      <el-form :model="formData" :rules="formRules" ref="formRef" label-width="100px">
        <el-form-item label="租户名称" prop="tenantName">
          <el-input v-model="formData.tenantName" placeholder="请输入租户名称" />
        </el-form-item>
        <el-form-item label="租户编码" prop="tenantCode">
          <el-input
            v-model="formData.tenantCode"
            placeholder="请输入租户编码"
            :disabled="isEdit"
          />
        </el-form-item>
        <el-form-item label="联系人" prop="contact">
          <el-input v-model="formData.contact" placeholder="请输入联系人姓名" />
        </el-form-item>
        <el-form-item label="联系电话" prop="phone">
          <el-input v-model="formData.phone" placeholder="请输入联系电话" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="formData.email" placeholder="请输入邮箱" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import {
  getTenantList,
  createTenant,
  updateTenant,
  deleteTenant,
  updateTenantStatus
} from '@/api/modules/system-tenant'
import { useTable } from '@/composables/useTable'
import type { TenantVO, CreateTenantDTO, UpdateTenantDTO, QueryTenantDTO } from '@/types/api'
import PageHeader from '@/components/common/PageHeader.vue'

const queryForm = reactive<QueryTenantDTO>({
  tenantName: '',
  tenantCode: '',
  status: undefined
})

const { loading, tableData, pagination, loadData, handlePageChange, handleSizeChange } = useTable<TenantVO>(
  (params) => getTenantList({ ...queryForm, ...params })
)

// 对话框相关
const dialogVisible = ref(false)
const dialogTitle = ref('')
const isEdit = ref(false)
const editingTenantId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const formData = reactive<CreateTenantDTO & { email?: string }>({
  tenantName: '',
  tenantCode: '',
  contact: '',
  phone: '',
  email: ''
})

const formRules: FormRules = {
  tenantName: [
    { required: true, message: '请输入租户名称', trigger: 'blur' }
  ],
  tenantCode: [
    { required: true, message: '请输入租户编码', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_-]+$/, message: '租户编码只能包含字母、数字、下划线和连字符', trigger: 'blur' }
  ],
  email: [
    { type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' }
  ],
  phone: [
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' }
  ]
}

// 重置查询表单
const handleReset = () => {
  queryForm.tenantName = ''
  queryForm.tenantCode = ''
  queryForm.status = undefined
  loadData()
}

// 创建租户
const handleCreate = () => {
  dialogTitle.value = '新建租户'
  isEdit.value = false
  dialogVisible.value = true
}

// 编辑租户
const handleEdit = (row: TenantVO) => {
  dialogTitle.value = '编辑租户'
  isEdit.value = true
  editingTenantId.value = row.tenantId
  formData.tenantName = row.tenantName
  formData.tenantCode = row.tenantCode
  formData.contact = row.contact || ''
  formData.phone = row.phone || ''
  formData.email = row.email || ''
  dialogVisible.value = true
}

// 提交表单
const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (valid) {
      try {
        if (isEdit.value && editingTenantId.value) {
          const updateData: UpdateTenantDTO = {
            tenantName: formData.tenantName,
            contact: formData.contact,
            phone: formData.phone,
            email: formData.email
          }
          await updateTenant(editingTenantId.value, updateData)
          ElMessage.success('更新成功')
        } else {
          await createTenant(formData as CreateTenantDTO)
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
  editingTenantId.value = null
  Object.assign(formData, {
    tenantName: '',
    tenantCode: '',
    contact: '',
    phone: '',
    email: ''
  })
}

// 切换租户状态
const handleToggleStatus = async (row: TenantVO) => {
  const action = row.status === 1 ? '禁用' : '启用'
  try {
    await ElMessageBox.confirm(`确定要${action}租户 ${row.tenantName} 吗？`, '提示', {
      type: 'warning'
    })
    const newStatus = row.status === 1 ? 0 : 1
    await updateTenantStatus(row.tenantId, newStatus)
    ElMessage.success(`${action}成功`)
    loadData()
  } catch (error) {
    // 用户取消
  }
}

// 删除租户
const handleDelete = async (row: TenantVO) => {
  try {
    await ElMessageBox.confirm(`确定要删除租户 ${row.tenantName} 吗？`, '提示', {
      type: 'warning'
    })
    await deleteTenant(row.tenantId)
    ElMessage.success('删除成功')
    loadData()
  } catch (error) {
    // 用户取消
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped lang="scss">
.system-tenant-page {
  :deep(.el-pagination) {
    display: flex;
  }
}
</style>
