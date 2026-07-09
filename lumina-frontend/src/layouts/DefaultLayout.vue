<template>
  <div class="lumina-layout">
    <!-- Sidebar -->
    <AppSidebar :menu-routes="menuRoutes" />

    <!-- Main Container -->
    <div class="main-container">
      <!-- Header -->
      <AppHeader :breadcrumbs="breadcrumbs" />

      <!-- Page Content -->
      <main class="page-content">
        <router-view v-slot="{ Component, route }">
          <transition name="lumina-fade" mode="out-in">
            <component :is="Component" :key="route.path" />
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AppSidebar from './components/AppSidebar.vue'
import AppHeader from './components/AppHeader.vue'
import type { RouteRecordRaw } from 'vue-router'

const route = useRoute()

withDefaults(defineProps<{
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
.lumina-layout {
  display: flex;
  width: 100%;
  height: 100vh;
  overflow: hidden;
  background: var(--lumina-bg-base);
}

.main-container {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  height: 100vh;
  overflow: hidden;
  background: var(--lumina-bg-base);
}

.page-content {
  flex: 1;
  overflow-y: auto;
  overflow-x: auto;
  padding: var(--lumina-spacing-lg);
  background: var(--lumina-bg-base);
  scrollbar-width: thin;
  scrollbar-color: var(--lumina-border) transparent;
}

.page-content::-webkit-scrollbar {
  width: var(--lumina-scrollbar-width);
  height: var(--lumina-scrollbar-width);
}

.page-content::-webkit-scrollbar-track {
  background: transparent;
}

.page-content::-webkit-scrollbar-thumb {
  background: var(--lumina-border);
  border-radius: 3px;
}

.page-content::-webkit-scrollbar-thumb:hover {
  background: var(--lumina-border-light);
}

/* ---------- Route Transition ---------- */
.lumina-fade-enter-active,
.lumina-fade-leave-active {
  transition:
    opacity var(--lumina-transition-base),
    transform var(--lumina-transition-base);
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
  .page-content {
    padding: var(--lumina-spacing-md);
  }
}
</style>
