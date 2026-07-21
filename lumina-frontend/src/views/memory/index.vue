<template>
  <div class="memory-page">
    <PageHeader :title="t('memory.title')" :description="t('memory.description')">
      <template #actions>
        <el-popconfirm :title="t('memory.clearConfirm')" @confirm="handleClearAll">
          <template #reference>
            <el-button type="danger" plain :disabled="!list.length">
              <el-icon><Delete /></el-icon>
              {{ t('memory.clearAll') }}
            </el-button>
          </template>
        </el-popconfirm>
      </template>
    </PageHeader>

    <el-card v-loading="loading">
      <el-table :data="list" stripe>
        <el-table-column prop="content" :label="t('memory.content')" min-width="300" show-overflow-tooltip />
        <el-table-column prop="memoryType" :label="t('memory.type')" width="90">
          <template #default="{ row }">
            <el-tag size="small">{{ row.memoryType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="importance" :label="t('memory.importance')" width="100" sortable>
          <template #default="{ row }">
            <el-progress :percentage="Math.round(row.importance * 100)" :stroke-width="6" />
          </template>
        </el-table-column>
        <el-table-column prop="conversationId" :label="t('memory.source')" width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="text-muted">{{ row.conversationId ? row.conversationId.substring(0, 12) + '...' : '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" :label="t('common.createTime')" width="180" />
        <el-table-column :label="t('common.actions')" width="80" fixed="right">
          <template #default="{ row }">
            <el-button link type="danger" @click="handleDelete(row)">
              {{ t('common.delete') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !list.length" :description="t('memory.empty')" />
    </el-card>
  </div>
</template>

<script setup lang="ts">

defineOptions({ name: 'LongTermMemory' })
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Delete } from '@element-plus/icons-vue'
import { listLongTermMemories, deleteLongTermMemory, deleteAllLongTermMemories, type LongTermMemoryVO } from '@/api/modules/long-term-memory'
import { PageHeader } from '@/components/common'

const { t } = useI18n()
const loading = ref(false)
const list = ref<LongTermMemoryVO[]>([])

async function loadData() {
  loading.value = true
  try {
    const res = await listLongTermMemories()
    list.value = res.data || []
  } catch { list.value = [] }
  finally { loading.value = false }
}

async function handleDelete(row: LongTermMemoryVO) {
  await deleteLongTermMemory(row.id)
  ElMessage.success(t('common.deleteSuccess'))
  loadData()
}

async function handleClearAll() {
  await deleteAllLongTermMemories()
  ElMessage.success(t('memory.cleared'))
  loadData()
}

onMounted(() => loadData())
</script>

<style scoped>
.memory-page { padding: var(--lumina-spacing-lg); }
.text-muted { color: var(--lumina-text-muted); font-size: var(--lumina-font-size-sm); }
</style>
