<template>
  <header class="app-header">
    <!-- Left: Collapse Toggle + Breadcrumb -->
    <div class="header-left">
      <button class="collapse-btn" @click="appStore.toggleSidebar()" :title="collapseTitle">
        <el-icon :size="20">
          <Fold v-if="!appStore.sidebarCollapsed" />
          <Expand v-else />
        </el-icon>
      </button>

      <!-- Breadcrumb -->
      <el-breadcrumb v-if="breadcrumbs?.length" separator="/" class="header-breadcrumb">
        <el-breadcrumb-item
          v-for="item in breadcrumbs"
          :key="item.path"
          :to="item.path"
        >
          {{ localizeBreadcrumbTitle(item.meta?.title, item.name) }}
        </el-breadcrumb-item>
      </el-breadcrumb>
    </div>

    <!-- Right: Actions -->
    <div class="header-right">
      <!-- Language Switch -->
      <div class="lang-switch">
        <button
          v-for="lang in langOptions"
          :key="lang.code"
          :class="['lang-btn', { active: locale === lang.code }]"
          @click="switchLocale(lang.code)"
        >
          {{ lang.label }}
        </button>
      </div>

      <!-- Theme Toggle -->
      <button class="theme-toggle" @click="toggleTheme" :title="t('header.darkMode')">
        <el-icon :size="18" class="theme-icon">
          <Sunny v-if="isDark" />
          <Moon v-else />
        </el-icon>
      </button>

      <!-- Notifications -->
      <el-popover placement="bottom-end" :width="360" trigger="click" @show="onNotificationPanelOpen">
        <template #reference>
          <button class="icon-btn" :title="t('header.notifications')">
            <el-badge :value="notificationStore.unreadCount" :hidden="!notificationStore.unreadCount" :max="99">
              <el-icon :size="18"><Bell /></el-icon>
            </el-badge>
          </button>
        </template>
        <div class="notification-panel">
          <!-- Header -->
          <div class="notif-panel-header">
            <span class="notif-panel-title">{{ t('notification.title') }}</span>
            <el-button
              v-if="notificationStore.unreadCount > 0"
              link
              size="small"
              @click="notificationStore.readAll()"
            >
              {{ t('notification.markAllRead') }}
            </el-button>
          </div>
          <!-- List -->
          <div v-if="notificationStore.recentList.length" class="notif-list">
            <div
              v-for="item in notificationStore.recentList"
              :key="item.id"
              :class="['notif-item', { 'is-unread': item.isRead === 0 }]"
              @click="onNotificationClick(item)"
            >
              <div class="notif-item-header">
                <el-tag :type="severityTagType(item.severity)" size="small" effect="plain">
                  {{ t(`notification.category.${item.category}`) }}
                </el-tag>
                <span class="notif-item-time">{{ formatTime(item.createTime) }}</span>
              </div>
              <div class="notif-item-title">{{ item.title }}</div>
              <div class="notif-item-content">{{ item.content }}</div>
            </div>
          </div>
          <p class="notif-empty" v-else>{{ t('header.noNotifications') }}</p>
          <!-- View all link -->
          <div class="notif-panel-footer">
            <el-button link type="primary" size="small" @click="goToNotificationPage">
              {{ t('notification.viewAll') }}
            </el-button>
          </div>
        </div>
      </el-popover>

      <!-- User Dropdown -->
      <el-dropdown trigger="click" @command="handleUserCommand">
        <div class="user-avatar-wrapper">
          <el-avatar :size="34" :src="userStore.userInfo?.avatar" class="user-avatar">
            {{ userStore.userInfo?.nickname?.charAt(0)?.toUpperCase() || 'U' }}
          </el-avatar>
          <span class="avatar-glow" />
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="profile">
              <el-icon><User /></el-icon>{{ $t('header.profile') }}
            </el-dropdown-item>
            <el-dropdown-item divided command="logout">
              <el-icon><SwitchButton /></el-icon>{{ $t('header.logout') }}
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAppStore, useUserStore, useNotificationStore } from '@/stores'
import { useRouter } from 'vue-router'
import { localizeTitle } from '@/utils'
import type { NotificationVO } from '@/api/modules/notification'
import {
  Fold,
  Expand,
  Sunny,
  Moon,
  Bell,
  User,
  SwitchButton
} from '@element-plus/icons-vue'

