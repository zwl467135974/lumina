<template>
  <div class="error-page">
    <el-result icon="warning" title="401" :sub-title="t('error.unauthorized')">
      <template #extra>
        <el-button type="primary" @click="router.push('/login')">{{ t('error.backHome') }}</el-button>
      </template>
    </el-result>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
const router = useRouter()
const { t } = useI18n()
</script>

<style scoped>
.error-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
}
</style>
