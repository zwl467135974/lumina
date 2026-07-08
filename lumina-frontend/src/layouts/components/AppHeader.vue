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

const isDark = computed(() => true) // always dark in Luminous theme
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
  // Theme is always dark for Luminous; toggle could switch accent
  console.warn('Theme locked to Luminous dark')
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
   AppHeader �� Luminous Dark Theme
   Design: glass backdrop, gradient edge, subtle animations
   ============================================================ */

.app-header {
  position: fixed;
  top: 0;
  right: 0;
  left: 0;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  z-index: 1000;

  /* Glass backdrop */
  background: linear-gradient(
    180deg,
    rgba(15, 23, 42, 0.98) 0%,
    rgba(15, 23, 42, 0.95) 100%
  );
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);

  /* Gradient bottom edge */
  border-bottom: 1px solid transparent;
  background-clip: padding-box;
}

/* Gradient border line at bottom */
.app-header::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 1px;
  background: linear-gradient(
    90deg,
    transparent 0%,
    rgba(124, 58, 237, 0.15) 20%,
    rgba(124, 58, 237, 0.25) 50%,
    rgba(124, 58, 237, 0.15) 80%,
    transparent 100%
  );
  pointer-events: none;
}

/* ---------- Left Section ---------- */
.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
}

/* Collapse button */
.collapse-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: none;
  border-radius: var(--lumina-radius-sm, 6px);
  background: transparent;
  color: var(--lumina-text-secondary, #94a3b8);
  cursor: pointer;
  transition:
    color var(--lumina-transition-fast, 150ms cubic-bezier(0.4, 0, 0.2, 1)),
    background var(--lumina-transition-fast, 150ms cubic-bezier(0.4, 0, 0.2, 1));
}

