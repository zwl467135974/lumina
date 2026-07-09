<template>
  <aside class="app-sidebar" :class="{ collapsed: appStore.sidebarCollapsed }">
    <div class="sidebar-logo">
      <h2 v-if="!appStore.sidebarCollapsed">Lumina</h2>
      <h2 v-else>L</h2>
    </div>
    <el-menu
      :default-active="activeMenu"
      :collapse="appStore.sidebarCollapsed"
      :unique-opened="true"
      router
      class="sidebar-menu"
    >
      <template v-for="menu in menuList" :key="menu.path">
        <el-sub-menu v-if="menu.children?.length" :index="menu.path">
          <template #title>
            <el-icon v-if="menu.icon"><component :is="menu.icon" /></el-icon>
            <span>{{ localizeTitle(menu.title) }}</span>
          </template>
          <el-menu-item
            v-for="child in menu.children"
            :key="child.path"
            :index="resolvePath(menu.path, child.path)"
          >
            <el-icon v-if="child.icon"><component :is="child.icon" /></el-icon>
            <template #title>{{ localizeTitle(child.title) }}</template>
          </el-menu-item>
        </el-sub-menu>
        <el-menu-item v-else :index="menu.path">
          <el-icon v-if="menu.icon"><component :is="menu.icon" /></el-icon>
          <template #title>{{ localizeTitle(menu.title) }}</template>
        </el-menu-item>
      </template>
    </el-menu>
    <div class="sidebar-glow" />
  </aside>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAppStore, useUserStore } from '@/stores'
import { useI18n } from 'vue-i18n'
import { localizeTitle as translateTitle } from '@/utils'

const appStore = useAppStore()
const route = useRoute()
const userStore = useUserStore()
const { t } = useI18n()

const activeMenu = computed(() => route.path)

interface MenuVO {
  name?: string
  path: string
  title?: string
  icon?: string
  redirect?: string
  permission?: string
  keepAlive?: boolean
  children?: MenuVO[]
}

const localizeTitle = (title?: string): string => {
  return translateTitle(title, t)
}

const resolvePath = (parent?: string, child?: string) => {
  if (!child) return parent || '/'
  if (child.startsWith('/')) return child
  const base = parent?.replace(/\/$/, '') || ''
  return `${base}/${child}`
}

const menuList = computed<MenuVO[]>(() => {
  const dashboard: MenuVO = {
    name: 'Dashboard',
    path: '/',
    title: t('menu.dashboard'),
    icon: 'Odometer'
  }
  return [dashboard, ...(userStore.menus as MenuVO[] || [])]
})
</script>

<style scoped>
/* ============================================================
   AppSidebar — Luminous Dark Theme (Flex Layout)
   方案 A: flex-shrink:0, 不再 fixed
   ============================================================ */

.app-sidebar {
  flex-shrink: 0;
  width: 220px;
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--lumina-bg-elevated);
  border-right: 1px solid var(--lumina-border);
  overflow: hidden;
  box-shadow: var(--lumina-shadow-sm);
  transition: width var(--lumina-transition-base);
}

.app-sidebar.collapsed {
  width: 64px;
}

/* ---------- Logo ---------- */
.sidebar-logo {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 56px;
  background: rgba(var(--lumina-primary-rgb), 0.04);
  border-bottom: 1px solid var(--lumina-border);
}

.sidebar-logo h2 {
  margin: 0;
  font-family: var(--lumina-font-display);
  font-size: var(--lumina-font-size-xl);
  font-weight: var(--lumina-font-weight-bold);
  color: transparent;
  background: linear-gradient(135deg, var(--lumina-primary), var(--lumina-accent));
  background-clip: text;
  -webkit-background-clip: text;
  letter-spacing: -0.02em;
}

/* ---------- Menu (可滚动) ---------- */
.sidebar-menu {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  border-right: none !important;
  background: transparent !important;
  padding: var(--lumina-spacing-sm);
  scrollbar-width: thin;
  scrollbar-color: var(--lumina-border) transparent;
}

.sidebar-menu::-webkit-scrollbar {
  width: 4px;
}

