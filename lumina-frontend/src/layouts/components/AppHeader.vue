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
      <el-popover placement="bottom-end" :width="320" trigger="click">
        <template #reference>
          <button class="icon-btn" :title="t('header.notifications')">
            <el-badge :value="unreadCount" :hidden="!unreadCount" :max="99">
              <el-icon :size="18"><Bell /></el-icon>
            </el-badge>
          </button>
        </template>
        <div class="notification-panel">
          <p class="notif-empty" v-if="!notifications?.length">{{ t('header.noNotifications') }}</p>
          <!-- notification list would go here -->
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
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { useAppStore, useUserStore } from '@/stores'
import { useRouter } from 'vue-router'
import { localizeTitle } from '@/utils'
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

const unreadCount = computed(() => 0) // placeholder
const notifications = computed(() => []) // placeholder

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
    ElMessage.info(t('header.profile'))
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
  padding: var(--lumina-spacing-sm) 0;
}

.notif-empty {
  text-align: center;
  color: var(--lumina-text-muted);
  font-family: var(--lumina-font-body);
  font-size: var(--lumina-font-size-sm);
  padding: var(--lumina-spacing-lg);
  margin: 0;
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
