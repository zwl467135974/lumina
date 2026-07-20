<template>
  <div class="budget-page">
    <PageHeader :title="t('budget.title')" :description="t('budget.description')">
      <template #actions>
        <el-button type="primary" @click="showCreateDialog">{{ t('budget.createRule') }}</el-button>
        <el-button @click="loadUsage">{{ t('common.refresh') }}</el-button>
      </template>
    </PageHeader>

    <!-- 预算使用概览 -->
    <el-card shadow="never" class="usage-card" v-loading="usageLoading">
      <template #header>{{ t('budget.usageStatus') }}</template>
      <div v-if="usageList.length === 0 && !usageLoading" class="empty-tip">
        {{ t('budget.noRule') }}
      </div>
      <div v-for="item in usageList" :key="item.ruleId" class="usage-item">
        <div class="usage-header">
          <span class="usage-name">{{ item.ruleName }}</span>
          <el-tag size="small" type="info">{{ item.scopeType }}</el-tag>
          <el-tag size="small">{{ item.periodType }}</el-tag>
        </div>
        <el-progress
          :percentage="Math.min(item.usagePercent, 100)"
          :color="usageColor(item.usagePercent, item.alertThreshold)"
          :stroke-width="16"
          :text-inside="true"
        />
        <div class="usage-detail">
          ¥{{ item.currentUsage.toFixed(4) }} / ¥{{ item.limitAmount.toFixed(4) }}
          <span class="usage-percent">({{ item.usagePercent.toFixed(1) }}%)</span>
          <span v-if="item.usagePercent >= item.alertThreshold" class="usage-alert">⚠ 告警阈值 {{ item.alertThreshold }}%</span>
        </div>
      </div>
    </el-card>

    <!-- 规则列表 -->
    <el-card shadow="never" class="rules-card">
      <template #header>{{ t('budget.ruleList') }}</template>
      <el-table :data="rules" stripe v-loading="rulesLoading">
        <el-table-column prop="ruleName" :label="t('budget.ruleName')" min-width="150" />
        <el-table-column prop="scopeType" :label="t('budget.scope')" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ row.scopeType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="scopeId" :label="t('budget.scopeId')" width="100">
          <template #default="{ row }">{{ row.scopeId ?? '-' }}</template>
        </el-table-column>
        <el-table-column prop="periodType" :label="t('budget.period')" width="100">
          <template #default="{ row }">{{ row.periodType === 'DAILY' ? '日' : '月' }}</template>
        </el-table-column>
        <el-table-column prop="limitAmount" :label="t('budget.limitYuan')" width="120">
          <template #default="{ row }">¥ {{ row.limitAmount.toFixed(4) }}</template>
        </el-table-column>
        <el-table-column prop="alertThreshold" :label="t('budget.alertThreshold')" width="100">
          <template #default="{ row }">{{ row.alertThreshold }}%</template>
        </el-table-column>
        <el-table-column :label="t('common.actions')" width="80" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="danger" @click="handleDelete(row.id)">{{ t('common.delete') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新建对话框 -->
    <el-dialog v-model="dialogVisible" :title="t('budget.createRule')" width="500px" :close-on-click-modal="false">
      <el-form :model="formData" label-width="100px">
        <el-form-item :label="t('budget.ruleName')" required>
          <el-input v-model="formData.ruleName" :placeholder="t('budget.ruleNamePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('budget.scopeType')" required>
          <el-select v-model="formData.scopeType" style="width: 100%">
            <el-option :label="t('budget.tenantLevel')" value="TENANT" />
            <el-option :label="t('budget.agentLevel')" value="AGENT" />
            <el-option :label="t('budget.userLevel')" value="USER" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="formData.scopeType !== 'TENANT'" :label="t('budget.scopeId')">
          <el-input-number v-model="formData.scopeId" :min="1" :placeholder="t('budget.scopeIdPlaceholder')" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="t('budget.period')" required>
          <el-radio-group v-model="formData.periodType">
            <el-radio value="DAILY">日预算</el-radio>
            <el-radio value="MONTHLY">月预算</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="t('budget.limit')" required>
          <el-input-number v-model="formData.limitAmount" :min="0.01" :precision="4" :step="1" style="width: 100%" />
          <span class="form-hint">单位：元</span>
        </el-form-item>
        <el-form-item :label="t('budget.alert')">
          <el-slider v-model="formData.alertThreshold" :min="10" :max="99" :step="5" show-input style="width: 100%" />
          <span class="form-hint">达到此百分比时记录告警日志</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import {
  listBudgetRules, createBudgetRule, deleteBudgetRule, getBudgetUsage,
  type BudgetRuleVO, type BudgetUsageVO, type BudgetRuleDTO
} from '@/api/modules/budget'

const { t } = useI18n()

const rules = ref<BudgetRuleVO[]>([])
const usageList = ref<BudgetUsageVO[]>([])
const rulesLoading = ref(false)
const usageLoading = ref(false)
const dialogVisible = ref(false)
const saving = ref(false)

const formData = reactive<BudgetRuleDTO>({
  ruleName: '',
  scopeType: 'TENANT',
  scopeId: undefined,
  periodType: 'DAILY',
  limitAmount: 10,
  alertThreshold: 80
})

const loadRules = async () => {
  rulesLoading.value = true
  try {
    const res = await listBudgetRules()
    rules.value = res.data || []
  } catch {
    rules.value = []
  } finally {
    rulesLoading.value = false
  }
}

const loadUsage = async () => {
  usageLoading.value = true
  try {
    const res = await getBudgetUsage()
    usageList.value = res.data || []
  } catch {
    usageList.value = []
  } finally {
    usageLoading.value = false
  }
}

const showCreateDialog = () => {
  formData.ruleName = ''
  formData.scopeType = 'TENANT'
  formData.scopeId = undefined
  formData.periodType = 'DAILY'
  formData.limitAmount = 10
  formData.alertThreshold = 80
  dialogVisible.value = true
}

const handleSave = async () => {
  if (!formData.ruleName.trim()) {
    ElMessage.warning('请输入规则名称')
    return
  }
  if (formData.scopeType !== 'TENANT' && !formData.scopeId) {
    ElMessage.warning('请输入范围 ID')
    return
  }
  saving.value = true
  try {
    await createBudgetRule({ ...formData })
    ElMessage.success(t('common.createSuccess'))
    dialogVisible.value = false
    await Promise.all([loadRules(), loadUsage()])
  } catch (e: any) {
    ElMessage.error(e.message || t('common.failed'))
  } finally {
    saving.value = false
  }
}

const handleDelete = async (id: number) => {
  try {
    await ElMessageBox.confirm(t('budget.deleteConfirm'), t('common.tip'), { type: 'warning' })
    await deleteBudgetRule(id)
    ElMessage.success(t('common.deleteSuccess'))
    await Promise.all([loadRules(), loadUsage()])
  } catch { /* cancelled */ }
}

const usageColor = (percent: number, threshold: number) => {
  if (percent >= 100) return 'var(--lumina-danger)'
  if (percent >= threshold) return 'var(--lumina-warning)'
  return 'var(--lumina-success)'
}

onMounted(() => {
  Promise.all([loadRules(), loadUsage()])
})
</script>

<style scoped>
.budget-page { padding: 0; }
.usage-card { margin-bottom: 16px; }
.usage-item { margin-bottom: 16px; }
.usage-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.usage-name { font-weight: 600; font-size: 14px; }
.usage-detail {
  margin-top: 4px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.usage-percent { font-weight: 500; }
.usage-alert { color: var(--el-color-warning); margin-left: 8px; }
.rules-card { margin-bottom: 16px; }
.empty-tip {
  text-align: center;
  padding: 40px;
  color: var(--el-text-color-placeholder);
}
.form-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-left: 8px;
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
