<template>
  <div class="wf-node" :style="nodeStyle">
    <Handle type="target" :position="Position.Top" />
    <div class="wf-node-header">
      <span class="wf-node-icon">{{ config?.icon || '📦' }}</span>
      <span class="wf-node-name">{{ data.label }}</span>
    </div>
    <div class="wf-node-type">{{ data.nodeType }}</div>
    <div v-if="data.properties?.agentId" class="wf-node-meta">Agent #{{ data.properties.agentId }}</div>
    <Handle type="source" :position="Position.Bottom" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Handle, Position } from '@vue-flow/core'
import { NODE_TYPES } from './node-types'

const props = defineProps<{
  id: string
  data: { label: string; nodeType: string; properties: Record<string, any> }
}>()

const config = computed(() => NODE_TYPES[props.data.nodeType] || null)

const nodeStyle = computed(() => ({
  borderColor: config.value?.color || '#dcdfe6',
  backgroundColor: config.value?.bgColor || '#fff'
}))
</script>

<style scoped>
.wf-node {
  padding: 10px 14px;
  border: 2px solid;
  border-radius: 8px;
  min-width: 160px;
  max-width: 220px;
  cursor: pointer;
  transition: box-shadow 0.2s;
}
.wf-node:hover {
  box-shadow: 0 2px 12px rgba(0,0,0,0.15);
}
.wf-node-header {
  display: flex;
  align-items: center;
  gap: 6px;
}
.wf-node-icon {
  font-size: 18px;
}
.wf-node-name {
  font-weight: 600;
  font-size: 13px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.wf-node-type {
  font-size: 11px;
  color: #909399;
  margin-top: 2px;
}
.wf-node-meta {
  font-size: 11px;
  color: #409eff;
  margin-top: 2px;
}
</style>
