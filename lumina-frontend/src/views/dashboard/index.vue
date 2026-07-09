<template>
  <div class="lumina-dashboard">
    <!-- Page Header -->
    <PageHeader :title="t('dashboard.title')" :description="t('dashboard.description')" class="dashboard-header" />

    <!-- Stat Cards Row -->
    <div class="stat-cards-grid">
      <div
        v-for="(stat, idx) in statCards"
        :key="stat.key"
        class="stat-card"
        :class="[`stat-card--${stat.colorKey}`, { 'stat-card--clickable': stat.clickable }]"
        :style="{ animationDelay: `${0.1 + idx * 0.08}s` }"
        @click="stat.clickable && stat.route ? router.push(stat.route) : undefined"
      >
        <div class="stat-card__glow" />
        <div class="stat-card__icon-circle">
          <el-icon :size="26"><component :is="stat.icon" /></el-icon>
        </div>
        <div class="stat-card__body">
          <span class="stat-card__label">{{ stat.label }}</span>
          <span class="stat-card__value">{{ stat.value }}</span>
        </div>
      </div>
    </div>

    <!-- Content Row: Tasks + Quick Actions -->
    <div class="content-grid">
      <!-- Recent Tasks -->
      <div class="content-card tasks-card">
        <div class="content-card__header">
          <h2 class="content-card__title">{{ t('dashboard.recentTasks') }}</h2>
          <div class="content-card__title-line" />
        </div>

        <el-table
          v-loading="loading"
          :data="recentTasks"
          class="lumina-table"
          :header-cell-class-name="() => 'lumina-table__header'"
          size="small"
        >
          <el-table-column prop="taskUuid" :label="t('dashboard.taskUuid') || 'UUID'" width="110">
            <template #default="{ row }">
              <span class="table-uuid">{{ row.taskUuid?.substring(0, 8) }}…</span>
            </template>
          </el-table-column>
          <el-table-column prop="agentId" :label="t('dashboard.agent') || 'Agent'" width="72" />
          <el-table-column prop="inputText" :label="t('dashboard.input') || 'Input'" min-width="200" show-overflow-tooltip />
          <el-table-column :label="t('dashboard.status') || 'Status'" width="96">
            <template #default="{ row }">
              <span class="status-tag" :class="`status-tag--${taskStatusType(row.status)}`">
                {{ taskStatusLabel(row.status) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" :label="t('dashboard.time') || 'Time'" width="154" />
        </el-table>

        <div v-if="!loading && recentTasks.length === 0" class="empty-state">
          <el-icon :size="40"><Document /></el-icon>
          <span>{{ t('common.noData') }}</span>
        </div>
      </div>

      <!-- Quick Actions -->
      <div class="content-card actions-card">
        <div class="content-card__header">
          <h2 class="content-card__title">{{ t('dashboard.quickActions') }}</h2>
          <div class="content-card__title-line" />
        </div>

        <div class="action-tiles">
          <div class="action-tile" @click="router.push('/agent/create')">
            <div class="action-tile__icon action-tile__icon--primary">
              <el-icon :size="22"><Plus /></el-icon>
            </div>
            <div class="action-tile__text">
              <span class="action-tile__title">{{ t('dashboard.createAgent') }}</span>
              <span class="action-tile__desc">{{ t('dashboard.createAgentDesc') || 'Build a new AI agent' }}</span>
            </div>
          </div>
          <div class="action-tile" @click="router.push('/knowledge')">
            <div class="action-tile__icon action-tile__icon--success">
              <el-icon :size="22"><Upload /></el-icon>
            </div>
            <div class="action-tile__text">
              <span class="action-tile__title">{{ t('dashboard.uploadDoc') }}</span>
              <span class="action-tile__desc">{{ t('dashboard.uploadDocDesc') || 'Add documents to knowledge base' }}</span>
            </div>
          </div>
          <div class="action-tile" @click="router.push('/evaluation')">
            <div class="action-tile__icon action-tile__icon--accent">
              <el-icon :size="22"><DataAnalysis /></el-icon>
            </div>
            <div class="action-tile__text">
              <span class="action-tile__title">{{ t('dashboard.newEvaluation') }}</span>
              <span class="action-tile__desc">{{ t('dashboard.newEvaluationDesc') || 'Run a new evaluation suite' }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Monitor, List, DataLine, Money, Plus, Upload, DataAnalysis, Document } from '@element-plus/icons-vue'
import { getStats, getRecentTasks } from '@/api/dashboard'
import PageHeader from '@/components/common/PageHeader.vue'

const { t } = useI18n()
const router = useRouter()
const loading = ref(false)

const stats = reactive({
  agentCount: 0,
  todayTasks: 0,
  totalTokens: 0,
  totalCost: 0,
})

const recentTasks = ref([])

// Stat card definitions for template rendering
const statCards = computed(() => [
  {
    key: 'agents',
    colorKey: 'primary',
    icon: Monitor,
    label: t('dashboard.agentCount'),
    value: stats.agentCount,
    clickable: true,
    route: '/agent/list',
  },
  {
    key: 'tasks',
    colorKey: 'success',
    icon: List,
    label: t('dashboard.todayTasks'),
    value: stats.todayTasks,
    clickable: true,
    route: '/agent/tasks',
  },
  {
    key: 'tokens',
    colorKey: 'accent',
    icon: DataLine,
    label: t('dashboard.totalTokens'),
    value: stats.totalTokens?.toLocaleString?.() ?? stats.totalTokens,
    clickable: false,
  },
  {
    key: 'cost',
    colorKey: 'danger',
    icon: Money,
    label: t('dashboard.totalCost'),
    value: `¥ ${(stats.totalCost ?? 0).toFixed(4)}`,
    clickable: false,
  },
])

function taskStatusLabel(status) {
  const map = {
    pending: t('task.pending') || 'Pending',
    running: t('task.running') || 'Running',
    completed: t('task.completed') || 'Completed',
    failed: t('task.failed') || 'Failed',
  }
  return map[status] || status
}

function taskStatusType(status) {
  const map = {
    running: 'info',
    completed: 'success',
    failure: 'danger',
    failed: 'danger',
    pending: 'warning',
  }
  return map[status] || 'info'
}

async function loadDashboard() {
  loading.value = true
  try {
    const [statsRes, tasksRes] = await Promise.all([getStats(), getRecentTasks()])
    Object.assign(stats, statsRes.data ?? statsRes)
    recentTasks.value = tasksRes?.data ?? tasksRes ?? []
  } catch {
    // keep defaults; user can retry
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadDashboard()
})
</script>

<style scoped>
/* ================================================================
   LUMINOUS DARK THEME – Dashboard Styles
   ================================================================ */

/* ---- Design Tokens --------------------------------------------------- */
.lumina-dashboard {
  font-family: var(--lumina-font-body);
  color: var(--lumina-text-primary);
  padding: 24px;
  min-height: 100%;
  display: flex;
  flex-direction: column;
  gap: 24px;
}

/* ---- Page Header ----------------------------------------------------- */
.dashboard-header {
  animation: fadeInUp 0.5s ease both;
}

/* ---- Animations ------------------------------------------------------ */
@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(16px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* ---- Stat Cards Grid ------------------------------------------------- */
.stat-cards-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.stat-card {
  position: relative;
  overflow: hidden;
  background: var(--lumina-bg-card);
  border: 1px solid var(--lumina-border);
  border-radius: 12px;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 20px;
  transition: transform var(--lumina-transition-base),
              border-color var(--lumina-transition-base),
              box-shadow var(--lumina-transition-base);
  animation: fadeInUp 0.5s ease both;
  cursor: default;
}

.stat-card--clickable {
  cursor: pointer;
}

/* Gradient background per color */
.stat-card--primary::before {
  background: linear-gradient(135deg, rgba(124, 58, 237, 0.08), transparent 70%);
}
.stat-card--success::before {
  background: linear-gradient(135deg, rgba(16, 185, 129, 0.08), transparent 70%);
}
.stat-card--accent::before {
  background: linear-gradient(135deg, rgba(245, 158, 11, 0.08), transparent 70%);
}
.stat-card--danger::before {
  background: linear-gradient(135deg, rgba(239, 68, 68, 0.08), transparent 70%);
}

.stat-card::before {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: 12px;
  z-index: 0;
}

.stat-card__glow {
  position: absolute;
  top: -40%;
  right: -20%;
  width: 140px;
  height: 140px;
  border-radius: 50%;
  opacity: 0.06;
  z-index: 0;
  transition: opacity var(--lumina-transition-base);
}

.stat-card--primary .stat-card__glow { background: var(--lumina-primary); }
.stat-card--success .stat-card__glow { background: var(--lumina-success); }
.stat-card--accent .stat-card__glow { background: var(--lumina-accent); }
.stat-card--danger .stat-card__glow { background: var(--lumina-danger); }

.stat-card:hover .stat-card__glow {
  opacity: 0.14;
}

.stat-card:hover {
  transform: translateY(-4px);
  border-color: var(--lumina-primary);
  box-shadow: var(--lumina-shadow-md), 0 0 28px var(--lumina-primary-glow);
}

/* Icon circle */
.stat-card__icon-circle {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  z-index: 1;
  flex-shrink: 0;
}

.stat-card--primary .stat-card__icon-circle {
  background: linear-gradient(135deg, rgba(124, 58, 237, 0.18), rgba(124, 58, 237, 0.06));
  color: var(--lumina-primary-light);
  box-shadow: 0 0 18px rgba(124, 58, 237, 0.2);
}
.stat-card--success .stat-card__icon-circle {
  background: linear-gradient(135deg, rgba(16, 185, 129, 0.18), rgba(16, 185, 129, 0.06));
  color: var(--lumina-success-light);
  box-shadow: 0 0 18px rgba(16, 185, 129, 0.2);
}
.stat-card--accent .stat-card__icon-circle {
  background: linear-gradient(135deg, rgba(245, 158, 11, 0.18), rgba(245, 158, 11, 0.06));
  color: var(--lumina-accent-light);
  box-shadow: 0 0 18px rgba(245, 158, 11, 0.2);
}
.stat-card--danger .stat-card__icon-circle {
  background: linear-gradient(135deg, rgba(239, 68, 68, 0.18), rgba(239, 68, 68, 0.06));
  color: var(--lumina-danger-light);
  box-shadow: 0 0 18px rgba(239, 68, 68, 0.2);
}

/* Body (label + value) */
.stat-card__body {
  display: flex;
  flex-direction: column;
  gap: 4px;
  position: relative;
  z-index: 1;
}

.stat-card__label {
  font-size: 13px;
  font-weight: 500;
  color: var(--lumina-text-muted);
  letter-spacing: 0.02em;
}

.stat-card__value {
  font-family: var(--lumina-font-display);
  font-size: 32px;
  font-weight: 700;
  color: var(--lumina-text-primary);
  line-height: 1.1;
  letter-spacing: -0.02em;
}

/* ---- Content Grid ---------------------------------------------------- */
.content-grid {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 24px;
  animation: fadeInUp 0.5s ease 0.3s both;
}

/* ---- Shared Content Card --------------------------------------------- */
.content-card {
  background: var(--lumina-bg-card);
  border: 1px solid var(--lumina-border);
  border-radius: 12px;
  padding: 24px;
  overflow: hidden;
}

.content-card__header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
}

.content-card__title {
  font-family: var(--lumina-font-display);
  font-size: 16px;
  font-weight: 600;
  color: var(--lumina-text-primary);
  margin: 0;
  white-space: nowrap;
}

.content-card__title-line {
  flex: 1;
  height: 1px;
  background: linear-gradient(90deg, var(--lumina-border), transparent);
}

/* ---- Lumina Table ---------------------------------------------------- */
.lumina-table {
  --el-table-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
  --el-table-header-bg-color: rgba(124, 58, 237, 0.06);
  --el-table-row-hover-bg-color: rgba(124, 58, 237, 0.08);
  --el-table-border-color: var(--lumina-border);
  --el-table-text-color: var(--lumina-text-secondary);
  --el-table-header-text-color: var(--lumina-text-muted);
}

.lumina-table :deep(.lumina-table__header) {
  background: rgba(124, 58, 237, 0.06) !important;
}

.lumina-table :deep(.el-table__header-wrapper th) {
  font-family: var(--lumina-font-display);
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--lumina-text-muted);
  border-bottom: 1px solid var(--lumina-border) !important;
  padding: 12px 0;
}

.lumina-table :deep(.el-table__body-wrapper td) {
  font-size: 13px;
  color: var(--lumina-text-secondary);
  border-bottom: 1px solid rgba(51, 65, 85, 0.5) !important;
  padding: 10px 0;
}

.lumina-table :deep(.el-table__body tr:hover > td) {
  background: rgba(124, 58, 237, 0.06) !important;
}

.table-uuid {
  font-family: 'SF Mono', 'Fira Code', monospace;
  font-size: 12px;
  color: var(--lumina-text-muted);
}

/* Custom Status Tags */
.status-tag {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 20px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.02em;
  line-height: 1.6;
}

.status-tag--success {
  background: rgba(16, 185, 129, 0.15);
  color: var(--lumina-success);
}

.status-tag--info {
  background: rgba(59, 130, 246, 0.15);
  color: var(--lumina-info);
}

.status-tag--warning {
  background: rgba(245, 158, 11, 0.15);
  color: var(--lumina-accent);
}

.status-tag--danger {
  background: rgba(239, 68, 68, 0.15);
  color: var(--lumina-danger);
}

/* Empty State */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 32px 0;
  color: var(--lumina-text-muted);
  font-size: 13px;
}