.sidebar-menu::-webkit-scrollbar-thumb {
  background: var(--lumina-border);
  border-radius: 4px;
}

/* ---------- Menu Items ---------- */
.sidebar-menu :deep(.el-menu-item),
.sidebar-menu :deep(.el-sub-menu__title) {
  height: 44px;
  line-height: 44px;
  margin: 2px 0;
  border-radius: var(--lumina-radius-sm);
  color: var(--lumina-text-secondary);
  background: transparent !important;
  font-family: var(--lumina-font-body);
  font-size: var(--lumina-font-size-base);
  font-weight: var(--lumina-font-weight-medium);
  transition:
    color var(--lumina-transition-fast),
    background var(--lumina-transition-fast);
}

.sidebar-menu :deep(.el-menu-item:hover),
.sidebar-menu :deep(.el-sub-menu__title:hover) {
  color: var(--lumina-text-primary);
  background: rgba(var(--lumina-primary-rgb), 0.1) !important;
}

/* ---------- Active State ---------- */
.sidebar-menu :deep(.el-menu-item.is-active) {
  position: relative;
  color: var(--lumina-primary-light) !important;
  background: linear-gradient(90deg, rgba(var(--lumina-primary-rgb), 0.18), rgba(var(--lumina-primary-rgb), 0.04)) !important;
  font-weight: var(--lumina-font-weight-semibold);
}

.sidebar-menu :deep(.el-menu-item.is-active::before) {
  content: '';
  position: absolute;
  left: 0;
  top: 25%;
  bottom: 25%;
  width: 3px;
  border-radius: 0 3px 3px 0;
  background: linear-gradient(180deg, var(--lumina-primary), var(--lumina-accent));
}

.sidebar-menu :deep(.el-menu-item.is-active .el-icon) {
  color: var(--lumina-accent);
}

/* ---------- Submenu ---------- */
.sidebar-menu :deep(.el-sub-menu.is-opened > .el-sub-menu__title) {
  color: var(--lumina-primary-light) !important;
}

.sidebar-menu :deep(.el-menu--inline) {
  background: var(--lumina-bg-hover) !important;
  border-radius: var(--lumina-radius-sm);
  margin: 2px 0;
  padding: 4px 0;
}

.sidebar-menu :deep(.el-menu--inline .el-menu-item) {
  height: 38px;
  line-height: 38px;
  font-size: var(--lumina-font-size-sm);
  padding-left: 52px !important;
}

/* ---------- Icons ---------- */
.sidebar-menu :deep(.el-icon) {
  color: inherit;
  font-size: 16px;
  transition: color var(--lumina-transition-fast);
}

/* ---------- Collapsed State ---------- */
.app-sidebar.collapsed .sidebar-menu :deep(.el-menu--collapse) {
  width: 64px;
}

.app-sidebar.collapsed .sidebar-menu :deep(.el-menu-item),
.app-sidebar.collapsed .sidebar-menu :deep(.el-sub-menu__title) {
  justify-content: center;
  padding-left: 0 !important;
  padding-right: 0 !important;
  margin: 2px auto;
  width: 44px;
}

/* ---------- Bottom Glow ---------- */
.sidebar-glow {
  flex-shrink: 0;
  height: 2px;
  margin: var(--lumina-spacing-sm) var(--lumina-spacing-md) var(--lumina-spacing-md);
  border-radius: 1px;
  background: linear-gradient(
    90deg,
    transparent 0%,
    rgba(var(--lumina-primary-rgb), 0.4) 50%,
    transparent 100%
  );
  opacity: 0.6;
}

.app-sidebar.collapsed .sidebar-glow {
  margin: var(--lumina-spacing-sm) var(--lumina-spacing-xs) var(--lumina-spacing-sm);
}

/* ---------- Responsive: 移动端抽屉 ---------- */
@media (max-width: 768px) {
  .app-sidebar {
    position: fixed;
    top: 0;
    left: 0;
    z-index: var(--lumina-z-fixed);
    transform: translateX(0);
    transition:
      transform var(--lumina-transition-base),
      width var(--lumina-transition-base);
  }

  .app-sidebar.collapsed {
    width: 220px;
    transform: translateX(-100%);
  }
}
</style>
