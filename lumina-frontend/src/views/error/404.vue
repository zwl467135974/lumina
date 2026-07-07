<template>
  <div class="not-found-page">
    <div class="not-found-content">
      <h1>404</h1>
      <p>{{ t('error.notFound') }}</p>
      <el-button type="primary" @click="goHome">{{ t('error.backHome') }}</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const router = useRouter()

const goHome = () => {
  router.push('/agent/list')
}
</script>

<style scoped lang="scss">
.not-found-page {
  display: flex;
  justify-content: center;
  align-items: center;
  width: 100%;
  height: 100vh;
  background-color: #f5f5f5;
}

.not-found-content {
  text-align: center;

  h1 {
    font-size: 120px;
    font-weight: 600;
    color: var(--el-color-primary);
    margin: 0 0 20px;
  }

  p {
    font-size: 18px;
    color: #666;
    margin: 0 0 30px;
  }
}
</style>
