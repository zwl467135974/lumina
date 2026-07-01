<template>
  <div class="agent-chat">
    <!-- 渲染区 -->
    <div ref="messagesRef" class="chat-messages">
      <template v-if="hasContent || streaming">
        <!-- 思考过程（推理片段）-->
        <el-collapse v-if="reasoningText" class="reasoning-block">
          <el-collapse-item title="🧠 思考过程" name="reasoning">
            <div class="reasoning-text">{{ reasoningText }}</div>
          </el-collapse-item>
        </el-collapse>

        <!-- 工具调用片段 -->
        <div v-if="actingText" class="acting-block">
          <div class="acting-title">🔧 工具调用</div>
          <div class="acting-text">{{ actingText }}</div>
        </div>

        <!-- 最终结果（打字机）-->
        <div v-if="finalText || streaming" class="final-block">
          <div class="final-text">
            {{ finalText }}<span v-if="streaming" class="cursor">▋</span>
          </div>
        </div>

        <!-- 错误 -->
        <el-alert v-if="errorMsg" :title="errorMsg" type="error" show-icon :closable="false" class="error-block" />
      </template>
      <el-empty v-else description="输入任务，开始对话" :image-size="80" />
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
        <el-button v-if="!streaming && hasContent" @click="reset">清空</el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { streamExecuteAgent, type StreamChunk } from '@/api/modules/agent'

const props = defineProps<{ agentId: number }>()

const task = ref('')
const streaming = ref(false)
const reasoningText = ref('')
const actingText = ref('')
const finalText = ref('')
const errorMsg = ref('')
const messagesRef = ref<HTMLElement | null>(null)

let controller: AbortController | null = null

const hasContent = computed(
  () => !!(reasoningText.value || actingText.value || finalText.value || errorMsg.value)
)

const scrollToBottom = () => {
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  })
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
      // 默认作为最终结果累加（增量片段）
      finalText.value += content
      break
  }
  scrollToBottom()
}

const send = () => {
  const t = task.value.trim()
  if (!t || streaming.value) return

  // 重置上一次内容
  reasoningText.value = ''
  actingText.value = ''
  finalText.value = ''
  errorMsg.value = ''
  streaming.value = true

  controller = streamExecuteAgent(props.agentId, t, {
    onChunk: handleChunk,
    onError: (err) => {
      streaming.value = false
      errorMsg.value = err.message || '流式执行失败'
      ElMessage.error(errorMsg.value)
    },
    onClose: () => {
      streaming.value = false
    }
  })
}

const abort = () => {
  controller?.abort()
  streaming.value = false
  ElMessage.info('已中断')
}

const reset = () => {
  reasoningText.value = ''
  actingText.value = ''
  finalText.value = ''
  errorMsg.value = ''
}

defineExpose({ reset })
</script>

<style scoped lang="scss">
.agent-chat {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 420px;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
  background: var(--el-fill-color-light);
  border-radius: 6px;
  margin-bottom: 12px;
}

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

.final-block {
  padding: 12px;
  background: var(--el-bg-color);
  border-radius: 6px;
  border: 1px solid var(--el-border-color-lighter);
  .final-text {
    white-space: pre-wrap;
    line-height: 1.7;
    font-size: 14px;
  }
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