/* ---- Action Tiles ---------------------------------------------------- */
.action-tiles {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.action-tile {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px;
  border-radius: 10px;
  background: linear-gradient(135deg, rgba(124, 58, 237, 0.04), transparent);
  border: 1px solid transparent;
  cursor: pointer;
  transition: background var(--lumina-transition-base),
              border-color var(--lumina-transition-base),
              transform var(--lumina-transition-base);
}

.action-tile:hover {
  background: linear-gradient(135deg, rgba(124, 58, 237, 0.1), rgba(124, 58, 237, 0.02));
  border-color: var(--lumina-primary);
  transform: translateY(-2px);
}

.action-tile__icon {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.action-tile__icon--primary {
  background: linear-gradient(135deg, rgba(124, 58, 237, 0.2), rgba(124, 58, 237, 0.08));
  color: var(--lumina-primary-light);
}

.action-tile__icon--success {
  background: linear-gradient(135deg, rgba(16, 185, 129, 0.2), rgba(16, 185, 129, 0.08));
  color: var(--lumina-success-light);
}

.action-tile__icon--accent {
  background: linear-gradient(135deg, rgba(245, 158, 11, 0.2), rgba(245, 158, 11, 0.08));
  color: var(--lumina-accent-light);
}

.action-tile__text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.action-tile__title {
  font-family: var(--lumina-font-display);
  font-size: 14px;
  font-weight: 600;
  color: var(--lumina-text-primary);
}

.action-tile__desc {
  font-size: 12px;
  color: var(--lumina-text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* ---- Loading skeleton overrides -------------------------------------- */
.lumina-table :deep(.el-loading-mask) {
  background-color: rgba(15, 23, 42, 0.7);
  backdrop-filter: blur(2px);
}

/* ---- Responsive ------------------------------------------------------ */
@media (max-width: 1200px) {
  .stat-cards-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .content-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .lumina-dashboard {
    padding: 16px;
    gap: 16px;
  }
  .stat-cards-grid {
    grid-template-columns: 1fr;
  }
  .stat-card {
    padding: 20px;
  }
  .stat-card__value {
    font-size: 26px;
  }
}
</style>