const { locale, availableLocales, t } = useI18n()
const appStore = useAppStore()
const userStore = useUserStore()
const notificationStore = useNotificationStore()
const router = useRouter()

const langOptions = computed(() => availableLocales.map(code => ({
  code,
  label: code === 'zh-CN' ? '中文' : 'EN'
})))

defineProps<{
  breadcrumbs?: Array<{ path: string; name?: string; meta?: { title?: string } }>
}>()

const isDark = computed(() => appStore.theme === 'dark')
const collapseTitle = computed(() => t(appStore.sidebarCollapsed ? 'common.expand' : 'common.collapse'))

/** 通知面板打开时刷新列表 */
function onNotificationPanelOpen() {
  notificationStore.fetchList()
}

/** 跳转到通知中心页面 */
function goToNotificationPage() {
  router.push('/notification')
}

/** 点击通知项：标记已读 + 跳转关联页面 */
function onNotificationClick(item: NotificationVO) {
  if (item.isRead === 0) {
    notificationStore.readOne(item.id)
  }
  // 根据 refType 跳转（简单路由映射）
  if (item.refType === 'agent_task') {
    router.push('/agent/tasks')
  } else if (item.refType === 'workflow_instance') {
    router.push('/workflow')
  }
}

/** severity → el-tag type */
function severityTagType(severity: string): 'info' | 'warning' | 'danger' {
  if (severity === 'ERROR') return 'danger'
  if (severity === 'WARN') return 'warning'
  return 'info'
}

