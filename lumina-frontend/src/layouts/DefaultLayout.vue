<template>
  <el-container class="lumina-layout">
    <!-- Sidebar -->
    <AppSidebar :menu-routes="menuRoutes" />

    <!-- Main Container -->
    <el-container class="main-container" :class="{ 'sidebar-collapsed': appStore.sidebarCollapsed }">
      <!-- Header -->
      <AppHeader :breadcrumbs="breadcrumbs" />

      <!-- Breadcrumb Area -->
      <div class="breadcrumb-bar" v-if="breadcrumbs?.length">
        <el-breadcrumb separator="·">
          <el-breadcrumb-item
            v-for="item in breadcrumbs"
            :key="item.path"
            :to="item.path"
          >
            {{ item.meta?.title || item.name }}
          </el-breadcrumb-item>
        </el-breadcrumb>
      </div>

      <!-- Page Content -->
      <el-main class="page-content">
        <router-view v-slot="{ Component, route }">
          <transition name="lumina-fade" mode="out-in">
            <component :is="Component" :key="route.path" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAppStore } from '@/stores'
import AppSidebar from './components/AppSidebar.vue'
import AppHeader from './components/AppHeader.vue'
import type { RouteRecordRaw } from 'vue-router'

const route = useRoute()
const appStore = useAppStore()

const props = withDefaults(defineProps<{
  menuRoutes: RouteRecordRaw[]
}>(), {
  menuRoutes: () => []
})

const breadcrumbs = computed(() => {
  const matched = route.matched.filter(r => r.meta?.title)
  return matched.map(r => ({
    path: r.path,
    name: r.name as string | undefined,
    meta: r.meta as { title?: string } | undefined
  }))
})
</script>

<style scoped>
/* ============================================================
   DefaultLayout — Luminous Dark Theme
   Design: dark base, generous padding, styled breadcrumb bar
   ============================================================ */

.lumina-layout {
  width: 100%;
  height: 100vh;
  overflow: hidden;
  background: var(--lumina-bg-base, #0f172a);
}

/* ---------- Main Container ---------- */
.main-container {
  flex-direction: column;
  height: 100vh;
  margin-left: 220px;
  transition: margin-left var(--lumina-transition-base, 250ms cubic-bezier(0.4, 0, 0.2, 1));
  background: var(--lumina-bg-base, #0f172a);
  overflow: hidden;
}

.main-container.sidebar-collapsed {
  margin-left: 64px;
}

/* ---------- Breadcrumb Bar ---------- */
.breadcrumb-bar {
  position: sticky;
  top: 56px; /* below header */
  z-index: 999;
  height: 40px;
  display: flex;
  align-items: center;
  padding: 0 24px;
  background: var(--lumina-bg-elevated, #1e293b);
  border-bottom: 1px solid var(--lumina-border, #334155);
}

.breadcrumb-bar :deep(.el-breadcrumb) {
  font-family: var(--lumina-font-body, 'IBM Plex Sans', -apple-system, sans-serif);
  font-size: 13px;
  line-height: 1;
}

.breadcrumb-bar :deep(.el-breadcrumb__item) {
  display: inline-flex;
  align-items: center;
}

.breadcrumb-bar :deep(.el-breadcrumb__inner) {
  color: var(--lumina-text-muted, #64748b);
  font-weight: 400;
  text-decoration: none;
  transition: color var(--lumina-transition-fast, 150ms cubic-bezier(0.4, 0, 0.2, 1));
}

.breadcrumb-bar :deep(.el-breadcrumb__inner:hover) {
  color: var(--lumina-primary-light, #a78bfa);
}

.breadcrumb-bar :deep(.el-breadcrumb__item:last-child .el-breadcrumb__inner) {
  color: var(--lumina-primary-light, #a78bfa);
  font-weight: 600;
}

.breadcrumb-bar :deep(.el-breadcrumb__separator) {
  color: var(--lumina-text-muted, #64748b);
  margin: 0 8px;
  font-weight: 400;
  font-size: 10px;
}

/* ---------- Page Content ---------- */
.page-content {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 24px;
  background: var(--lumina-bg-base, #0f172a);
  scrollbar-width: thin;
  scrollbar-color: var(--lumina-border, #334155) transparent;
}

.page-content::-webkit-scrollbar {
  width: 6px;
}

.page-content::-webkit-scrollbar-track {
  background: transparent;
}

.page-content::-webkit-scrollbar-thumb {
  background: var(--lumina-border, #334155);
  border-radius: 3px;
}

.page-content::-webkit-scrollbar-thumb:hover {
  background: var(--lumina-border-light, #475569);
}

/* ---------- Route Transition ---------- */
.lumina-fade-enter-active,
.lumina-fade-leave-active {
  transition:
    opacity var(--lumina-transition-base, 250ms cubic-bezier(0.4, 0, 0.2, 1)),
    transform var(--lumina-transition-base, 250ms cubic-bezier(0.4, 0, 0.2, 1));
}

.lumina-fade-enter-from {
  opacity: 0;
  transform: translateY(8px);
}

.lumina-fade-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

/* ---------- Responsive ---------- */
@media (max-width: 768px) {
  .main-container {
    margin-left: 64px;
  }

  .page-content {
    padding: 16px;
  }

  .breadcrumb-bar {
    padding: 0 16px;
  }
}
</style>
