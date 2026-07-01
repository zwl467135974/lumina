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
        <div ref="messagesRef" class="chat-messages">
          <!-- 历史消息 -->
          <div v-for="msg in historyMessages" :key="msg.messageId" :class="['msg-item', `msg-${msg.role}`]">
            <div class="msg-avatar">{{ msg.role === 'user' ? '我' : 'AI' }}</div>
            <div class="msg-body">
              <div class="msg-role">{{ msg.role === 'user' ? '我' : '助手' }}</div>
              <div class="msg-content">{{ msg.content }}</div>
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
        </div>

        <!-- 输入区 -->
        <div class="input-area">
          <el-input
            v-model="task"
            type="textarea"
            :rows="2"
            placeholder="输入任务描述，Enter 发送 / Shift+Enter 换行"
            :disabled="streaming"
            @keydown.enter.exact.prevent="send"
          />
          <div class="actions">
            <el-button v-if="!streaming" type="primary" :disabled="!task.trim()" @click="send">发送</el-button>
            <el-button v-else type="danger" @click="abort">中断</el-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { streamExecuteAgent, type StreamChunk } from '@/api/modules/agent'
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
  if (streaming.value) return
  currentConvId.value = uuid
  resetStream()
  await loadHistory(uuid)
}

// 加载历史消息
const loadHistory = async (uuid: string) => {
  try {
    const res = await listMessages(uuid)
    historyMessages.value = res.data?.list || []
    scrollToBottom()
  } catch (e) {
    historyMessages.value = []
  }
}

const resetStream = () => {
  reasoningText.value = ''
  actingText.value = ''
  finalText.value = ''
  errorMsg.value = ''
}

const handleChunk = (chunk: StreamChunk) => {
  const content = chunk.content || ''
  switch (chunk.type) {
    case 'REASONING_CHUNK':
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
    case 'FINAL':
    default:
      finalText.value += content
      break
  }
  scrollToBottom()
}

const send = async () => {
  const t = task.value.trim()
  if (!t || streaming.value) return

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

  // 即时显示用户消息
  historyMessages.value.push({
    messageId: Date.now(),
    role: 'user',
    content: t,
    tokenCount: 0,
    durationMs: null,
    createTime: new Date().toISOString()
  })

  task.value = ''
  resetStream()
  streaming.value = true

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
        // 刷新历史获取助手回复（含 token 用量），并清空流式临时态
        resetStream()
        if (currentConvId.value) {
          loadHistory(currentConvId.value)
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
    justify-content: flex-end;
    gap: 8px;
  }
}
</style>
