<template>
  <div class="page-header">
    <div class="page-header-left">
      <div class="page-header-accent-bar" aria-hidden="true" />
      <div class="page-header-text">
        <h2 class="page-header-title">{{ title }}</h2>
        <p v-if="description" class="page-header-desc">{{ description }}</p>
      </div>
    </div>
    <div class="page-header-actions">
      <slot name="actions" />
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  title: string
  description?: string
}>()
</script>

<style scoped>
/* ============================================================
   PageHeader — Luminous Dark Theme
   方案 A: flex-wrap 操作区, 防止按钮溢出
   ============================================================ */

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: var(--lumina-spacing-md);
  margin-bottom: var(--lumina-spacing-xl);
}

/* ---------- Left Section ---------- */
.page-header-left {
  display: flex;
  align-items: flex-start;
  gap: var(--lumina-spacing-md);
  padding-left: var(--lumina-spacing-md);
  position: relative;
  flex: 1;
  min-width: 0;
}

/* Gradient accent bar */
.page-header-accent-bar {
  position: absolute;
  left: 0;
  top: 4px;
  bottom: 4px;
  width: 4px;
  border-radius: 2px;
  background: linear-gradient(
    180deg,
    var(--lumina-primary) 0%,
    var(--lumina-accent) 100%
  );
  box-shadow: 0 0 12px var(--lumina-primary-glow);
  flex-shrink: 0;
}

.page-header-text {
  display: flex;
  flex-direction: column;
  gap: var(--lumina-spacing-xs);
  min-width: 0;
}

.page-header-title {
  font-family: var(--lumina-font-display);
  font-size: var(--lumina-font-size-2xl);
  font-weight: var(--lumina-font-weight-bold);
  color: var(--lumina-text-primary);
  line-height: var(--lumina-line-height-tight);
  letter-spacing: -0.02em;
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.page-header-desc {
  font-family: var(--lumina-font-body);
  font-size: var(--lumina-font-size-base);
  font-weight: var(--lumina-font-weight-normal);
  color: var(--lumina-text-muted);
  line-height: var(--lumina-line-height-base);
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ---------- Actions Section (可换行) ---------- */
.page-header-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--lumina-spacing-sm);
  flex-shrink: 0;
  padding-top: 2px;
}

/* ---------- Responsive ---------- */
@media (max-width: 768px) {
  .page-header {
    flex-direction: column;
    align-items: stretch;
  }

  .page-header-actions {
    width: 100%;
    justify-content: flex-start;
    padding-left: var(--lumina-spacing-md);
  }
}
</style>
