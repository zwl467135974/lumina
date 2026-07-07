<template>
  <div class="error-page">
    <el-result icon="warning" title="403" :sub-title="t('error.forbidden')">
      <template #extra>
        <el-button type="primary" @click="router.push('/')">{{ t('error.backHome') }}</el-button>
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
