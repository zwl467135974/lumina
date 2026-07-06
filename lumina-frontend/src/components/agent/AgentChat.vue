<template>
  <div class="agent-chat">
    <div class="chat-layout">
      <!-- 左：会话列表 -->
      <div class="conversation-sidebar">
        <div class="sidebar-header">
          <span class="sidebar-title">会话</span>
          <el-button size="small" type="primary" plain @click="newConversation" :loading="creating">新建</el-button>
        </div>
        <div class="conversation-list">
          <div v-if="loadingConvs" class="loading-tip">加载中…</div>
          <div
            v-for="c in conversations"
            :key="c.conversationUuid"
            :class="['conv-item', { active: c.conversationUuid === currentConvId }]"
            @click="selectConversation(c.conversationUuid)"
          >
            <div class="conv-title">{{ c.title || '新会话' }}</div>
            <div class="conv-meta">{{ c.messageCount }} 条消息</div>
          </div>
          <el-empty v-if="!loadingConvs && conversations.length === 0" description="暂无会话" :image-size="40" />
        </div>
      </div>

      <!-- 右：对话区 -->
      <div class="chat-main">
        <!-- 调试模式切换 -->
        <div class="chat-toolbar">
          <el-switch v-model="debugMode" size="small" active-text="调试" inactive-text="" />
        </div>

        <div ref="messagesRef" class="chat-messages">
          <!-- 历史消息 -->
          <div v-for="msg in historyMessages" :key="msg.messageId" :class="['msg-item', `msg-${msg.role}`]">
            <div class="msg-avatar">{{ msg.role === 'user' ? '我' : 'AI' }}</div>
            <div class="msg-body">
              <div class="msg-role">{{ msg.role === 'user' ? '我' : '助手' }}</div>
              <div class="msg-content">{{ msg.content }}</div>
              <div v-if="msg.images && msg.images.length > 0" class="msg-images">
                <img v-for="(img, idx) in msg.images" :key="idx" :src="img" class="msg-image-thumb" />
              </div>
              <div v-if="msg.tokenCount" class="msg-meta">
                Token: {{ msg.tokenCount }}<span v-if="msg.durationMs"> · {{ msg.durationMs }}ms</span>
              </div>
            </div>
          </div>

          <!-- 当前流式响应 -->
          <template v-if="streaming || reasoningText || actingText || finalText || errorMsg">
            <el-collapse v-if="reasoningText" class="reasoning-block">
              <el-collapse-item title="🧠 思考过程" name="reasoning">
                <div class="reasoning-text">{{ reasoningText }}</div>
              </el-collapse-item>
            </el-collapse>

            <div v-if="actingText" class="acting-block">
              <div class="acting-title">🔧 工具调用</div>
              <div class="acting-text">{{ actingText }}</div>
            </div>

            <div v-if="finalText || streaming" class="msg-item msg-assistant current-response">
              <div class="msg-avatar">AI</div>
              <div class="msg-body">
                <div class="msg-role">助手</div>
                <div class="msg-content">{{ finalText }}<span v-if="streaming" class="cursor">▋</span></div>
              </div>
            </div>

            <el-alert v-if="errorMsg" :title="errorMsg" type="error" show-icon :closable="false" class="error-block" />
          </template>

          <el-empty
            v-if="historyMessages.length === 0 && !streaming && !finalText && !errorMsg"
            description="输入任务，开始对话"
            :image-size="60"
          />

          <!-- 调试面板（右侧栏） -->
          <div v-if="debugMode" class="debug-sidebar">
            <div class="debug-sidebar-header">
              <span class="debug-sidebar-title">调试面板</span>
              <el-tag v-if="streaming" size="small" type="warning">执行中…</el-tag>
              <el-tag v-else-if="finalText" size="small" type="success">已完成</el-tag>
            </div>

            <div class="debug-sidebar-body">
            <!-- 统计概览 -->
            <div class="debug-section">
              <div class="debug-section-title">执行统计</div>
              <div class="debug-stats-grid">
                <div class="stat-card"><div class="stat-value">{{ debugStats.totalMs }}</div><div class="stat-label">总耗时(ms)</div></div>
                <div class="stat-card"><div class="stat-value">{{ debugStats.finalChars }}</div><div class="stat-label">回复字符</div></div>
                <div class="stat-card"><div class="stat-value">{{ debugStats.estimatedTotalTokens }}</div><div class="stat-label">Token 估算</div></div>
                <div class="stat-card"><div class="stat-value">{{ debugStats.actingEvents }}</div><div class="stat-label">工具调用</div></div>
              </div>
            </div>

            <!-- Token 估算 -->
            <div v-if="debugStats.estimatedTotalTokens > 0" class="debug-section">
              <div class="debug-section-title">Token 与费用估算</div>
              <div class="debug-info-rows">
                <div class="info-row"><span class="info-label">输入(Prompt)</span><span class="info-value">~{{ debugStats.estimatedPromptTokens }} tok</span></div>
                <div class="info-row"><span class="info-label">输出(Completion)</span><span class="info-value">~{{ debugStats.estimatedCompletionTokens }} tok</span></div>
                <div class="info-row"><span class="info-label">合计</span><span class="info-value">{{ debugStats.estimatedTotalTokens }} tok</span></div>
                <div class="info-row"><span class="info-label">估算费用</span><span class="info-value">¥{{ debugStats.estimatedCost }}</span></div>
              </div>
            </div>

            <!-- 执行阶段 -->
            <div v-if="executionPhases.totalMs > 0" class="debug-section">
              <div class="debug-section-title">执行阶段</div>
              <div class="debug-phase-bar">
                <div v-if="executionPhases.firstTokenMs > 0" class="phase-segment phase-init" :style="{ width: phaseWidth('init') }" :title="`首字延迟: ${executionPhases.firstTokenMs}ms`" />
                <div v-if="executionPhases.reasoningMs > 0" class="phase-segment phase-reasoning" :style="{ width: phaseWidth('reasoning') }" :title="`推理: ${executionPhases.reasoningMs}ms`" />
                <div v-if="executionPhases.actingMs > 0" class="phase-segment phase-acting" :style="{ width: phaseWidth('acting') }" :title="`工具: ${executionPhases.actingMs}ms`" />
                <div v-if="executionPhases.generationMs > 0" class="phase-segment phase-generation" :style="{ width: phaseWidth('generation') }" :title="`生成: ${executionPhases.generationMs}ms`" />
              </div>
              <div class="debug-phase-legend">
                <span v-if="executionPhases.firstTokenMs > 0" class="legend-item"><i class="dot dot-init"></i>首字 {{ executionPhases.firstTokenMs }}ms</span>
                <span v-if="executionPhases.reasoningMs > 0" class="legend-item"><i class="dot dot-reasoning"></i>推理 {{ executionPhases.reasoningMs }}ms</span>
                <span v-if="executionPhases.actingMs > 0" class="legend-item"><i class="dot dot-acting"></i>工具 {{ executionPhases.actingMs }}ms</span>
                <span v-if="executionPhases.generationMs > 0" class="legend-item"><i class="dot dot-generation"></i>生成 {{ executionPhases.generationMs }}ms</span>
              </div>
            </div>

            <!-- 模型信息 -->
            <div class="debug-section">
              <div class="debug-section-title">Agent 信息</div>
              <div class="debug-info-rows">
                <div class="info-row"><span class="info-label">Agent ID</span><span class="info-value">{{ agentId }}</span></div>
                <div class="info-row"><span class="info-label">事件总数</span><span class="info-value">{{ eventLog.length }}</span></div>
                <div class="info-row"><span class="info-label">推理事件</span><span class="info-value">{{ debugStats.reasoningEvents }}</span></div>
              </div>
            </div>

            <!-- 推理过程 -->
            <div v-if="reasoningText" class="debug-section">
              <div class="debug-section-title">🧠 推理过程</div>
              <div class="debug-reasoning-text">{{ reasoningText }}</div>
            </div>

            <!-- 工具调用 -->
            <div v-if="actingText" class="debug-section">
              <div class="debug-section-title">🔧 工具调用</div>
              <div class="debug-acting-text">{{ actingText }}</div>
            </div>

            <!-- 事件时间线 -->
            <div v-if="eventLog.length > 0" class="debug-section">
              <div class="debug-section-title">事件时间线</div>
              <div class="debug-timeline">
                <div v-for="(ev, idx) in eventLog" :key="idx" class="debug-event-row">
                  <span class="ev-time">+{{ ev.elapsed }}ms</span>
                  <el-tag size="small" :type="eventTagType(ev.type)">{{ ev.type }}</el-tag>
                  <span class="ev-preview">{{ ev.preview }}</span>
                </div>
              </div>
            </div>

            <el-empty v-if="!streaming && eventLog.length === 0" description="执行后显示调试数据" :image-size="40" />
            </div>
          </div>
        </div>

        <!-- 输入区 -->
        <div class="input-area">
          <!-- 图片预览行 -->
          <div v-if="selectedImages.length > 0" class="image-previews">
            <div v-for="(img, idx) in selectedImages" :key="idx" class="image-preview-item">
              <img :src="img.url" :alt="img.name" />
              <span class="image-remove" @click="removeImage(idx)">×</span>
            </div>
          </div>

          <el-input
            v-model="task"
            type="textarea"
            :rows="2"
            placeholder="输入任务描述，Enter 发送 / Shift+Enter 换行"
            :disabled="isBusy"
            @keydown.enter.exact.prevent="send"
          />
          <input
            ref="fileInputRef"
            type="file"
            accept="image/png,image/jpeg,image/webp"
            multiple
            class="hidden-file-input"
            @change="onImagesSelected"
          />
          <div class="actions">
            <el-button :disabled="isBusy" @click="pickImages">
              <el-icon><Picture /></el-icon>
              <span>{{ uploading ? '上传中…' : '图片' }}</span>
            </el-button>
            <span v-if="selectedImages.length > 0" class="image-hint">{{ selectedImages.length }}/5</span>
            <el-button v-if="!isBusy" type="primary" :disabled="!task.trim()" @click="send">发送</el-button>
            <el-button v-if="streaming" type="danger" @click="abort">中断</el-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Picture } from '@element-plus/icons-vue'
