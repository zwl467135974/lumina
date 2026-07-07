<template>
  <div class="app-header">
    <div class="header-left">
      <el-icon class="collapse-icon" @click="handleCollapse">
        <Fold v-if="!appStore.sidebarCollapsed" />
        <Expand v-else />
      </el-icon>
    </div>
    <div class="header-right">
      <!-- 语言切换 -->
      <el-dropdown @command="toggleLang" trigger="click">
        <span class="lang-switch">
          {{ currentLang === 'zh-CN' ? '中文' : 'EN' }}
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="zh-CN" :class="{ 'is-active': currentLang === 'zh-CN' }">中文</el-dropdown-item>
            <el-dropdown-item command="en" :class="{ 'is-active': currentLang === 'en' }">English</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>

      <el-icon class="theme-icon" @click="toggleTheme">
        <Sunny v-if="isDark" />
        <Moon v-else />
      </el-icon>
      <el-dropdown @command="handleCommand">
        <span class="user-dropdown">
          <el-avatar :size="32" :src="userInfo?.avatar">
            {{ userInfo?.username?.charAt(0)?.toUpperCase() }}
          </el-avatar>
          <span class="username">{{ userInfo?.nickname || userInfo?.username }}</span>
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="profile">{{ t('header.profile') }}</el-dropdown-item>
            <el-dropdown-item command="logout" divided>{{ t('header.logout') }}</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { Fold, Expand, Moon, Sunny } from '@element-plus/icons-vue'
import { useAppStore, useUserStore } from '@/stores'

const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()
const { locale, t } = useI18n()

const currentLang = computed(() => locale.value)

const toggleLang = (lang: string) => {
  locale.value = lang
  localStorage.setItem('lumina-lang', lang)
}

const userInfo = computed(() => userStore.userInfo)

const isDark = ref(false)

const toggleTheme = () => {
  isDark.value = !isDark.value
  document.documentElement.classList.toggle('dark', isDark.value)
  localStorage.setItem('lumina-theme', isDark.value ? 'dark' : 'light')
}

onMounted(() => {
  const saved = localStorage.getItem('lumina-theme')
  if (saved === 'dark') {
    isDark.value = true
    document.documentElement.classList.add('dark')
  }
})

const handleCollapse = () => {
  appStore.toggleSidebar()
}

const handleCommand = async (command: string) => {
  if (command === 'logout') {
    try {
      await ElMessageBox.confirm(t('header.logout') + '?', '', {
        type: 'warning'
      })
      await userStore.logout()
      router.push('/login')
    } catch {
      // 用户取消
    }
  } else if (command === 'profile') {
    ElMessage.info('个人中心功能开发中')
  }
}
</script>

<style scoped lang="scss">
.app-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: 60px;
  padding: 0 20px;
  background-color: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color);

  .header-left {
    display: flex;
    align-items: center;

    .collapse-icon {
      font-size: 20px;
      cursor: pointer;
      transition: color 0.3s;

      &:hover {
        color: var(--el-color-primary);
      }
    }
  }

  .header-right {
    display: flex;
    align-items: center;
    gap: 16px;

    .lang-switch {
      font-size: 14px;
      cursor: pointer;
      color: var(--el-text-color-regular);
      user-select: none;
      &:hover { color: var(--el-color-primary); }
    }

    .theme-icon {
      font-size: 18px;
      cursor: pointer;
      color: var(--el-text-color-regular);
      transition: color 0.3s;

      &:hover {
        color: var(--el-color-primary);
      }
    }

    .user-dropdown {
      display: flex;
      align-items: center;
      gap: 8px;
      cursor: pointer;

      .username {
        font-size: 14px;
        color: var(--el-text-color-primary);
      }
    }
  }
}
</style>
