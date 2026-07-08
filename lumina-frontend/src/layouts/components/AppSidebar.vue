<template>
  <div class="app-sidebar" :class="{ collapsed: appStore.sidebarCollapsed }">
    <div class="sidebar-logo">
      <h2 v-if="!appStore.sidebarCollapsed">Lumina</h2>
      <h2 v-else>L</h2>
    </div>
    <el-menu
      :default-active="activeMenu"
      :collapse="appStore.sidebarCollapsed"
      :unique-opened="true"
      router
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
  </div>
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
.app-sidebar {
  position: fixed;
  top: 0; left: 0; bottom: 0;
  width: 220px;
  display: flex;
  flex-direction: column;
  background: linear-gradient(180deg, #0f172a 0%, #1a1040 60%, #0f172a 100%);
  overflow: hidden;
  z-index: 1001;
  transition: width 250ms cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 2px 0 24px rgba(0, 0, 0, 0.4);
}
.app-sidebar.collapsed { width: 64px; }

.sidebar-logo {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 60px;
  background: rgba(124, 58, 237, 0.08);
  border-bottom: 1px solid rgba(124, 58, 237, 0.15);
}
.sidebar-logo h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: transparent;
  background: linear-gradient(135deg, #a78bfa, #f59e0b);
  background-clip: text;
  -webkit-background-clip: text;
}

:deep(.el-menu) {
  border-right: none;
  background: transparent;
  flex: 1;
  padding: 8px;
}
:deep(.el-menu-item),
:deep(.el-sub-menu__title) {
  color: #94a3b8;
  border-radius: 8px;
  margin: 2px 0;
  height: 44px;
  line-height: 44px;
}
:deep(.el-menu-item:hover),
:deep(.el-sub-menu__title:hover) {
  background: rgba(124, 58, 237, 0.1);
  color: #f1f5f9;
}
:deep(.el-menu-item.is-active) {
  background: linear-gradient(90deg, rgba(124, 58, 237, 0.2), rgba(124, 58, 237, 0.05));
  color: #a78bfa;
  position: relative;
}
:deep(.el-menu-item.is-active::before) {
  content: '';
  position: absolute;
  left: 0; top: 25%; bottom: 25%;
  width: 3px;
  border-radius: 0 3px 3px 0;
  background: linear-gradient(180deg, #7c3aed, #f59e0b);
}

.sidebar-glow {
  height: 2px;
  background: linear-gradient(90deg, transparent, rgba(124, 58, 237, 0.4), transparent);
}

@media (max-width: 768px) {
  :deep(.el-col) { max-width: 100%; flex: 0 0 100%; }
}
</style>