/** 格式化时间 */
function formatTime(time: string): string {
  if (!time) return ''
  const d = new Date(time)
  const now = new Date()
  const diff = now.getTime() - d.getTime()
  if (diff < 60000) return t('notification.justNow')
  if (diff < 3600000) return `${Math.floor(diff / 60000)} ${t('notification.minutesAgo')}`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)} ${t('notification.hoursAgo')}`
  return d.toLocaleDateString()
}

onMounted(() => {
  if (userStore.isLoggedIn) {
    notificationStore.init()
  }
})

onUnmounted(() => {
  notificationStore.disconnectSSE()
})

function switchLocale(code: string) {
  locale.value = code
  localStorage.setItem('lumina-lang', code)
}

function localizeBreadcrumbTitle(title?: string, name?: string): string {
  return localizeTitle(title, t) || name || ''
}

function toggleTheme() {
  appStore.toggleTheme()
}

function handleUserCommand(command: string) {
  if (command === 'profile') {
    router.push('/profile')
  } else if (command === 'logout') {
    userStore.logout().then(() => router.push('/login'))
  }
}
</script>

<style scoped>
/* ============================================================
   AppHeader — Luminous Dark Theme (Flex Layout)
   方案 A: sticky + flex-shrink:0, 替代 fixed
   ============================================================ */

.app-header {
  flex-shrink: 0;
  position: sticky;
  top: 0;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--lumina-spacing-lg);
  z-index: var(--lumina-z-sticky);

  /* Glass backdrop */
  background: var(--lumina-bg-base);
  border-bottom: 1px solid var(--lumina-border);
}

/* Subtle top border accent */
.app-header::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 1px;
  background: var(--lumina-border);
  pointer-events: none;
}

/* ---------- Left Section ---------- */
.header-left {
  display: flex;
  align-items: center;
  gap: var(--lumina-spacing-sm);
  flex: 1;
  min-width: 0;
}

/* Collapse button */
.collapse-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  flex-shrink: 0;
  border: none;
  border-radius: var(--lumina-radius-sm);
  background: transparent;
  color: var(--lumina-text-secondary);
  cursor: pointer;
  transition:
    color var(--lumina-transition-fast),
    background var(--lumina-transition-fast);
}

.collapse-btn:hover {
  color: var(--lumina-primary-light);
  background: rgba(var(--lumina-primary-rgb), 0.1);
}

/* ---------- Breadcrumb ---------- */
.header-breadcrumb {
  margin-left: var(--lumina-spacing-xs);
  min-width: 0;
  overflow: hidden;
}

.header-breadcrumb :deep(.el-breadcrumb__item) {
  font-family: var(--lumina-font-body);
  font-size: var(--lumina-font-size-sm);
}

.header-breadcrumb :deep(.el-breadcrumb__inner) {
  color: var(--lumina-text-muted);
  font-weight: var(--lumina-font-weight-normal);
  transition: color var(--lumina-transition-fast);
}

.header-breadcrumb :deep(.el-breadcrumb__inner:hover) {
  color: var(--lumina-primary-light);
}

.header-breadcrumb :deep(.el-breadcrumb__item:last-child .el-breadcrumb__inner) {
  color: var(--lumina-primary-light);
  font-weight: var(--lumina-font-weight-semibold);
}

.header-breadcrumb :deep(.el-breadcrumb__separator) {
  color: var(--lumina-border-light);
  margin: 0 var(--lumina-spacing-xs);
}

/* ---------- Right Section ---------- */
.header-right {
  display: flex;
  align-items: center;
  gap: var(--lumina-spacing-sm);
  flex-shrink: 0;
}

/* ---------- Language Switch ---------- */
.lang-switch {
  display: flex;
  align-items: center;
  background: var(--lumina-bg-elevated);
  border-radius: var(--lumina-radius-full);
  padding: 2px;
  border: 1px solid var(--lumina-border);
}

.lang-btn {
  padding: 4px 10px;
  border: none;
  border-radius: 18px;
  background: transparent;
  color: var(--lumina-text-muted);
  font-family: var(--lumina-font-body);
  font-size: var(--lumina-font-size-xs);
  font-weight: var(--lumina-font-weight-medium);
  cursor: pointer;
  white-space: nowrap;
  transition:
    color var(--lumina-transition-fast),
    background var(--lumina-transition-fast),
    box-shadow var(--lumina-transition-fast);
}

.lang-btn:hover {
  color: var(--lumina-text-secondary);
}

.lang-btn.active {
  background: var(--lumina-primary);
  color: var(--lumina-text-primary);
  box-shadow: 0 2px 8px var(--lumina-primary-glow);
}

/* ---------- Icon Buttons ---------- */
.icon-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: none;
  border-radius: var(--lumina-radius-sm);
  background: transparent;
  color: var(--lumina-text-secondary);
  cursor: pointer;
  position: relative;
  transition:
    color var(--lumina-transition-fast),
    background var(--lumina-transition-fast);
}

.icon-btn:hover {
  color: var(--lumina-primary-light);
  background: rgba(var(--lumina-primary-rgb), 0.1);
}

/* Theme toggle */
.theme-toggle {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: none;
  border-radius: var(--lumina-radius-sm);
  background: transparent;
  color: var(--lumina-accent);
  cursor: pointer;
  transition:
    color var(--lumina-transition-fast),
    background var(--lumina-transition-fast);
}

.theme-toggle:hover {
  background: rgba(var(--lumina-accent-rgb), 0.1);
}

.theme-toggle .theme-icon {
  transition: transform var(--lumina-transition-slow);
  display: inline-flex;
}

.theme-toggle:hover .theme-icon {
  transform: rotate(180deg);
}

/* ---------- User Avatar ---------- */
.user-avatar-wrapper {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  margin-left: var(--lumina-spacing-xs);
}

.user-avatar {
  border: 2px solid var(--lumina-border);
  transition: border-color var(--lumina-transition-fast);
  font-family: var(--lumina-font-body);
  font-weight: var(--lumina-font-weight-semibold);
  background: linear-gradient(135deg, var(--lumina-primary-dark), var(--lumina-primary));
  color: var(--lumina-text-primary);
}

.user-avatar-wrapper:hover .user-avatar {
  border-color: var(--lumina-primary-light);
}

.avatar-glow {
  position: absolute;
  inset: -4px;
  border-radius: 50%;
  background: radial-gradient(
    circle at center,
    var(--lumina-primary-glow) 0%,
    rgba(var(--lumina-primary-rgb), 0.1) 50%,
    transparent 70%
  );
  opacity: 0;
  transition: opacity var(--lumina-transition-base);
  pointer-events: none;
}

.user-avatar-wrapper:hover .avatar-glow {
  opacity: 1;
}

/* ---------- Notification Panel ---------- */
.notification-panel {
  padding: 0;
}

.notif-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--lumina-spacing-sm) var(--lumina-spacing-md);
  border-bottom: 1px solid var(--lumina-border);
}

.notif-panel-title {
  font-family: var(--lumina-font-display);
  font-size: var(--lumina-font-size-base);
  font-weight: var(--lumina-font-weight-semibold);
  color: var(--lumina-text-primary);
}

.notif-list {
  max-height: 400px;
  overflow-y: auto;
}

.notif-item {
  padding: var(--lumina-spacing-sm) var(--lumina-spacing-md);
  border-bottom: 1px solid var(--lumina-border);
  cursor: pointer;
  transition: background var(--lumina-transition-fast);
}

.notif-item:hover {
  background: rgba(var(--lumina-primary-rgb), 0.05);
}

.notif-item.is-unread {
  background: rgba(var(--lumina-primary-rgb), 0.03);
}

.notif-item-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}

.notif-item-time {
  font-family: var(--lumina-font-body);
  font-size: var(--lumina-font-size-xs);
  color: var(--lumina-text-muted);
}

.notif-item-title {
  font-family: var(--lumina-font-body);
  font-size: var(--lumina-font-size-sm);
  font-weight: var(--lumina-font-weight-medium);
  color: var(--lumina-text-primary);
  margin-bottom: 2px;
}

.notif-item-content {
  font-family: var(--lumina-font-body);
  font-size: var(--lumina-font-size-xs);
  color: var(--lumina-text-secondary);
  line-height: 1.4;
}

.notif-empty {
  text-align: center;
  color: var(--lumina-text-muted);
  font-family: var(--lumina-font-body);
  font-size: var(--lumina-font-size-sm);
  padding: var(--lumina-spacing-lg);
  margin: 0;
}

.notif-panel-footer {
  text-align: center;
  border-top: 1px solid var(--lumina-border);
  padding-top: var(--lumina-spacing-sm);
  margin-top: var(--lumina-spacing-sm);
}

/* ---------- Badge Override ---------- */
:deep(.el-badge__content) {
  background: var(--lumina-accent);
  border: 2px solid var(--lumina-bg-elevated);
}

/* ---------- Dropdown Override ---------- */
:deep(.el-dropdown-menu__item) {
  font-family: var(--lumina-font-body);
  font-size: var(--lumina-font-size-sm);
  color: var(--lumina-text-secondary);
  display: flex;
  align-items: center;
  gap: var(--lumina-spacing-sm);
}

:deep(.el-dropdown-menu__item:hover) {
  background: rgba(var(--lumina-primary-rgb), 0.1);
  color: var(--lumina-text-primary);
}

:deep(.el-dropdown-menu__item .el-icon) {
  font-size: 15px;
}

/* ---------- Responsive ---------- */
@media (max-width: 768px) {
  .app-header {
    padding: 0 var(--lumina-spacing-md);
  }

  .header-breadcrumb {
    display: none;
  }

  .lang-switch {
    display: none;
  }
}
</style>
