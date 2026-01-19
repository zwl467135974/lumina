<template>
  <div class="layout-container">
    <app-header />
    <div class="layout-content">
      <app-sidebar />
      <div class="layout-main" :class="{ collapsed: appStore.sidebarCollapsed }">
        <app-breadcrumb />
        <div class="layout-page">
          <router-view v-slot="{ Component }">
            <keep-alive>
              <component :is="Component" v-if="$route.meta?.keepAlive" :key="$route.path" />
            </keep-alive>
            <component :is="Component" v-if="!$route.meta?.keepAlive" :key="$route.path" />
          </router-view>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useAppStore } from '@/stores'
import AppHeader from './components/AppHeader.vue'
import AppSidebar from './components/AppSidebar.vue'
import AppBreadcrumb from './components/AppBreadcrumb.vue'

const appStore = useAppStore()
</script>

<style scoped lang="scss">
.layout-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
}

.layout-content {
  display: flex;
  flex: 1;
  overflow: hidden;
}

.layout-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  transition: margin-left 0.3s;

  .layout-page {
    flex: 1;
    padding: 20px;
    overflow-y: auto;
    background-color: #f5f5f5;
  }
}

.collapsed {
  margin-left: 64px;
}
</style>
