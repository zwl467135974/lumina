<template>
  <div class="app-sidebar" :class="{ collapsed: appStore.sidebarCollapsed }">
    <!-- Logo -->
    <div class="sidebar-logo">
      <div class="logo-content">
        <span class="logo-icon">✦</span>
        <h2 v-if="!appStore.sidebarCollapsed" class="logo-text">Lumina</h2>
        <h2 v-else class="logo-text logo-text--collapsed">L</h2>
      </div>
      <div class="logo-divider" />
    </div>

    <!-- Navigation Menu -->
    <el-menu
      :default-active="activeMenu"
      :collapse="appStore.sidebarCollapsed"
      :unique-opened="true"
      router
      class="sidebar-menu"
    >
      <template v-for="menu in menuRoutes" :key="menu.path">
        <!-- Sub-menu with children -->
        <el-sub-menu v-if="menu.children?.length" :index="menu.path">
          <template #title>
            <el-icon v-if="menu.meta?.icon"><component :is="menu.meta.icon" /></el-icon>
            <span>{{ menu.meta?.title }}</span>
          </template>
          <el-menu-item
            v-for="child in menu.children"
            :key="child.path"
            :index="resolvePath(menu.path, child.path)"
          >
            <el-icon v-if="child.meta?.icon"><component :is="child.meta.icon" /></el-icon>
            <span>{{ child.meta?.title }}</span>
          </el-menu-item>
        </el-sub-menu>

        <!-- Single-level menu item -->
        <el-menu-item v-else :index="menu.path">
          <el-icon v-if="menu.meta?.icon"><component :is="menu.meta.icon" /></el-icon>
          <span>{{ menu.meta?.title }}</span>
        </el-menu-item>
      </template>
    </el-menu>

    <!-- Bottom decorative glow bar -->
    <div class="sidebar-glow" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAppStore } from '@/stores/app'
import type { RouteRecordRaw } from 'vue-router'

const appStore = useAppStore()
const route = useRoute()

const props = defineProps<{
  menuRoutes: RouteRecordRaw[]
}>()

const activeMenu = computed(() => {
  const { path } = route
  return path
})

function resolvePath(parentPath: string, childPath: string): string {
  if (childPath.startsWith('/')) return childPath
  return `${parentPath}/${childPath}`.replace(/\/+/g, '/')
}
</script>

<style scoped>
/* ============================================================
   AppSidebar — Luminous Dark Theme
   Design: deep purple gradient, accent indicators, glass feel
   ============================================================ */

