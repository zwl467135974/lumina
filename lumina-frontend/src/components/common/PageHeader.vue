<template>
  <div class="page-header">
    <div class="page-header-left">
      <h2 class="page-header-title">{{ title }}</h2>
      <p v-if="description" class="page-header-desc">{{ description }}</p>
    </div>
    <div class="page-header-actions">
      <slot name="actions" />
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
interface Props {
  title?: string
  description?: string
}

withDefaults(defineProps<Props>(), {
  title: '',
  description: ''
})
</script>

<style scoped lang="scss">
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 20px;
  flex-wrap: wrap;
  gap: 12px;

  .page-header-left {
    flex: 1;
    min-width: 0;
  }

  .page-header-title {
    font-size: 22px;
    font-weight: 600;
    margin: 0;
    color: var(--el-text-color-primary);
    line-height: 1.4;
  }

  .page-header-desc {
    font-size: 13px;
    color: var(--el-text-color-secondary);
    margin-top: 4px;
    line-height: 1.5;
  }

  .page-header-actions {
    display: flex;
    gap: 8px;
    align-items: center;
    flex-shrink: 0;
  }
}
</style>
