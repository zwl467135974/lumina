<template>
  <div class="lumina-layout">
    <!-- Sidebar -->
    <AppSidebar />

    <!-- Main Container -->
    <div class="main-container">
      <!-- Header -->
      <AppHeader :breadcrumbs="breadcrumbs" />

      <!-- Page Content -->
      <main class="page-content">
        <router-view v-slot="{ Component, route }">
          <transition name="lumina-fade" mode="out-in">
            <keep-alive :include="cachedViews">
              <component :is="Component" :key="route.fullPath" />
            </keep-alive>
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppSidebar from './components/AppSidebar.vue'
import AppHeader from './components/AppHeader.vue'

const route = useRoute()
const router = useRouter()

// keep-alive 缓存列表：读取路由表中 meta.keepAlive 的组件 name
const cachedViews = computed<string[]>(() => {
  const names: string[] = []
  router.getRoutes().forEach(r => {
    if (r.meta?.keepAlive && r.name) names.push(r.name as string)
  })
  return names
})

// 面包屑：用 route.path 实际解析路径，避免动态路由 :id 模板导致点击 404
const breadcrumbs = computed(() => {
  const matched = route.matched.filter(r => r.meta?.title)
  return matched.map((r, idx) => ({
    // 最后一个用 route.fullPath（当前真实路径），其余用 record.path（但需替换动态段）
    path: idx === matched.length - 1 ? route.fullPath : r.path,
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
  background: var(--lumina-bg-page);
}

.main-container {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  height: 100vh;
  overflow: hidden;
  background: var(--lumina-bg-page);
}

.page-content {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: auto;
  padding: var(--lumina-spacing-lg);
  background: var(--lumina-bg-page);
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
