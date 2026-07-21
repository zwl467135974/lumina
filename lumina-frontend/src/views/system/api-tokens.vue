<template>
  <div class="api-tokens-page">
    <PageHeader :title="t('apiToken.title')" :description="t('apiToken.description')">
      <template #actions>
        <el-button type="primary" @click="handleCreate">
          <el-icon><Plus /></el-icon>
          {{ t('apiToken.create') }}
        </el-button>
      </template>
    </PageHeader>

    <el-card v-loading="loading">
      <el-table :data="list" stripe>
        <el-table-column prop="name" :label="t('apiToken.name')" min-width="160" show-overflow-tooltip />
        <el-table-column prop="scopes" :label="t('apiToken.scopes')" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag v-for="scope in (row.scopes || '').split(',').filter(Boolean)" :key="scope" size="small" class="scope-tag">
              {{ scope }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" :label="t('common.status')" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? t('common.enable') : t('common.disable') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastUsedAt" :label="t('apiToken.lastUsedAt')" width="180">
          <template #default="{ row }">{{ row.lastUsedAt || '-' }}</template>
        </el-table-column>
        <el-table-column prop="expiresAt" :label="t('apiToken.expiresAt')" width="180">
          <template #default="{ row }">{{ row.expiresAt || t('apiToken.neverExpires') }}</template>
        </el-table-column>
        <el-table-column prop="createTime" :label="t('common.createTime')" width="180" />
        <el-table-column :label="t('common.actions')" width="80" fixed="right">
          <template #default="{ row }">
            <el-button link type="danger" @click="handleRevoke(row)">{{ t('apiToken.revoke') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !list.length" :description="t('apiToken.empty')">
        <el-button type="primary" @click="handleCreate">{{ t('apiToken.create') }}</el-button>
      </el-empty>
    </el-card>

    <!-- 创建对话框 -->
    <el-dialog v-model="createDialogVisible" :title="t('apiToken.create')" width="520px" @close="handleCreateDialogClose">
      <el-form :model="formData" :rules="formRules" ref="formRef" label-width="100px">
        <el-form-item :label="t('apiToken.name')" prop="name">
          <el-input v-model="formData.name" :placeholder="t('apiToken.namePlaceholder')" maxlength="64" />
        </el-form-item>
        <el-form-item :label="t('apiToken.expiresIn')" prop="expiresInDays">
          <el-select v-model="formData.expiresInDays" :placeholder="t('common.pleaseSelect')" style="width: 100%">
            <el-option :label="t('apiToken.days30')" :value="30" />
            <el-option :label="t('apiToken.days90')" :value="90" />
            <el-option :label="t('apiToken.days365')" :value="365" />
            <el-option :label="t('apiToken.neverExpires')" :value="0" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="creating" @click="handleSubmit">{{ t('common.ok') }}</el-button>
      </template>
    </el-dialog>

    <!-- 创建成功：明文只显示一次 -->
    <el-dialog v-model="tokenDialogVisible" :title="t('apiToken.createdTitle')" width="560px" :close-on-click-modal="false">
      <el-alert type="warning" :closable="false" show-icon class="token-alert">
        {{ t('apiToken.saveWarning') }}
      </el-alert>
      <div class="token-display">
        <code class="token-text">{{ createdToken }}</code>
        <el-button type="primary" plain size="small" @click="handleCopy">
          <el-icon><CopyDocument /></el-icon>
          {{ t('common.copy') }}
        </el-button>
      </div>
      <template #footer>
        <el-button type="primary" @click="tokenDialogVisible = false">{{ t('apiToken.savedConfirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">

defineOptions({ name: 'SystemApiTokens' })
import { reactive, ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, CopyDocument } from '@element-plus/icons-vue'
import { getApiTokenList, createApiToken, revokeApiToken, type ApiTokenVO, type CreateApiTokenDTO } from '@/api/modules/api-token'
import { PageHeader } from '@/components/common'

const { t } = useI18n()
const loading = ref(false)
const list = ref<ApiTokenVO[]>([])

// 创建对话框
const createDialogVisible = ref(false)
const creating = ref(false)
const formRef = ref<FormInstance>()
const formData = reactive<CreateApiTokenDTO>({
  name: '',
  expiresInDays: 0 // 0 = 永不过期（提交时映射为 undefined）
})

const formRules: FormRules = {
  name: [{ required: true, message: t('apiToken.nameRequired'), trigger: 'blur' }]
}

// 明文展示对话框
const tokenDialogVisible = ref(false)
const createdToken = ref('')

async function loadData() {
  loading.value = true
  try {
    const res = await getApiTokenList()
    list.value = res.data || []
  } catch { list.value = [] }
  finally { loading.value = false }
}

function handleCreate() {
  createDialogVisible.value = true
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    creating.value = true
    try {
      // 0（永不过期）映射为 undefined，不传给后端
      const payload: CreateApiTokenDTO = {
        name: formData.name,
        expiresInDays: formData.expiresInDays || undefined
      }
      const res = await createApiToken(payload)
      createDialogVisible.value = false
      createdToken.value = res.data?.token || ''
      tokenDialogVisible.value = true
      loadData()
    } catch (error) {
      console.error('创建失败:', error)
    } finally {
      creating.value = false
    }
  })
}

function handleCreateDialogClose() {
  formRef.value?.resetFields()
  formData.name = ''
  formData.expiresInDays = 0
}

async function handleCopy() {
  try {
    await navigator.clipboard.writeText(createdToken.value)
    ElMessage.success(t('common.copySuccess'))
  } catch {
    ElMessage.error(t('apiToken.copyFailed'))
  }
}

async function handleRevoke(row: ApiTokenVO) {
  try {
    await ElMessageBox.confirm(
      t('apiToken.revokeConfirm', { name: row.name }),
      t('common.tip'),
      { type: 'warning' }
    )
    await revokeApiToken(row.id)
    ElMessage.success(t('apiToken.revokeSuccess'))
    loadData()
  } catch {
    // 用户取消
  }
}

onMounted(() => loadData())
</script>

<style scoped>
.api-tokens-page { padding: var(--lumina-spacing-lg); }
.scope-tag { margin-right: 4px; }
.token-alert { margin-bottom: 16px; }
.token-display {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  background: var(--lumina-bg-elevated);
  border-radius: 6px;
}
.token-text {
  flex: 1;
  word-break: break-all;
  font-size: var(--lumina-font-size-sm, 13px);
}

@media (max-width: 767px) {
  .api-tokens-page { padding: var(--lumina-spacing-md); }
  .token-display { flex-wrap: wrap; }
}
</style>