import { streamExecuteAgent, streamExecuteMultimodalAgent, type StreamChunk } from '@/api/modules/agent'
import { uploadFile } from '@/api/modules/file'
import {
  listConversations,
  createConversation,
  listMessages,
  type ConversationVO,
  type MessageVO
} from '@/api/modules/conversation'

const props = defineProps<{ agentId: number }>()

// 会话状态
const conversations = ref<ConversationVO[]>([])
const currentConvId = ref<string | null>(null)
const historyMessages = ref<MessageVO[]>([])
const loadingConvs = ref(false)
const creating = ref(false)

// 流式状态
const task = ref('')
const streaming = ref(false)
const reasoningText = ref('')
const actingText = ref('')
const finalText = ref('')
const errorMsg = ref('')
const messagesRef = ref<HTMLElement | null>(null)

// 图片上传
interface ImagePreview { fileUuid: string; url: string; name: string }
const selectedImages = ref<ImagePreview[]>([])
const fileInputRef = ref<HTMLInputElement | null>(null)
const uploading = ref(false)

const isBusy = computed(() => streaming.value || uploading.value)

// 调试模式
const debugMode = ref(false)
interface DebugEvent { type: string; elapsed: number; preview: string }
const eventLog = ref<DebugEvent[]>([])
let streamStartTime = 0

