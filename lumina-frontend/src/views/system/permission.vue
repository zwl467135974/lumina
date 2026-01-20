<template>
  <div class="system-permission-page">
    <page-header title="权限管理">
      <el-button type="primary" v-permission="'permission:create'" @click="handleCreate">
        <el-icon><Plus /></el-icon>
        新建权限
      </el-button>
    </page-header>

    <el-card>
      <el-form :model="queryForm" inline>
        <el-form-item label="权限名称">
          <el-input v-model="queryForm.permissionName" placeholder="请输入权限名称" clearable />
        </el-form-item>
        <el-form-item label="权限编码">
          <el-input v-model="queryForm.permissionCode" placeholder="请输入权限编码" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadPermissionTree">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button @click="expandAll">展开全部</el-button>
          <el-button @click="collapseAll">折叠全部</el-button>
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
        <el-table-column prop="permissionName" label="权限名称" width="200" />
        <el-table-column prop="permissionCode" label="权限编码" width="200" />
        <el-table-column prop="resourcePath" label="资源路径" show-overflow-tooltip />
        <el-table-column prop="description" label="描述" show-overflow-tooltip />
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" v-permission="'permission:create'" @click="handleCreateChild(row)">
              添加子权限
            </el-button>
            <el-button link type="primary" v-permission="'permission:update'" @click="handleEdit(row)">
              编辑
            </el-button>
            <el-button link type="danger" v-permission="'permission:delete'" @click="handleDelete(row)">
              删除
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
        <el-form-item label="权限名称" prop="permissionName">
          <el-input v-model="formData.permissionName" placeholder="请输入权限名称" />
        </el-form-item>
        <el-form-item label="权限编码" prop="permissionCode">
          <el-input
            v-model="formData.permissionCode"
            placeholder="请输入权限编码，如：user:create"
            :disabled="isEdit"
          />
        </el-form-item>
        <el-form-item label="资源路径" prop="resourcePath">
          <el-input v-model="formData.resourcePath" placeholder="请输入资源路径，如：/api/v1/users" />
        </el-form-item>
        <el-form-item label="父级权限" prop="parentId">
          <el-tree-select
            v-model="formData.parentId"
            :data="parentPermissionTree"
            :props="{ label: 'permissionName', value: 'permissionId' }"
            placeholder="请选择父级权限（不选则为顶级权限）"
            clearable
            check-strictly
            :render-after-expand="false"
          />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="formData.description"
            type="textarea"
            :rows="3"
            placeholder="请输入权限描述"
          />
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
import { reactive, ref, onMounted, computed } from 'vue'
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

const queryForm = reactive<QueryPermissionDTO>({
  permissionName: '',
  permissionCode: '',
  resourcePath: ''
})

const loading = ref(false)
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
    { required: true, message: '请输入权限名称', trigger: 'blur' }
  ],
  permissionCode: [
    { required: true, message: '请输入权限编码', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9:_-]+$/, message: '权限编码格式不正确', trigger: 'blur' }
  ],
  resourcePath: [
    { required: true, message: '请输入资源路径', trigger: 'blur' }
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
    ElMessage.error('加载权限树失败')
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
  dialogTitle.value = '新建权限'
  isEdit.value = false
  formData.parentId = undefined
  dialogVisible.value = true
}

// 创建子权限
const handleCreateChild = (row: PermissionVO) => {
  dialogTitle.value = '新建子权限'
  isEdit.value = false
  formData.parentId = row.permissionId
  dialogVisible.value = true
}

// 编辑权限
const handleEdit = (row: PermissionVO) => {
  dialogTitle.value = '编辑权限'
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
      try {
        if (isEdit.value && editingPermissionId.value) {
          const updateData: UpdatePermissionDTO = {
            permissionName: formData.permissionName,
            resourcePath: formData.resourcePath,
            description: formData.description,
            parentId: formData.parentId
          }
          await updatePermission(editingPermissionId.value, updateData)
          ElMessage.success('更新成功')
        } else {
          await createPermission(formData as CreatePermissionDTO)
          ElMessage.success('创建成功')
        }
        dialogVisible.value = false
        loadPermissionTree()
      } catch (error) {
        console.error('操作失败:', error)
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
    ElMessage.warning('该权限存在子权限，请先删除子权限')
    return
  }

  try {
    await ElMessageBox.confirm(
      `确定要删除权限 ${row.permissionName} 吗？`,
      '提示',
      {
        type: 'warning'
      }
    )
    await deletePermission(row.permissionId)
    ElMessage.success('删除成功')
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
</style>
