<template>
  <div class="oauth2-callback-page">
    <el-card class="callback-card" shadow="never">
      <div v-if="loading" class="callback-body">
        <el-icon class="is-loading" :size="28"><Loading /></el-icon>
        <p>{{ t('login.oauth2.processing') }}</p>
      </div>
      <div v-else-if="errorMessage" class="callback-body">
        <el-result icon="error" :title="t('login.oauth2.failed')" :sub-title="errorMessage">
          <template #extra>
            <el-button type="primary" @click="goLogin">{{ t('login.oauth2.backToLogin') }}</el-button>
          </template>
        </el-result>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Loading } from '@element-plus/icons-vue'
import { setToken } from '@/utils'

defineOptions({ name: 'OAuth2Callback' })

const route = useRoute()
const router = useRouter()
const { t } = useI18n()

const loading = ref(true)
const errorMessage = ref('')

onMounted(() => {
  const token = String(route.query.token || '')
  if (token) {
    setToken(token)
    router.replace('/')
  } else {
    loading.value = false
    errorMessage.value = String(route.query.error || t('login.oauth2.noToken'))
  }
})

const goLogin = () => router.replace('/login')
</script>

<style scoped lang="scss">
.oauth2-callback-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: var(--lumina-bg-page);
}

.callback-card {
  width: 380px;
  text-align: center;
}

.callback-body {
  padding: var(--lumina-spacing-lg, 24px) 0;
  color: var(--lumina-text-primary);
}
</style>