const debugStats = computed(() => {
  const total = eventLog.value.length > 0
    ? eventLog.value[eventLog.value.length - 1].elapsed
    : 0
  const promptChars = task.value.length
  const completionChars = finalText.value.length + reasoningText.value.length + actingText.value.length
  const estimatedPromptTokens = Math.ceil(promptChars / 2)
  const estimatedCompletionTokens = Math.ceil(completionChars / 2)
  const estimatedTotalTokens = estimatedPromptTokens + estimatedCompletionTokens
  const estimatedCost = ((estimatedPromptTokens * 0.004 + estimatedCompletionTokens * 0.012) / 1000).toFixed(4)
  return {
    totalMs: total,
    finalChars: finalText.value.length,
    actingEvents: eventLog.value.filter(e => e.type.includes('ACTING')).length,
    reasoningEvents: eventLog.value.filter(e => e.type.includes('REASONING')).length,
    estimatedPromptTokens,
    estimatedCompletionTokens,
    estimatedTotalTokens,
    estimatedCost
  }
})

const executionPhases = computed(() => {
  const events = eventLog.value
  if (events.length === 0) {
    return { firstTokenMs: 0, reasoningMs: 0, actingMs: 0, generationMs: 0, totalMs: 0 }
  }
  const totalMs = events[events.length - 1].elapsed

  let firstFinalMs = 0
  for (const ev of events) {
    if (ev.type === 'FINAL' || ev.type === 'AGENT_RESULT') {
      firstFinalMs = ev.elapsed
      break
    }
  }

  let firstReasoningMs = 0
  let lastReasoningMs = 0
  for (const ev of events) {
    if (ev.type.includes('REASONING')) {
      if (firstReasoningMs === 0) firstReasoningMs = ev.elapsed
      lastReasoningMs = ev.elapsed
    }
  }

  let firstActingMs = 0
  let lastActingMs = 0
  for (const ev of events) {
    if (ev.type.includes('ACTING')) {
      if (firstActingMs === 0) firstActingMs = ev.elapsed
      lastActingMs = ev.elapsed
    }
  }

  const reasoningMs = firstReasoningMs > 0 ? lastReasoningMs - firstReasoningMs + 50 : 0
  const actingMs = firstActingMs > 0 ? lastActingMs - firstActingMs + 50 : 0
  const initEnd = Math.min(
    firstReasoningMs || firstFinalMs || totalMs,
    firstActingMs || firstFinalMs || totalMs,
    firstFinalMs || totalMs
  )
  const firstTokenMs = firstFinalMs > 0 ? firstFinalMs : initEnd

  const generationMs = Math.max(0, totalMs - firstTokenMs)

  return { firstTokenMs, reasoningMs, actingMs, generationMs, totalMs }
})

