<template>
  <div class="page-header">
    <div class="page-header-title">
      <slot name="title">{{ title }}</slot>
    </div>
    <div class="page-header-actions">
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
interface Props {
  title?: string
}

withDefaults(defineProps<Props>(), {
  title: ''
})
</script>

<style scoped lang="scss">
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;

  .page-header-title {
    font-size: 20px;
    font-weight: 500;
    color: #333;
  }

  .page-header-actions {
    display: flex;
    gap: 8px;
  }
}
</style>
