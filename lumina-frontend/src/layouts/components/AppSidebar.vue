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
      <template v-for="route in menuRoutes" :key="route.path">
        <el-sub-menu v-if="route.children?.length" :index="route.path">
          <template #title>
            <el-icon v-if="route.meta?.icon">
              <component :is="route.meta.icon" />
            </el-icon>
            <span>{{ route.meta?.title }}</span>
          </template>
          <el-menu-item
            v-for="child in route.children"
            :key="child.path"
            :index="child.path"
          >
            {{ child.meta?.title }}
          </el-menu-item>
        </el-sub-menu>
        <el-menu-item v-else :index="route.path">
          <el-icon v-if="route.meta?.icon">
            <component :is="route.meta.icon" />
          </el-icon>
          <template #title>{{ route.meta?.title }}</template>
        </el-menu-item>
      </template>
    </el-menu>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAppStore } from '@/stores'
import { agentRoutes, systemRoutes } from '@/router/modules'

const route = useRoute()
const appStore = useAppStore()

const activeMenu = computed(() => route.path)

const menuRoutes = computed(() => {
  return [...agentRoutes, ...systemRoutes].filter((route) => !route.meta?.hidden)
})
</script>

<style scoped lang="scss">
.app-sidebar {
  width: 200px;
  height: 100%;
  background-color: #304156;
  transition: width 0.3s;

  &.collapsed {
    width: 64px;
  }

  .sidebar-logo {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 60px;
    background-color: #2b3a4a;

    h2 {
      margin: 0;
      font-size: 20px;
      font-weight: 600;
      color: #fff;
    }
  }

  :deep(.el-menu) {
    border-right: none;
    background-color: #304156;

    .el-menu-item,
    .el-sub-menu__title {
      color: #bfcbd9;

      &:hover {
        background-color: #263445;
      }
    }

    .el-menu-item.is-active {
      color: var(--el-color-primary);
      background-color: #263445;
    }
  }
}
</style>