const phaseWidth = (phase: string) => {
  const p = executionPhases.value
  const total = p.totalMs || 1
  let ms = 0
  if (phase === 'init') ms = p.firstTokenMs
  else if (phase === 'reasoning') ms = p.reasoningMs
  else if (phase === 'acting') ms = p.actingMs
  else if (phase === 'generation') ms = p.generationMs
  return `${Math.max((ms / total) * 100, 2)}%`
}

const eventTagType = (type: string) => {
  if (type.includes('REASONING')) return 'info'
  if (type.includes('ACTING')) return 'warning'
  if (type === 'FINAL') return 'success'
  if (type === 'ERROR') return 'danger'
  return ''
}

let controller: AbortController | null = null

const scrollToBottom = () => {
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  })
}

// 加载会话列表
const loadConversations = async () => {
  loadingConvs.value = true
  try {
    const res = await listConversations(props.agentId)
    conversations.value = res.data?.list || []
  } catch (e) {
    // 静默失败（首次可能无会话）
  } finally {
    loadingConvs.value = false
  }
}

// 新建会话
const newConversation = async () => {
  creating.value = true
  try {
    const res = await createConversation(props.agentId)
    currentConvId.value = res.data?.conversationUuid || null
    historyMessages.value = []
    resetStream()
    await loadConversations()
    ElMessage.success('已创建新会话')
  } catch (e: any) {
    ElMessage.error(e.message || '创建会话失败')
  } finally {
    creating.value = false
  }
}

// 选择会话
const selectConversation = async (uuid: string) => {
  if (isBusy.value) return
  currentConvId.value = uuid
  clearImages()
  resetStream()
  await loadHistory(uuid)
}

// 加载历史消息
const loadHistory = async (uuid: string) => {
  try {
    const res = await listMessages(uuid)
    const msgs = res.data?.list || []
    // 解析 fileIds JSON → 构造图片 URL 列表
    historyMessages.value = msgs.map(m => {
      const parsed = { ...m }
      if (m.fileIds && typeof m.fileIds === 'string') {
        try {
          const uuids = JSON.parse(m.fileIds as string) as string[]
          parsed.images = uuids.map((uuid: string) => `/api/v1/files/${uuid}/download`)
        } catch {
          // fileIds 不是合法 JSON，忽略
        }
      }
      return parsed
    })
    scrollToBottom()
  } catch {
    historyMessages.value = []
  }
}

const resetStream = () => {
  reasoningText.value = ''
  actingText.value = ''
  finalText.value = ''
  errorMsg.value = ''
  eventLog.value = []
}

