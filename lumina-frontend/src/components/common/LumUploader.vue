<template>
  <div class="lum-uploader">
    <!-- 已选文件预览 -->
    <div v-if="items.length > 0" class="preview-area">
      <div v-for="(item, idx) in items" :key="idx" class="preview-item">
        <img v-if="item.isImage" :src="item.url" class="preview-thumb" />
        <div v-else class="preview-file">
          <el-icon :size="24"><Document /></el-icon>
          <span class="file-name">{{ item.name }}</span>
        </div>
        <span class="preview-remove" @click="removeItem(idx)">×</span>
      </div>
    </div>

    <!-- 隐藏的文件输入 -->
    <input
      ref="inputRef"
      type="file"
      :accept="accept"
      :multiple="maxCount > 1"
      class="hidden-input"
      @change="onFilesSelected"
    />
    <el-button :disabled="disabled || uploading" size="small" @click="pickFiles">
      <el-icon><Paperclip /></el-icon>
      <span>{{ uploading ? '上传中…' : buttonText }}</span>
    </el-button>
    <span v-if="items.length > 0" class="count-hint">{{ items.length }}/{{ maxCount }}</span>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Document, Paperclip } from '@element-plus/icons-vue'
import { uploadFile } from '@/api/modules/file'

interface UploadedItem {
  fileUuid: string
  url: string
  name: string
  isImage: boolean
}

const props = withDefaults(
  defineProps<{
    /** 接受的文件类型（MIME 或扩展名，逗号分隔） */
    accept?: string
    /** 最大文件数 */
    maxCount?: number
    /** 单文件最大字节 */
    maxSize?: number
    /** 文件业务类型 */
    bizType?: string
    /** 按钮文字 */
    buttonText?: string
    /** 禁用 */
    disabled?: boolean
  }>(),
  {
    accept: 'image/png,image/jpeg,image/webp',
    maxCount: 5,
    maxSize: 10 * 1024 * 1024,
    bizType: 'chat_image',
    buttonText: '附件',
    disabled: false
  }
)

const emit = defineEmits<{
  /** 文件列表变化时触发，返回已上传的 fileUuid 列表 */
  (e: 'change', fileUuids: string[]): void
}>()

const items = ref<UploadedItem[]>([])
const inputRef = ref<HTMLInputElement | null>(null)
const uploading = ref(false)

const allowedTypes = computed(() =>
  props.accept.split(',').map((t) => t.trim().toLowerCase())
)

function isImageType(type: string, name: string): boolean {
  const imageExts = ['.png', '.jpg', '.jpeg', '.webp', '.gif', '.bmp']
  if (type.startsWith('image/')) return true
  return imageExts.some((ext) => name.toLowerCase().endsWith(ext))
}

function isAllowed(file: File): boolean {
  const fileName = file.name.toLowerCase()
  const fileType = file.type.toLowerCase()
  // 如果 accept 为 * 或空，允许所有
  if (props.accept === '*' || !props.accept) return true
  return allowedTypes.value.some((allowed) => {
    if (allowed.startsWith('.')) {
      // 扩展名匹配
      return fileName.endsWith(allowed)
    }
    if (allowed.endsWith('/*')) {
      // 通配 MIME 如 image/*
      const prefix = allowed.slice(0, -2)
      return fileType.startsWith(prefix)
    }
    // 精确 MIME 匹配
    return fileType === allowed
  })
}

function pickFiles() {
  inputRef.value?.click()
}

async function onFilesSelected(e: Event) {
  const target = e.target as HTMLInputElement
  if (!target.files) return
  const filesToAdd: File[] = []
  for (const f of Array.from(target.files)) {
    if (items.value.length + filesToAdd.length >= props.maxCount) {
      ElMessage.warning(`最多 ${props.maxCount} 个文件`)
      break
    }
    if (!isAllowed(f)) {
      ElMessage.warning(`${f.name} 格式不支持`)
      continue
    }
    if (f.size > props.maxSize) {
      ElMessage.warning(`${f.name} 超过 ${Math.round(props.maxSize / 1024 / 1024)}MB`)
      continue
    }
    filesToAdd.push(f)
  }
  target.value = ''

  uploading.value = true
  for (const f of filesToAdd) {
    try {
      const res = await uploadFile(f, props.bizType)
      if (res.data) {
        const isImg = isImageType(f.type, f.name)
        items.value.push({
          fileUuid: res.data.fileUuid,
          url: `/api/v1/files/${res.data.fileUuid}/download`,
          name: res.data.originalName,
          isImage: isImg
        })
      }
    } catch {
      ElMessage.error(`${f.name} 上传失败`)
    }
  }
  uploading.value = false
  emitChange()
}

function removeItem(idx: number) {
  items.value.splice(idx, 1)
  emitChange()
}

function emitChange() {
  emit('change', items.value.map((i) => i.fileUuid))
}

/** 清空已选文件（供父组件调用） */
function clear() {
  items.value = []
  emitChange()
}

defineExpose({ clear, items })
</script>

<style scoped>
.lum-uploader {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
.preview-area {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-right: 4px;
}
.preview-item {
  position: relative;
  width: 56px;
  height: 56px;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  overflow: hidden;
}
.preview-thumb {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.preview-file {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  background: var(--el-fill-color-light);
}
.file-name {
  font-size: 10px;
  max-width: 52px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--el-text-color-secondary);
}
.preview-remove {
  position: absolute;
  top: -2px;
  right: -2px;
  width: 18px;
  height: 18px;
  background: rgba(0, 0, 0, 0.6);
  color: #fff;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  font-size: 14px;
  line-height: 1;
}
.preview-remove:hover {
  background: rgba(0, 0, 0, 0.8);
}
.hidden-input {
  display: none;
}
.count-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
