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
        <!-- 有子菜单：展开为子菜单组 -->
        <el-sub-menu v-if="menu.children?.length" :index="menu.path">
          <template #title>
            <el-icon v-if="menu.icon">
              <component :is="menu.icon" />
            </el-icon>
            <span>{{ localizeTitle(menu.title) }}</span>
          </template>
          <el-menu-item
            v-for="child in menu.children"
            :key="child.path"
            :index="child.path"
          >
            {{ localizeTitle(child.title) }}
          </el-menu-item>
        </el-sub-menu>
        <!-- 无子菜单：单级菜单项 -->
        <el-menu-item v-else :index="menu.path">
          <el-icon v-if="menu.icon">
            <component :is="menu.icon" />
          </el-icon>
          <template #title>{{ localizeTitle(menu.title) }}</template>
        </el-menu-item>
      </template>
    </el-menu>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAppStore, useUserStore } from '@/stores'
import type { MenuVO } from '@/api/modules/menu'

const { t } = useI18n()
const route = useRoute()
const appStore = useAppStore()
const userStore = useUserStore()

const activeMenu = computed(() => route.path)

/**
 * 中文菜单标题 → i18n key 映射表
 * 用于本地化后端动态下发的菜单标题
 */
const titleKeyMap: Record<string, string> = {
  '仪表盘': 'menu.dashboard',
  'Agent 管理': 'menu.agent',
  'Agent 列表': 'menu.agentList',
  '异步任务': 'menu.agentTasks',
  '工作流管理': 'menu.workflow',
  '工作流': 'menu.workflow',
  '知识库': 'menu.knowledge',
  '知识库联邦': 'menu.knowledgeBase',
  'Prompt 管理': 'menu.prompt',
  '成本仪表盘': 'menu.cost',
  '预算管理': 'menu.budget',
  'Agent 评估': 'menu.evaluation',
  '系统管理': 'menu.system',
  '用户管理': 'menu.user',
  '角色管理': 'menu.role',
  '权限管理': 'menu.permission',
  '租户管理': 'menu.tenant',
  '工具监控': 'menu.monitor'
}

/**
 * 将菜单标题本地化
 * 优先按已知中文 → i18n key 映射；未命中则原样返回（保留后端下发的原始标题）
 */
const localizeTitle = (title?: string): string => {
  if (!title) return ''
  const key = titleKeyMap[title]
  return key ? t(key) : title
}

/**
 * 侧边栏菜单列表
 *
 * 后端动态下发菜单优先；后端未下发的路由（如工作流）由前端静态补充。
 */
const menuList = computed<MenuVO[]>(() => {
  const backendMenus = userStore.menus
  const extras: MenuVO[] = []
  if (!backendMenus.some(m => m.path?.includes('workflow'))) {
    extras.push({ name: 'workflow', path: '/workflow/list', title: t('menu.workflow'), icon: 'Connection' } as MenuVO)
  }
  if (!backendMenus.some(m => m.path?.includes('prompt'))) {
    extras.push({ name: 'prompt', path: '/prompt', title: t('menu.prompt'), icon: 'EditPen' } as MenuVO)
  }
  if (!backendMenus.some(m => m.path?.includes('cost'))) {
    extras.push({ name: 'cost', path: '/cost', title: t('menu.cost'), icon: 'Money' } as MenuVO)
  }
  return [...backendMenus, ...extras]
})

/**
 * 页面刷新时如果 store 有 token 但菜单为空（持久化恢复后），
 * 重新拉取菜单
 */
onMounted(async () => {
  if (userStore.isLoggedIn && menuList.value.length === 0) {
    await userStore.loadMenus()
  }
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