const handleChunk = (chunk: StreamChunk) => {
  const content = chunk.content || ''

  // 调试日志
  if (debugMode.value) {
    eventLog.value.push({
      type: chunk.type,
      elapsed: streamStartTime > 0 ? Date.now() - streamStartTime : 0,
      preview: content.substring(0, 80).replace(/\n/g, ' ')
    })
  }

  switch (chunk.type) {
    case 'REASONING_CHUNK':
    case 'REASONING':
    case 'POST_REASONING':
      reasoningText.value += content
      break
    case 'ACTING_CHUNK':
    case 'ACTING':
    case 'POST_ACTING':
      actingText.value += content
      break
    case 'ERROR':
      errorMsg.value = content || '执行失败'
      break
    default:
      finalText.value += content
      break
  }
  scrollToBottom()
}

// 图片上传
const MAX_IMAGES = 5
const ALLOWED_IMAGE_TYPES = ['image/png', 'image/jpeg', 'image/webp']
const MAX_IMAGE_SIZE = 10 * 1024 * 1024

const pickImages = () => fileInputRef.value?.click()

const onImagesSelected = async (e: Event) => {
  const target = e.target as HTMLInputElement
  if (!target.files) return
  const filesToAdd: File[] = []
  for (const f of Array.from(target.files)) {
    if (selectedImages.value.length + filesToAdd.length >= MAX_IMAGES) {
      ElMessage.warning(`最多 ${MAX_IMAGES} 张图片`)
      break
    }
    if (!ALLOWED_IMAGE_TYPES.includes(f.type)) {
      ElMessage.warning(`${f.name} 格式不支持，仅支持 png、jpg、webp`)
      continue
    }
    if (f.size > MAX_IMAGE_SIZE) {
      ElMessage.warning(`${f.name} 超过 10MB`)
      continue
    }
    filesToAdd.push(f)
  }
  target.value = ''

  // 上传到文件存储服务
  uploading.value = true
  for (const f of filesToAdd) {
    try {
      const res = await uploadFile(f)
      if (res.data) {
        selectedImages.value.push({
          fileUuid: res.data.fileUuid,
          url: `/api/v1/files/${res.data.fileUuid}/download`,
          name: res.data.originalName
        })
      }
    } catch {
      ElMessage.error(`${f.name} 上传失败`)
    }
  }
  uploading.value = false
}

const removeImage = (idx: number) => {
  selectedImages.value.splice(idx, 1)
}

const clearImages = () => {
  selectedImages.value = []
}

const send = async () => {
  const t = task.value.trim()
  if (!t || isBusy.value) return

  // 无会话时自动创建
  if (!currentConvId.value) {
    creating.value = true
    try {
      const res = await createConversation(props.agentId)
      currentConvId.value = res.data?.conversationUuid || null
      await loadConversations()
    } catch (e: any) {
      ElMessage.error(e.message || '创建会话失败')
      creating.value = false
      return
    }
    creating.value = false
  }

  const hasImages = selectedImages.value.length > 0

  // 即时显示用户消息（含图片缩略图）
  historyMessages.value.push({
    messageId: Date.now(),
    role: 'user',
    content: t,
    images: hasImages ? selectedImages.value.map(i => i.url) : undefined,
    tokenCount: 0,
    durationMs: null,
    createTime: new Date().toISOString()
  })

  task.value = ''
  resetStream()

  if (hasImages) {
    await sendMultimodal(t)
  } else {
    sendStream(t)
  }
}

const sendStream = (t: string) => {
  streaming.value = true
  streamStartTime = Date.now()

  controller = streamExecuteAgent(
    props.agentId,
    t,
    {
      onChunk: handleChunk,
      onError: (err) => {
        streaming.value = false
        errorMsg.value = err.message || '流式执行失败'
        ElMessage.error(errorMsg.value)
      },
      onClose: () => {
        streaming.value = false
        if (currentConvId.value) {
          loadHistory(currentConvId.value).then(() => resetStream())
        } else {
          resetStream()
        }
      }
    },
    currentConvId.value || undefined
  )
}

const sendMultimodal = (t: string) => {
  streaming.value = true
  streamStartTime = Date.now()

  const fileUuids = selectedImages.value.map(i => i.fileUuid)

  controller = streamExecuteMultimodalAgent(
    props.agentId,
    t,
    fileUuids,
    {
      onChunk: handleChunk,
      onError: (err) => {
        streaming.value = false
        errorMsg.value = err.message || '流式多模态执行失败'
        ElMessage.error(errorMsg.value)
      },
      onClose: () => {
        streaming.value = false
        clearImages()
        if (currentConvId.value) {
          loadHistory(currentConvId.value).then(() => resetStream())
        } else {
          resetStream()
        }
      }
    },
    currentConvId.value || undefined
  )
}

