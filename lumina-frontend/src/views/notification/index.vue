<template>
  <div class="notification-page">
    <PageHeader :title="t('notification.title')" :description="t('notification.description')">
      <template #actions>
        <el-button
          v-if="unreadCount > 0"
          type="primary"
          plain
          @click="handleReadAll"
        >
          <el-icon><Check /></el-icon>
          {{ t('notification.markAllRead') }}
        </el-button>
      </template>
    </PageHeader>

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
      <el-table-column prop="category" :label="t('notification.category.BUDGET')" width="100">
        <template #default="{ row }">
          <el-tag size="small" effect="plain">{{ t(`notification.category.${row.category}`) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="severity" :label="t('notification.severityLabel')" width="90">
        <template #default="{ row }">
          <el-tag :type="severityTagType(row.severity)" size="small">
            {{ t(`notification.severity.${row.severity}`) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="title" :label="t('notification.titleLabel')" min-width="180" show-overflow-tooltip />
      <el-table-column prop="content" :label="t('notification.contentLabel')" min-width="280" show-overflow-tooltip />
      <el-table-column prop="isRead" :label="t('notification.readStatusLabel')" width="90">
        <template #default="{ row }">
          <el-tag :type="row.isRead === 1 ? 'info' : 'danger'" size="small">
            {{ row.isRead === 1 ? t('notification.filterRead') : t('notification.filterUnread') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" :label="t('common.createTime')" width="180" />
      <el-table-column :label="t('common.actions')" width="120" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.isRead === 0"
            link
            type="primary"
            @click="handleMarkRead(row)"
          >
            {{ t('notification.markRead') }}
          </el-button>
          <span v-else class="read-done">{{ t('notification.filterRead') }}</span>
        </template>
      </el-table-column>
    </LumTablePanel>
  </div>
</template>

<script setup lang="ts">

defineOptions({ name: 'NotificationList' })
import { reactive, ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check } from '@element-plus/icons-vue'
import { listNotifications, markAsRead, markAllAsRead, type NotificationVO, type NotificationQuery } from '@/api/modules/notification'
import { useTable } from '@/composables/useTable'
import { useNotificationStore } from '@/stores'
import { PageHeader, LumTablePanel, type SearchField } from '@/components/common'

const { t } = useI18n()
const notificationStore = useNotificationStore()

const queryForm = reactive<NotificationQuery>({
  category: undefined,
  isRead: undefined,
  pageNum: 1,
  pageSize: 20,
})

const searchFields: SearchField[] = [
  {
    prop: 'category',
    label: t('notification.categoryLabel'),
    type: 'select',
    options: [
      { label: t('notification.category.BUDGET'), value: 'BUDGET' },
      { label: t('notification.category.TASK'), value: 'TASK' },
      { label: t('notification.category.WORKFLOW'), value: 'WORKFLOW' },
      { label: t('notification.category.DOCUMENT'), value: 'DOCUMENT' },
      { label: t('notification.category.EVALUATION'), value: 'EVALUATION' },
      { label: t('notification.category.SYSTEM'), value: 'SYSTEM' },
    ],
  },
  {
    prop: 'isRead',
    label: t('notification.readStatusLabel'),
    type: 'select',
    options: [
      { label: t('notification.filterAll'), value: '' },
      { label: t('notification.filterUnread'), value: 0 },
      { label: t('notification.filterRead'), value: 1 },
    ],
  },
]

const { tableData, loading, pagination, loadData, handlePageChange, handleSizeChange } = useTable<NotificationVO>(
  async (params) => {
    const res = await listNotifications({
      ...queryForm,
      ...params,
    })
    return res
  }
)

const unreadCount = ref(notificationStore.unreadCount)

function handleReset() {
  queryForm.category = undefined
  queryForm.isRead = undefined
  queryForm.pageNum = 1
  loadData()
}

async function handleMarkRead(row: NotificationVO) {
  try {
    await markAsRead(row.id)
    row.isRead = 1
    unreadCount.value = Math.max(0, unreadCount.value - 1)
    notificationStore.fetchUnreadCount()
    ElMessage.success(t('notification.markReadSuccess'))
  } catch {
    // 错误由全局拦截器处理
  }
}

async function handleReadAll() {
  try {
    await ElMessageBox.confirm(t('notification.readAllConfirm'), t('notification.markAllRead'), {
      type: 'warning',
    })
    await markAllAsRead()
    tableData.value.forEach((n) => (n.isRead = 1))
    unreadCount.value = 0
    notificationStore.fetchUnreadCount()
    ElMessage.success(t('notification.readAllSuccess'))
  } catch {
    // 用户取消或错误
  }
}

function severityTagType(severity: string) {
  switch (severity) {
    case 'ERROR': return 'danger'
    case 'WARN': return 'warning'
    default: return 'info'
  }
}

onMounted(() => {
  loadData()
  notificationStore.fetchUnreadCount().then(() => {
    unreadCount.value = notificationStore.unreadCount
  })
})
</script>

<style scoped>
.notification-page {
  padding: var(--lumina-spacing-lg);
}

.read-done {
  color: var(--lumina-text-placeholder);
  font-size: var(--lumina-font-size-sm);
}
</style>
