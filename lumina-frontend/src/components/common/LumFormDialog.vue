<template>
  <el-dialog
    :model-value="modelValue"
    :title="title"
    :width="width"
    @update:model-value="$emit('update:modelValue', $event)"
    @close="handleClose"
  >
    <el-form
      ref="formRef"
      :model="model"
      :rules="rules"
      :label-width="labelWidth"
      v-bind="$attrs"
    >
      <slot />
    </el-form>

    <template #footer>
      <slot name="footer">
        <el-button @click="$emit('update:modelValue', false)">
          {{ cancelText }}
        </el-button>
        <el-button type="primary" :loading="loading" @click="handleSubmit">
          {{ confirmText }}
        </el-button>
      </slot>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { FormInstance, FormRules } from 'element-plus'

const { t } = useI18n()

interface Props {
  modelValue: boolean
  title: string
  model?: Record<string, any>
  rules?: FormRules
  loading?: boolean
  width?: string
  labelWidth?: string
  cancelText?: string
  confirmText?: string
}

const props = withDefaults(defineProps<Props>(), {
  model: () => ({}),
  loading: false,
  width: '600px',
  labelWidth: '100px',
  cancelText: '',
  confirmText: ''
})

const emit = defineEmits<{
  'update:modelValue': [val: boolean]
  submit: []
  close: []
}>()

defineOptions({ inheritAttrs: false })

const formRef = ref<FormInstance>()

const cancelText = props.cancelText || t('common.cancel')
const confirmText = props.confirmText || t('common.confirm')

const handleSubmit = async () => {
  if (!formRef.value) {
    emit('submit')
    return
  }
  try {
    await formRef.value.validate()
    emit('submit')
  } catch {
    // 校验不通过
  }
}

const handleClose = () => {
  formRef.value?.resetFields()
  emit('close')
}

defineExpose({
  formRef,
  validate: () => formRef.value?.validate(),
  resetFields: () => formRef.value?.resetFields()
})
</script>