const abort = () => {
  controller?.abort()
  streaming.value = false
  ElMessage.info('已中断')
}

onMounted(() => {
  loadConversations()
})

defineExpose({ resetStream })
</script>

<style scoped lang="scss">
.agent-chat {
  height: 100%;
  min-height: 480px;
}

.chat-layout {
  display: flex;
  gap: 12px;
  height: 100%;
}

/* 左侧会话列表 */
.conversation-sidebar {
  width: 200px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: var(--el-fill-color-light);
  border-radius: 6px;
  overflow: hidden;
}

.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  .sidebar-title {
    font-size: 14px;
    font-weight: 600;
  }
}

.conversation-list {
  flex: 1;
  overflow-y: auto;
  padding: 6px;
  .loading-tip {
    padding: 12px;
    text-align: center;
    font-size: 13px;
    color: var(--el-text-color-secondary);
  }
}

.conv-item {
  padding: 8px 10px;
  border-radius: 4px;
  cursor: pointer;
  transition: background 0.2s;
  &:hover {
    background: var(--el-fill-color);
  }
  &.active {
    background: var(--el-color-primary-light-9);
  }
  .conv-title {
    font-size: 13px;
    color: var(--el-text-color-primary);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  .conv-meta {
    font-size: 12px;
    color: var(--el-text-color-secondary);
    margin-top: 2px;
  }
}

/* 右侧对话区 */
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
  background: var(--el-fill-color-light);
  border-radius: 6px;
  margin-bottom: 12px;
}

/* 消息气泡 */
.msg-item {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
  .msg-avatar {
    width: 32px;
    height: 32px;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 13px;
    font-weight: 600;
    flex-shrink: 0;
  }
  .msg-body {
    flex: 1;
    min-width: 0;
  }
  .msg-role {
    font-size: 12px;
    color: var(--el-text-color-secondary);
    margin-bottom: 4px;
  }
  .msg-content {
    font-size: 14px;
    line-height: 1.7;
    white-space: pre-wrap;
    word-break: break-word;
  }
  .msg-images {
    display: flex;
    gap: 6px;
    flex-wrap: wrap;
    margin-top: 8px;
  }
  .msg-image-thumb {
    width: 120px;
    height: 120px;
    object-fit: cover;
    border-radius: 6px;
    border: 1px solid var(--el-border-color-lighter);
    cursor: pointer;
  }
  .msg-meta {
    font-size: 12px;
    color: var(--el-text-color-placeholder);
    margin-top: 4px;
  }
}

.msg-user .msg-avatar {
  background: var(--el-color-primary-light-7);
  color: var(--el-color-primary);
}

.msg-assistant .msg-avatar {
  background: var(--el-color-success-light-7);
  color: var(--el-color-success);
}

/* 流式过程块 */
.reasoning-block {
  margin-bottom: 12px;
  :deep(.el-collapse-item__header) {
    font-size: 13px;
    color: var(--el-text-color-secondary);
  }
  .reasoning-text {
    font-size: 13px;
    color: var(--el-text-color-secondary);
    white-space: pre-wrap;
    line-height: 1.6;
  }
}

.acting-block {
  margin-bottom: 12px;
  padding: 8px 12px;
  background: var(--el-color-warning-light-9);
  border-left: 3px solid var(--el-color-warning);
  border-radius: 4px;
  .acting-title {
    font-size: 13px;
    color: var(--el-color-warning);
    margin-bottom: 4px;
  }
  .acting-text {
    font-size: 13px;
    white-space: pre-wrap;
    line-height: 1.6;
  }
}

.current-response {
  animation: fadeIn 0.2s;
}

.cursor {
  display: inline-block;
  margin-left: 2px;
  animation: blink 1s step-end infinite;
  color: var(--el-color-primary);
}

@keyframes blink {
  50% {
    opacity: 0;
  }
}