.app-sidebar {
  position: fixed;
  top: 0;
  left: 0;
  bottom: 0;
  width: 220px;
  display: flex;
  flex-direction: column;
  background: linear-gradient(180deg, #0f172a 0%, #1a1040 60%, #0f172a 100%);
  overflow: hidden;
  z-index: 1001;
  transition: width var(--lumina-transition-base, 250ms cubic-bezier(0.4, 0, 0.2, 1));
  box-shadow: 2px 0 24px rgba(0, 0, 0, 0.4);
}

/* Collapsed state */
.app-sidebar.collapsed {
  width: 64px;
}

/* ---------- Logo Area ---------- */
.sidebar-logo {
  flex-shrink: 0;
  padding: 16px 20px 0;
  background: linear-gradient(180deg, rgba(124, 58, 237, 0.08) 0%, transparent 100%);
  position: relative;
}

.app-sidebar.collapsed .sidebar-logo {
  padding: 16px 12px 0;
}

.logo-content {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 4px 0 12px;
}

.app-sidebar.collapsed .logo-content {
  justify-content: center;
}

.logo-icon {
  font-size: 22px;
  color: var(--lumina-accent, #f59e0b);
  text-shadow: 0 0 12px rgba(245, 158, 11, 0.5);
  line-height: 1;
  flex-shrink: 0;
}

.logo-text {
  font-family: var(--lumina-font-display, 'Outfit', -apple-system, sans-serif);
  font-size: 20px;
  font-weight: 700;
  color: var(--lumina-text-primary, #f1f5f9);
  letter-spacing: -0.02em;
  margin: 0;
  white-space: nowrap;
  overflow: hidden;
  user-select: none;
  background: linear-gradient(135deg, #f1f5f9 0%, #a78bfa 100%);
  background-clip: text;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.logo-text--collapsed {
  font-size: 18px;
}

/* Gradient divider below logo */
.logo-divider {
  height: 2px;
  width: 100%;
  background: linear-gradient(
    90deg,
    transparent 0%,
    rgba(124, 58, 237, 0.4) 20%,
    rgba(245, 158, 11, 0.3) 50%,
    rgba(124, 58, 237, 0.4) 80%,
    transparent 100%
  );
  border-radius: 1px;
}

.app-sidebar.collapsed .logo-divider {
  background: linear-gradient(
    90deg,
    transparent 0%,
    rgba(124, 58, 237, 0.3) 50%,
    transparent 100%
  );
}

/* ---------- el-menu Overrides ---------- */
.sidebar-menu {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  border-right: none !important;
  background: transparent !important;
  padding-top: 8px;
  scrollbar-width: thin;
  scrollbar-color: var(--lumina-border, #334155) transparent;
}

/* Override Element Plus default menu styles */
.sidebar-menu :deep(.el-menu) {
  background: transparent !important;
  border-right: none !important;
}

/* Menu items */
.sidebar-menu :deep(.el-menu-item) {
  position: relative;
  height: 44px;
  line-height: 44px;
  margin: 2px 8px;
  border-radius: var(--lumina-radius-md, 10px);
  color: var(--lumina-text-secondary, #94a3b8) !important;
  background: transparent !important;
  font-family: var(--lumina-font-body, 'IBM Plex Sans', -apple-system, sans-serif);
  font-size: 14px;
  font-weight: 500;
  transition:
    color var(--lumina-transition-fast, 150ms cubic-bezier(0.4, 0, 0.2, 1)),
    background var(--lumina-transition-fast, 150ms cubic-bezier(0.4, 0, 0.2, 1));
}

/* Left gradient indicator bar */
.sidebar-menu :deep(.el-menu-item)::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 0%;
  border-radius: 0 2px 2px 0;
  background: linear-gradient(180deg, var(--lumina-primary, #7c3aed), var(--lumina-accent, #f59e0b));
  opacity: 0;
  transition:
    height var(--lumina-transition-base, 250ms cubic-bezier(0.4, 0, 0.2, 1)),
    opacity var(--lumina-transition-fast, 150ms cubic-bezier(0.4, 0, 0.2, 1));
}

/* Hover state */
.sidebar-menu :deep(.el-menu-item:hover) {
  color: var(--lumina-text-primary, #f1f5f9) !important;
  background: rgba(124, 58, 237, 0.08) !important;
}

.sidebar-menu :deep(.el-menu-item:hover)::before {
  height: 50%;
  opacity: 0.7;
}

/* Active state */
.sidebar-menu :deep(.el-menu-item.is-active) {
  color: var(--lumina-accent, #f59e0b) !important;
  background: linear-gradient(90deg, rgba(124, 58, 237, 0.12), rgba(124, 58, 237, 0.02)) !important;
  font-weight: 600;
  text-shadow: 0 0 8px rgba(245, 158, 11, 0.15);
}

.sidebar-menu :deep(.el-menu-item.is-active)::before {
  height: 60%;
  opacity: 1;
}

/* Sub-menu title */
.sidebar-menu :deep(.el-sub-menu__title) {
  position: relative;
  height: 44px;
  line-height: 44px;
  margin: 2px 8px;
  border-radius: var(--lumina-radius-md, 10px);
  color: var(--lumina-text-secondary, #94a3b8) !important;
  background: transparent !important;
  font-family: var(--lumina-font-body, 'IBM Plex Sans', -apple-system, sans-serif);
  font-size: 14px;
  font-weight: 500;
  transition:
    color var(--lumina-transition-fast, 150ms cubic-bezier(0.4, 0, 0.2, 1)),
    background var(--lumina-transition-fast, 150ms cubic-bezier(0.4, 0, 0.2, 1));
}

.sidebar-menu :deep(.el-sub-menu__title)::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 0%;
  border-radius: 0 2px 2px 0;
  background: linear-gradient(180deg, var(--lumina-primary, #7c3aed), var(--lumina-accent, #f59e0b));
  opacity: 0;
  transition:
    height var(--lumina-transition-base, 250ms cubic-bezier(0.4, 0, 0.2, 1)),
    opacity var(--lumina-transition-fast, 150ms cubic-bezier(0.4, 0, 0.2, 1));
}

.sidebar-menu :deep(.el-sub-menu__title:hover) {
  color: var(--lumina-text-primary, #f1f5f9) !important;
  background: rgba(124, 58, 237, 0.08) !important;
}

.sidebar-menu :deep(.el-sub-menu__title:hover)::before {
  height: 40%;
  opacity: 0.5;
}

/* Sub-menu opened */
.sidebar-menu :deep(.el-sub-menu.is-opened > .el-sub-menu__title) {
  color: var(--lumina-primary-light, #a78bfa) !important;
}

/* Sub-menu children container */
.sidebar-menu :deep(.el-menu--inline) {
  background: rgba(15, 23, 42, 0.6) !important;
  margin: 2px 8px;
  padding: 2px 0;
  border-radius: var(--lumina-radius-sm, 6px);
}

/* Sub-menu children items */
.sidebar-menu :deep(.el-menu--inline .el-menu-item) {
  padding-left: 56px !important;
  height: 38px;
  line-height: 38px;
  font-size: 13px;
  margin: 1px 4px;
}

/* Icon styling */
.sidebar-menu :deep(.el-icon) {
  color: inherit;
  font-size: 16px;
  transition: color var(--lumina-transition-fast, 150ms cubic-bezier(0.4, 0, 0.2, 1));
}

.sidebar-menu :deep(.el-menu-item.is-active .el-icon) {
  color: var(--lumina-accent, #f59e0b);
}

/* Collapse transition for menu text */
.sidebar-menu :deep(.el-menu--collapse) {
  width: 64px;
}

.sidebar-menu :deep(.el-menu--collapse .el-menu-item),
.sidebar-menu :deep(.el-menu--collapse .el-sub-menu__title) {
  justify-content: center;
  padding-left: 0 !important;
  padding-right: 0 !important;
  margin: 2px 12px;
}

/* Scrollbar styling */
.sidebar-menu::-webkit-scrollbar {
  width: 4px;
}

.sidebar-menu::-webkit-scrollbar-track {
  background: transparent;
}

.sidebar-menu::-webkit-scrollbar-thumb {
  background: var(--lumina-border, #334155);
  border-radius: 4px;
}

.sidebar-menu::-webkit-scrollbar-thumb:hover {
  background: var(--lumina-border-light, #475569);
}

/* ---------- Bottom Decorative Glow ---------- */
.sidebar-glow {
  flex-shrink: 0;
  height: 2px;
  margin: 8px 16px 16px;
  border-radius: 1px;
  background: linear-gradient(
    90deg,
    transparent 0%,
    rgba(124, 58, 237, 0.3) 30%,
    rgba(245, 158, 11, 0.15) 50%,
    rgba(124, 58, 237, 0.3) 70%,
    transparent 100%
  );
  opacity: 0.6;
}

.app-sidebar.collapsed .sidebar-glow {
  margin: 8px 12px 16px;
  background: linear-gradient(
    90deg,
    transparent 0%,
    rgba(124, 58, 237, 0.2) 50%,
    transparent 100%
  );
}
</style>
