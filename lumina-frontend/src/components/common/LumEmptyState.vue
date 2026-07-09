<template>
  <div class="lum-empty-state">
    <el-icon :size="48" class="lum-empty-state__icon"><component :is="icon" /></el-icon>
    <p class="lum-empty-state__description">{{ description }}</p>
    <div v-if="$slots.default" class="lum-empty-state__action">
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
interface Props {
  description?: string
  icon?: string
}

withDefaults(defineProps<Props>(), {
  icon: 'Box',
  description: ''
})
</script>

<style scoped>
.lum-empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--lumina-spacing-md);
  padding: var(--lumina-spacing-2xl) var(--lumina-spacing-lg);
}

.lum-empty-state__icon {
  color: var(--lumina-text-muted);
}

.lum-empty-state__description {
  font-size: var(--lumina-font-size-base);
  color: var(--lumina-text-secondary);
  margin: 0;
}

.lum-empty-state__action {
  margin-top: var(--lumina-spacing-sm);
}
</style>
