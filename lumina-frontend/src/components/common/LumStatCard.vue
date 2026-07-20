<template>
  <div
    class="lum-stat-card"
    :class="[`lum-stat-card--${color}`, { 'lum-stat-card--clickable': clickable }]"
    @click="clickable && $emit('click')"
  >
    <div class="lum-stat-card__icon">
      <el-icon :size="22"><component :is="icon" v-if="icon" /></el-icon>
    </div>
    <div class="lum-stat-card__body">
      <span class="lum-stat-card__label">{{ label }}</span>
      <span class="lum-stat-card__value">{{ value }}</span>
      <span v-if="suffix" class="lum-stat-card__suffix">{{ suffix }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
interface Props {
  label: string
  value: string | number
  icon?: string
  color?: 'primary' | 'success' | 'warning' | 'danger' | 'info'
  suffix?: string
  clickable?: boolean
}

withDefaults(defineProps<Props>(), {
  color: 'primary',
  clickable: false
})

defineEmits<{ click: [] }>()
</script>

<style scoped>
.lum-stat-card {
  display: flex;
  align-items: center;
  gap: var(--lumina-spacing-md);
  padding: var(--lumina-spacing-lg);
  background: var(--lumina-bg-card);
  border: 1px solid var(--lumina-border);
  border-radius: var(--lumina-radius-lg);
  transition:
    box-shadow var(--lumina-transition-base),
    border-color var(--lumina-transition-base);
}

.lum-stat-card--clickable {
  cursor: pointer;
}

.lum-stat-card--clickable:hover {
  border-color: var(--lumina-border-hover);
  box-shadow: var(--lumina-shadow-md);
}

.lum-stat-card__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border-radius: var(--lumina-radius-md);
  flex-shrink: 0;
}

.lum-stat-card__body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.lum-stat-card__label {
  font-size: var(--lumina-font-size-sm);
  color: var(--lumina-text-secondary);
  font-weight: var(--lumina-font-weight-medium);
}

.lum-stat-card__value {
  font-family: var(--lumina-font-display);
  font-size: var(--lumina-font-size-2xl);
  font-weight: var(--lumina-font-weight-bold);
  color: var(--lumina-text-primary);
  line-height: 1.2;
}

.lum-stat-card__suffix {
  font-size: var(--lumina-font-size-sm);
  color: var(--lumina-text-muted);
}

/* Color variants */
.lum-stat-card--primary .lum-stat-card__icon {
  background: rgba(var(--lumina-primary-rgb), 0.1);
  color: var(--lumina-primary);
}

.lum-stat-card--success .lum-stat-card__icon {
  background: rgba(var(--lumina-success-rgb), 0.1);
  color: var(--lumina-success);
}

.lum-stat-card--warning .lum-stat-card__icon {
  background: rgba(var(--lumina-warning-rgb), 0.1);
  color: var(--lumina-warning);
}

.lum-stat-card--danger .lum-stat-card__icon {
  background: rgba(var(--lumina-danger-rgb), 0.1);
  color: var(--lumina-danger);
}

.lum-stat-card--info .lum-stat-card__icon {
  background: rgba(var(--lumina-info-rgb), 0.1);
  color: var(--lumina-info);
}

@media (max-width: 639px) {
  .lum-stat-card { padding: var(--lumina-spacing-md); }
}
</style>
