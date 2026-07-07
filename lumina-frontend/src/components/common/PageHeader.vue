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
   Design: display font title, gradient accent bar, spacious
   ============================================================ */

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 28px;
}

/* ---------- Left Section ---------- */
.page-header-left {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  padding-left: 16px;
  position: relative;
  flex: 1;
  min-width: 0;
}

/* Gradient accent bar — vertical line */
.page-header-accent-bar {
  position: absolute;
  left: 0;
  top: 4px;
  bottom: 4px;
  width: 4px;
  border-radius: 2px;
  background: linear-gradient(
    180deg,
    var(--lumina-primary, #7c3aed) 0%,
    var(--lumina-accent, #f59e0b) 100%
  );
  box-shadow: 0 0 12px rgba(124, 58, 237, 0.3);
  flex-shrink: 0;
}

/* Text wrapper */
.page-header-text {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

/* Title */
.page-header-title {
  font-family: var(--lumina-font-display, 'Outfit', -apple-system, sans-serif);
  font-size: 24px;
  font-weight: 700;
  color: var(--lumina-text-primary, #f1f5f9);
  line-height: 1.3;
  letter-spacing: -0.02em;
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* Description */
.page-header-desc {
  font-family: var(--lumina-font-body, 'IBM Plex Sans', -apple-system, sans-serif);
  font-size: 14px;
  font-weight: 400;
  color: var(--lumina-text-muted, #64748b);
  line-height: 1.5;
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ---------- Actions Section ---------- */
.page-header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
  padding-top: 2px; /* visual alignment with title */
}

/* Responsive: stack on narrow screens */
@media (max-width: 640px) {
  .page-header {
    flex-direction: column;
    gap: 16px;
  }

  .page-header-actions {
    width: 100%;
    justify-content: flex-start;
    padding-left: 16px;
  }
}
</style>
