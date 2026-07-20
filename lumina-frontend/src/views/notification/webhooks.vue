<template>
  <div class="webhooks-page">
    <PageHeader :title="t('webhook.title')" :description="t('webhook.description')">
      <template #actions>
        <el-button type="primary" @click="handleCreate">
          <el-icon><Plus /></el-icon>
          {{ t('webhook.create') }}
        </el-button>
      </template>
    </PageHeader>

    <el-card v-loading="loading">
      <el-table :data="list" stripe>
        <el-table-column prop="name" :label="t('webhook.name')" min-width="140" show-overflow-tooltip />
        <el-table-column prop="channel" :label="t('webhook.channel')" width="110">
          <template #default="{ row }">
            <el-tag :type="channelTagType(row.channel)" size="small">
              {{ channelLabel(row.channel) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="url" :label="t('webhook.url')" min-width="220" show-overflow-tooltip />
        <el-table-column prop="events" :label="t('webhook.events')" min-width="150">
          <template #default="{ row }">
            <el-tag v-if="row.events === '*'" size="small" type="info">{{ t('webhook.allEvents') }}</el-tag>
            <el-tag v-for="evt in row.events === '*' ? [] : row.events.split(',')" :key="evt" size="small" class="event-tag">
              {{ evt }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="enabled" :label="t('common.status')" width="90">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 1 ? 'success' : 'danger'" size="small">
              {{ row.enabled === 1 ? t('common.enable') : t('common.disable') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastStatus" :label="t('webhook.lastStatus')" width="120">
          <template #default="{ row }">
            <el-tooltip v-if="row.lastStatus === 'FAILED' && row.lastError" :content="row.lastError" placement="top">
              <el-tag type="danger" size="small">FAILED ({{ row.failCount }})</el-tag>
            </el-tooltip>
            <el-tag v-else-if="row.lastStatus === 'SUCCESS'" type="success" size="small">SUCCESS</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="lastTriggeredAt" :label="t('webhook.lastTriggeredAt')" width="170">
          <template #default="{ row }">{{ row.lastTriggeredAt || '-' }}</template>
        </el-table-column>
        <el-table-column :label="t('common.actions')" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :loading="testingId === row.id" @click="handleTest(row)">
              {{ t('webhook.test') }}
            </el-button>
            <el-button link type="danger" @click="handleDelete(row)">{{ t('common.delete') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !list.length" :description="t('webhook.empty')" />
    </el-card>

    <!-- 创建对话框 -->
    <el-dialog v-model="createDialogVisible" :title="t('webhook.create')" width="560px" @close="handleCreateDialogClose">
      <el-form :model="formData" :rules="formRules" ref="formRef" label-width="100px">
        <el-form-item :label="t('webhook.name')" prop="name">
          <el-input v-model="formData.name" :placeholder="t('webhook.namePlaceholder')" maxlength="64" />
        </el-form-item>
        <el-form-item :label="t('webhook.channel')" prop="channel">
          <el-select v-model="formData.channel" style="width: 100%">
            <el-option :label="t('webhook.channelWebhook')" value="WEBHOOK" />
            <el-option :label="t('webhook.channelWeCom')" value="WE_COM" />
            <el-option :label="t('webhook.channelDingTalk')" value="DINGTALK" />
            <el-option :label="t('webhook.channelFeishu')" value="FEISHU" />
            <el-option :label="t('webhook.channelTelegram')" value="TELEGRAM" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('webhook.url')" prop="url">
          <el-input
            v-model="formData.url"
            :placeholder="urlPlaceholder"
            maxlength="512"
          />
        </el-form-item>
        <el-alert
          v-if="channelTip"
          type="info"
          :closable="false"
          show-icon
          class="wecom-tip"
        >
          {{ channelTip }}
        </el-alert>
        <el-form-item v-if="formData.channel === 'WEBHOOK'" :label="t('webhook.secret')" prop="secret">
          <el-input v-model="formData.secret" :placeholder="t('webhook.secretPlaceholder')" maxlength="128" />
        </el-form-item>
        <el-form-item :label="t('webhook.events')" prop="events">
          <el-select v-model="formData.events" multiple :placeholder="t('webhook.eventsPlaceholder')" style="width: 100%">
            <el-option :label="t('webhook.allEvents')" value="*" />
            <el-option v-for="cat in categories" :key="cat" :label="cat" :value="cat" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="creating" @click="handleSubmit">{{ t('common.ok') }}</el-button>
      </template>
    </el-dialog>

    <!-- 创建成功：secret 明文只显示一次 -->
    <el-dialog v-model="secretDialogVisible" :title="t('webhook.createdTitle')" width="560px" :close-on-click-modal="false">
      <el-alert type="warning" :closable="false" show-icon class="secret-alert">
        {{ t('webhook.saveWarning') }}
      </el-alert>
      <div class="secret-display">
        <code class="secret-text">{{ createdSecret }}</code>
        <el-button type="primary" plain size="small" @click="handleCopy">
          <el-icon><CopyDocument /></el-icon>
          {{ t('common.copy') }}
        </el-button>
      </div>
      <template #footer>
        <el-button type="primary" @click="secretDialogVisible = false">{{ t('webhook.savedConfirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, CopyDocument } from '@element-plus/icons-vue'
import {
  listWebhooks, createWebhook, deleteWebhook, testWebhook,
  type WebhookVO, type WebhookChannel
} from '@/api/modules/webhook'
import { PageHeader } from '@/components/common'

const { t } = useI18n()
const loading = ref(false)
const list = ref<WebhookVO[]>([])
const testingId = ref<number | null>(null)

/** 后端 NotificationCategory 枚举 */
const categories = ['BUDGET', 'TASK', 'WORKFLOW', 'DOCUMENT', 'EVALUATION', 'SYSTEM']

/** 渠道展示名（i18n key 映射） */
const CHANNEL_LABEL_KEYS: Record<string, string> = {
  WEBHOOK: 'webhook.channelWebhook',
  WE_COM: 'webhook.channelWeCom',
  DINGTALK: 'webhook.channelDingTalk',
  FEISHU: 'webhook.channelFeishu',
  TELEGRAM: 'webhook.channelTelegram'
}

/** 渠道标签颜色（通用 Webhook 蓝色，IM 渠道绿色） */
const CHANNEL_TAG_TYPES: Record<string, 'primary' | 'success' | 'warning'> = {
  WEBHOOK: 'primary',
  WE_COM: 'success',
  DINGTALK: 'success',
  FEISHU: 'success',
  TELEGRAM: 'success'
}

/** 各渠道 URL 输入提示（i18n key） */
const CHANNEL_URL_PLACEHOLDER_KEYS: Record<string, string> = {
  WEBHOOK: 'webhook.urlPlaceholder',
  WE_COM: 'webhook.weComUrlPlaceholder',
  DINGTALK: 'webhook.dingTalkUrlPlaceholder',
  FEISHU: 'webhook.feishuUrlPlaceholder',
  TELEGRAM: 'webhook.telegramUrlPlaceholder'
}

/** 各渠道配置说明（i18n key，WEBHOOK 无需说明） */
const CHANNEL_TIP_KEYS: Record<string, string> = {
  WE_COM: 'webhook.weComTip',
  DINGTALK: 'webhook.dingTalkTip',
  FEISHU: 'webhook.feishuTip',
  TELEGRAM: 'webhook.telegramTip'
}

function channelLabel(channel: string) {
  return t(CHANNEL_LABEL_KEYS[channel] || 'webhook.channelWebhook')
}

function channelTagType(channel: string) {
  return CHANNEL_TAG_TYPES[channel] || 'primary'
}

// 创建对话框
const createDialogVisible = ref(false)
const creating = ref(false)
const formRef = ref<FormInstance>()
const formData = reactive<{
  name: string
  url: string
  channel: WebhookChannel
  secret: string
  events: string[]
}>({
  name: '',
  url: '',
  channel: 'WEBHOOK',
  secret: '',
  events: ['*']
})

const formRules: FormRules = {
  name: [{ required: true, message: t('webhook.nameRequired'), trigger: 'blur' }],
  url: [
    { required: true, message: t('webhook.urlRequired'), trigger: 'blur' },
    { pattern: /^https?:\/\/.+/, message: t('webhook.urlInvalid'), trigger: 'blur' }
  ]
}

/** 当前渠道的 URL 输入提示 */
const urlPlaceholder = computed(() =>
  t(CHANNEL_URL_PLACEHOLDER_KEYS[formData.channel] || 'webhook.urlPlaceholder')
)

/** 当前渠道的配置说明（WEBHOOK 渠道无） */
const channelTip = computed(() => {
  const key = CHANNEL_TIP_KEYS[formData.channel]
  return key ? t(key) : ''
})

// secret 明文展示对话框
const secretDialogVisible = ref(false)
const createdSecret = ref('')

async function loadData() {
  loading.value = true
  try {
    const res = await listWebhooks()
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
      const res = await createWebhook({
        name: formData.name,
        url: formData.url,
        channel: formData.channel,
        secret: formData.channel === 'WEBHOOK' && formData.secret ? formData.secret : undefined,
        events: formData.events.includes('*') ? ['*'] : formData.events
      })
      createDialogVisible.value = false
      // WEBHOOK 渠道展示一次性 secret；WE_COM 无 secret
      if (res.data?.secret) {
        createdSecret.value = res.data.secret
        secretDialogVisible.value = true
      } else {
        ElMessage.success(t('webhook.createSuccess'))
      }
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
  formData.url = ''
  formData.channel = 'WEBHOOK'
  formData.secret = ''
  formData.events = ['*']
}

async function handleCopy() {
  try {
    await navigator.clipboard.writeText(createdSecret.value)
    ElMessage.success(t('common.copySuccess'))
  } catch {
    ElMessage.error(t('webhook.copyFailed'))
  }
}

async function handleTest(row: WebhookVO) {
  testingId.value = row.id
  try {
    const res = await testWebhook(row.id)
    if (res.data?.success) {
      ElMessage.success(t('webhook.testSuccess'))
    } else {
      ElMessage.error(t('webhook.testFailed'))
    }
    loadData()
  } catch {
    ElMessage.error(t('webhook.testFailed'))
  } finally {
    testingId.value = null
  }
}

async function handleDelete(row: WebhookVO) {
  try {
    await ElMessageBox.confirm(
      t('webhook.deleteConfirm', { name: row.name }),
      t('common.tip'),
      { type: 'warning' }
    )
    await deleteWebhook(row.id)
    ElMessage.success(t('common.deleteSuccess'))
    loadData()
  } catch {
    // 用户取消
  }
}

onMounted(() => loadData())
</script>

<style scoped>
.webhooks-page { padding: var(--lumina-spacing-lg); }
.event-tag { margin-right: 4px; }
.wecom-tip { margin: 0 0 18px 100px; }
.secret-alert { margin-bottom: 16px; }
.secret-display {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  background: var(--lumina-bg-secondary, #f5f7fa);
  border-radius: 6px;
}
.secret-text {
  flex: 1;
  word-break: break-all;
  font-size: var(--lumina-font-size-sm, 13px);
}
</style>
