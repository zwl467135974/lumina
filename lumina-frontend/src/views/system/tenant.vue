<template>
  <div class="system-tenant-page">
    <PageHeader :title="t('system.tenant.title')" />

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
        <el-button type="primary" v-permission="'tenant:create'" @click="handleCreate">
          <el-icon><Plus /></el-icon>
          {{ t('common.create') }}
        </el-button>
      </template>

      <el-table-column prop="tenantId" label="ID" width="80" />
      <el-table-column prop="tenantName" :label="t('system.tenant.tenantName')" width="200" />
      <el-table-column prop="tenantCode" :label="t('system.tenant.tenantCode')" width="200" />
      <el-table-column prop="contact" :label="t('system.tenant.contact')" width="150" />
      <el-table-column prop="phone" :label="t('system.tenant.phone')" width="150" />
      <el-table-column prop="email" :label="t('system.tenant.email')" width="200" />
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
          <el-button link type="primary" v-permission="'tenant:update'" @click="handleEdit(row)">{{ t('common.edit') }}</el-button>
          <el-button link type="primary" v-permission="'tenant:status'" @click="handleToggleStatus(row)">
            {{ row.status === 1 ? t('common.disable') : t('common.enable') }}
          </el-button>
          <el-button link type="danger" v-permission="'tenant:delete'" @click="handleDelete(row)">{{ t('common.delete') }}</el-button>
        </template>
      </el-table-column>
    </LumTablePanel>

    <!-- 创建/编辑租户对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="600px"
      @close="handleDialogClose"
    >
      <el-form :model="formData" :rules="formRules" ref="formRef" label-width="100px">
        <el-form-item :label="t('system.tenant.tenantName')" prop="tenantName">
          <el-input v-model="formData.tenantName" :placeholder="t('system.tenant.tenantNamePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('system.tenant.tenantCode')" prop="tenantCode">
          <el-input
            v-model="formData.tenantCode"
            :placeholder="t('system.tenant.tenantCodePlaceholder')"
            :disabled="isEdit"
          />
        </el-form-item>
        <el-form-item :label="t('system.tenant.contact')" prop="contact">
          <el-input v-model="formData.contact" :placeholder="t('system.tenant.contactPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('system.tenant.phone')" prop="phone">
          <el-input v-model="formData.phone" :placeholder="t('system.tenant.phonePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('system.tenant.email')" prop="email">
          <el-input v-model="formData.email" :placeholder="t('system.tenant.emailPlaceholder')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleSubmit">{{ t('common.ok') }}</el-button>
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
  getTenantList,
  createTenant,
  updateTenant,
  deleteTenant,
  updateTenantStatus
} from '@/api/modules/system-tenant'
import { useTable } from '@/composables/useTable'
import type { TenantVO, CreateTenantDTO, UpdateTenantDTO, QueryTenantDTO } from '@/types/api'
import { PageHeader, LumTablePanel, type SearchField } from '@/components/common'

const { t } = useI18n()

const queryForm = reactive<QueryTenantDTO>({
  tenantName: '',
  tenantCode: '',
  status: undefined
})

const searchFields = computed<SearchField[]>(() => [
  { prop: 'tenantName', label: t('system.tenant.tenantName'), type: 'input', placeholder: t('system.tenant.tenantNamePlaceholder') },
  { prop: 'tenantCode', label: t('system.tenant.tenantCode'), type: 'input', placeholder: t('system.tenant.tenantCodePlaceholder') },
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
    { required: true, message: t('system.tenant.tenantNameRequired'), trigger: 'blur' }
  ],
  tenantCode: [
    { required: true, message: t('system.tenant.tenantCodeRequired'), trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_-]+$/, message: t('system.tenant.tenantCodePattern'), trigger: 'blur' }
  ],
  email: [
    { type: 'email', message: t('common.emailInvalid'), trigger: 'blur' }
  ],
  phone: [
    { pattern: /^1[3-9]\d{9}$/, message: t('common.phoneInvalid'), trigger: 'blur' }
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
  dialogTitle.value = t('system.tenant.create')
  isEdit.value = false
  dialogVisible.value = true
}

// 编辑租户
const handleEdit = (row: TenantVO) => {
  dialogTitle.value = t('system.tenant.edit')
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
          ElMessage.success(t('common.updateSuccess'))
        } else {
          await createTenant(formData as CreateTenantDTO)
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
  const action = row.status === 1 ? t('common.disable') : t('common.enable')
  try {
    await ElMessageBox.confirm(
      t('system.tenant.toggleStatusConfirm', { action, name: row.tenantName }),
      t('common.tip'),
      { type: 'warning' }
    )
    const newStatus = row.status === 1 ? 0 : 1
    await updateTenantStatus(row.tenantId, newStatus)
    ElMessage.success(row.status === 1 ? t('common.disableSuccess') : t('common.enableSuccess'))
    loadData()
  } catch (error) {
    // 用户取消
  }
}

// 删除租户
const handleDelete = async (row: TenantVO) => {
  try {
    await ElMessageBox.confirm(
      t('system.tenant.deleteConfirm', { name: row.tenantName }),
      t('common.tip'),
      { type: 'warning' }
    )
    await deleteTenant(row.tenantId)
    ElMessage.success(t('common.deleteSuccess'))
    loadData()
  } catch (error) {
    // 用户取消
  }
}

onMounted(() => {
  loadData()
})
</script>
