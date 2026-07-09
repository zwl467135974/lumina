<template>
  <el-input
    :model-value="modelValue"
    :placeholder="placeholder"
    :clearable="clearable"
    :debounce="debounce"
    class="lum-search-input"
    @update:model-value="onInput"
    @keyup.enter="$emit('search', modelValue)"
    @clear="$emit('search', '')"
  >
    <template #prefix>
      <el-icon><Search /></el-icon>
    </template>
    <template v-if="$slots.append" #append>
      <slot name="append" />
    </template>
  </el-input>
</template>

<script setup lang="ts">
import { Search } from '@element-plus/icons-vue'

interface Props {
  modelValue: string
  placeholder?: string
  clearable?: boolean
  debounce?: number
}

withDefaults(defineProps<Props>(), {
  placeholder: '',
  clearable: true,
  debounce: 300
})

const emit = defineEmits<{
  'update:modelValue': [val: string]
  search: [val: string]
}>()

const onInput = (val: string) => {
  emit('update:modelValue', val)
  emit('search', val)
}
</script>

<style scoped>
.lum-search-input {
  width: 240px;
}
</style>