.collapse-btn:hover {
  color: var(--lumina-primary-light, #a78bfa);
  background: rgba(124, 58, 237, 0.1);
}

/* ---------- Breadcrumb ---------- */
.header-breadcrumb {
  margin-left: 4px;
}

.header-breadcrumb :deep(.el-breadcrumb__item) {
  font-family: var(--lumina-font-body, 'IBM Plex Sans', -apple-system, sans-serif);
  font-size: 13px;
}

.header-breadcrumb :deep(.el-breadcrumb__inner) {
  color: var(--lumina-text-muted, #64748b);
  font-weight: 400;
  transition: color var(--lumina-transition-fast, 150ms cubic-bezier(0.4, 0, 0.2, 1));
}

.header-breadcrumb :deep(.el-breadcrumb__inner:hover) {
  color: var(--lumina-primary-light, #a78bfa);
}

.header-breadcrumb :deep(.el-breadcrumb__item:last-child .el-breadcrumb__inner) {
  color: var(--lumina-primary-light, #a78bfa);
  font-weight: 600;
}

.header-breadcrumb :deep(.el-breadcrumb__separator) {
  color: var(--lumina-border-light, #475569);
  margin: 0 6px;
}

/* ---------- Right Section ---------- */
.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* ---------- Language Switch ---------- */
.lang-switch {
  display: flex;
  align-items: center;
  background: var(--lumina-bg-elevated, #1e293b);
  border-radius: 20px;
  padding: 2px;
  border: 1px solid var(--lumina-border, #334155);
}

.lang-btn {
  padding: 4px 10px;
  border: none;
  border-radius: 18px;
  background: transparent;
  color: var(--lumina-text-muted, #64748b);
  font-family: var(--lumina-font-body, 'IBM Plex Sans', -apple-system, sans-serif);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--lumina-transition-fast, 150ms cubic-bezier(0.4, 0, 0.2, 1));
  white-space: nowrap;
}

.lang-btn:hover {
  color: var(--lumina-text-secondary, #94a3b8);
}

.lang-btn.active {
  background: var(--lumina-primary, #7c3aed);
  color: var(--lumina-text-primary, #f1f5f9);
  box-shadow: 0 2px 8px rgba(124, 58, 237, 0.35);
}

/* ---------- Icon Buttons ---------- */
.icon-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: none;
  border-radius: var(--lumina-radius-sm, 6px);
  background: transparent;
  color: var(--lumina-text-secondary, #94a3b8);
  cursor: pointer;
  position: relative;
  transition:
    color var(--lumina-transition-fast, 150ms cubic-bezier(0.4, 0, 0.2, 1)),
    background var(--lumina-transition-fast, 150ms cubic-bezier(0.4, 0, 0.2, 1));
}

.icon-btn:hover {
  color: var(--lumina-primary-light, #a78bfa);
  background: rgba(124, 58, 237, 0.1);
}

/* Theme toggle with rotation animation */
.theme-toggle {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: none;
  border-radius: var(--lumina-radius-sm, 6px);
  background: transparent;
  color: var(--lumina-accent, #f59e0b);
  cursor: pointer;
  transition:
    color var(--lumina-transition-fast, 150ms cubic-bezier(0.4, 0, 0.2, 1)),
    background var(--lumina-transition-fast, 150ms cubic-bezier(0.4, 0, 0.2, 1));
}

.theme-toggle:hover {
  background: rgba(245, 158, 11, 0.1);
}

.theme-toggle .theme-icon {
  transition: transform var(--lumina-transition-slow, 400ms cubic-bezier(0.4, 0, 0.2, 1));
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
  margin-left: 4px;
}

.user-avatar {
  border: 2px solid var(--lumina-border, #334155);
  transition: border-color var(--lumina-transition-fast, 150ms cubic-bezier(0.4, 0, 0.2, 1));
  font-family: var(--lumina-font-body, 'IBM Plex Sans', -apple-system, sans-serif);
  font-weight: 600;
  background: linear-gradient(135deg, var(--lumina-primary-dark, #5b21b6), var(--lumina-primary, #7c3aed));
  color: var(--lumina-text-primary, #f1f5f9);
}

.user-avatar-wrapper:hover .user-avatar {
  border-color: var(--lumina-primary-light, #a78bfa);
}

.avatar-glow {
  position: absolute;
  inset: -4px;
  border-radius: 50%;
  background: radial-gradient(
    circle at center,
    rgba(124, 58, 237, 0.25) 0%,
    rgba(124, 58, 237, 0.1) 50%,
    transparent 70%
  );
  opacity: 0;
  transition: opacity var(--lumina-transition-base, 250ms cubic-bezier(0.4, 0, 0.2, 1));
  pointer-events: none;
}

.user-avatar-wrapper:hover .avatar-glow {
  opacity: 1;
}

/* ---------- Notification Panel ---------- */
.notification-panel {
  padding: 8px 0;
}

.notif-empty {
  text-align: center;
  color: var(--lumina-text-muted, #64748b);
  font-family: var(--lumina-font-body, 'IBM Plex Sans', -apple-system, sans-serif);
  font-size: 13px;
  padding: 20px;
  margin: 0;
}

/* ---------- Badge Override ---------- */
:deep(.el-badge__content) {
  background: var(--lumina-accent, #f59e0b);
  border: 2px solid var(--lumina-bg-elevated, #1e293b);
}

/* ---------- Dropdown Override ---------- */
:deep(.el-dropdown-menu__item) {
  font-family: var(--lumina-font-body, 'IBM Plex Sans', -apple-system, sans-serif);
  font-size: 13px;
  color: var(--lumina-text-secondary, #94a3b8);
  display: flex;
  align-items: center;
  gap: 8px;
}

:deep(.el-dropdown-menu__item:hover) {
  background: rgba(124, 58, 237, 0.1);
  color: var(--lumina-text-primary, #f1f5f9);
}

:deep(.el-dropdown-menu__item .el-icon) {
  font-size: 15px;
}
</style>