@keyframes fadeIn {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

.error-block {
  margin-top: 12px;
}

.input-area {
  display: flex;
  flex-direction: column;
  gap: 8px;
  .actions {
    display: flex;
    align-items: center;
    justify-content: flex-end;
    gap: 8px;
    .image-hint {
      font-size: 12px;
      color: var(--el-text-color-secondary);
      margin-right: auto;
    }
  }
}

/* 图片预览 */
.image-previews {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.image-preview-item {
  position: relative;
  width: 72px;
  height: 72px;
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  .image-remove {
    position: absolute;
    top: 2px;
    right: 2px;
    width: 18px;
    height: 18px;
    border-radius: 50%;
    background: rgba(0, 0, 0, 0.55);
    color: #fff;
    font-size: 13px;
    line-height: 18px;
    text-align: center;
    cursor: pointer;
    transition: background 0.2s;
    &:hover {
      background: rgba(0, 0, 0, 0.8);
    }
  }
}

.hidden-file-input {
  display: none;
}

/* 工具栏 */
.chat-toolbar {
  display: flex;
  justify-content: flex-end;
  padding: 4px 0 8px;
}

/* 调试面板 — 右侧栏 */
.debug-sidebar {
  width: 280px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: var(--el-fill-color-light);
  border-radius: 6px;
  overflow: hidden;
  max-height: 100%;

  .debug-sidebar-header {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 10px 12px;
    border-bottom: 1px solid var(--el-border-color-lighter);
    flex-shrink: 0;

    .debug-sidebar-title {
      font-size: 14px;
      font-weight: 600;
    }
  }

  .debug-sidebar-body {
    flex: 1;
    overflow-y: auto;
  }

  .debug-section {
    padding: 10px 12px;
    border-bottom: 1px solid var(--el-border-color-extra-light);

    .debug-section-title {
      font-size: 12px;
      font-weight: 600;
      color: var(--el-text-color-secondary);
      margin-bottom: 8px;
    }
  }

  .debug-stats-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 6px;

    .stat-card {
      text-align: center;
      padding: 8px 4px;
      background: var(--el-fill-color);
      border-radius: 4px;

      .stat-value {
        font-size: 18px;
        font-weight: 700;
        color: var(--el-color-primary);
      }

      .stat-label {
        font-size: 11px;
        color: var(--el-text-color-placeholder);
      }
    }
  }

  .debug-info-rows {
    .info-row {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 3px 0;
      font-size: 12px;

      .info-label {
        color: var(--el-text-color-secondary);
      }

      .info-value {
        color: var(--el-text-color-primary);
        font-weight: 500;
        font-family: monospace;
      }
    }
  }

  .debug-phase-bar {
    display: flex;
    height: 20px;
    border-radius: 4px;
    overflow: hidden;
    margin-bottom: 8px;

    .phase-segment {
      height: 100%;
      transition: width 0.3s;
      min-width: 2%;
    }

    .phase-init { background: var(--el-color-info-light-5); }
    .phase-reasoning { background: var(--el-color-primary-light-5); }
    .phase-acting { background: var(--el-color-warning-light-5); }
    .phase-generation { background: var(--el-color-success-light-5); }
  }

  .debug-phase-legend {
    display: flex;
    flex-wrap: wrap;
    gap: 4px 8px;

    .legend-item {
      display: flex;
      align-items: center;
      gap: 3px;
      font-size: 11px;
      color: var(--el-text-color-secondary);
      font-family: monospace;

      .dot {
        display: inline-block;
        width: 8px;
        height: 8px;
        border-radius: 2px;
      }

      .dot-init { background: var(--el-color-info-light-5); }
      .dot-reasoning { background: var(--el-color-primary-light-5); }
      .dot-acting { background: var(--el-color-warning-light-5); }
      .dot-generation { background: var(--el-color-success-light-5); }
    }
  }

  .debug-reasoning-text,
  .debug-acting-text {
    font-size: 12px;
    line-height: 1.6;
    white-space: pre-wrap;
    word-break: break-word;
    max-height: 150px;
    overflow-y: auto;
    color: var(--el-text-color-regular);
    font-family: monospace;
  }

  .debug-timeline {
    max-height: 250px;
    overflow-y: auto;
    font-family: monospace;
    font-size: 11px;

    .debug-event-row {
      display: flex;
      align-items: center;
      gap: 4px;
      padding: 2px 0;

      .ev-time {
        width: 60px;
        color: var(--el-text-color-placeholder);
        flex-shrink: 0;
      }

      .ev-preview {
        color: var(--el-text-color-secondary);
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
        font-size: 11px;
      }
    }
  }
}
</style>
