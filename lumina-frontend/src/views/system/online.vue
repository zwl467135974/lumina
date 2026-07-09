<template>
  <div class="system-online-page">
    <PageHeader :title="t('system.online.title')" :description="t('system.online.description')" />

    <el-card shadow="never">
      <div style="margin-bottom: 16px; display: flex; gap: 8px; align-items: center">
        <el-input
          v-model="searchUsername"
          :placeholder="t('system.online.searchPlaceholder')"
          clearable
          style="width: 240px"
          @keyup.enter="loadData"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button type="primary" @click="loadData">{{ t('common.search') }}</el-button>
        <el-button @click="handleReset">{{ t('common.reset') }}</el-button>
        <el-tag type="info" style="margin-left: auto">{{ list.length }} {{ t('system.online.count') }}</el-tag>
      </div>

      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="userId" label="ID" width="80" />
        <el-table-column prop="username" :label="t('system.user.username')" min-width="150" />
        <el-table-column prop="loginTime" :label="t('system.online.loginTime')" width="200">
          <template #default="{ row }">{{ formatTime(row.loginTime) }}</template>
        </el-table-column>
        <el-table-column :label="t('common.actions')" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="danger" @click="handleForceLogout(row)">
              {{ t('system.online.forceLogout') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && list.length === 0" :description="t('common.noData')" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { getOnlineUsers, forceLogout, type OnlineUserVO } from '@/api/modules/system-online'
import { PageHeader } from '@/components/common'

const { t } = useI18n()
const loading = ref(false)
const list = ref<OnlineUserVO[]>([])
const searchUsername = ref('')

const loadData = async () => {
  loading.value = true
  try {
    const res = await getOnlineUsers(searchUsername.value || undefined)
    list.value = res.data || []
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

const handleReset = () => {
  searchUsername.value = ''
  loadData()
}

const handleForceLogout = async (row: OnlineUserVO) => {
  try {
    await ElMessageBox.confirm(
      t('system.online.forceConfirm', { name: row.username }),
      t('common.tip'),
      { type: 'warning' }
    )
    await forceLogout(row.userId)
    ElMessage.success(t('system.online.forceSuccess'))
    loadData()
  } catch {
    // 用户取消
  }
}

const formatTime = (dt?: string) => {
  if (!dt) return '-'
  return dt.replace('T', ' ').substring(0, 19)
}

onMounted(() => loadData())
</script>
