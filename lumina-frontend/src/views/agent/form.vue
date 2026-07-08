<template>
  <div class="agent-form-page">
    <page-header :title="pageTitle">
      <el-button @click="handleBack">返回</el-button>
    </page-header>

    <el-card v-loading="loading">
      <el-form :model="formData" :rules="formRules" ref="formRef" label-width="120px">
        <el-divider content-position="left">基本信息</el-divider>

        <el-form-item :label="t('agent.name')" prop="agentName">
          <el-input v-model="formData.agentName" placeholder="请输入 Agent 名称" />
        </el-form-item>

        <el-form-item :label="t('agent.type')" prop="agentType">
          <el-select v-model="formData.agentType" placeholder="请选择 Agent 类型" style="width: 100%">
            <el-option label="ReAct" value="ReAct">
              <span>ReAct</span>
              <span style="color: #8492a6; font-size: 12px; margin-left: 10px">
                推理-行动模式，适合复杂任务
              </span>
            </el-option>
            <el-option label="Simple" value="simple">
              <span>Simple</span>
              <span style="color: #8492a6; font-size: 12px; margin-left: 10px">
                简单对话模式，匹配 simple Prompt
              </span>
            </el-option>
            <el-option label="Tool" value="tool">
              <span>Tool</span>
              <span style="color: #8492a6; font-size: 12px; margin-left: 10px">
                工具调用模式，匹配 tool Prompt
              </span>
            </el-option>
            <el-option label="PlanAndExecute" value="PlanAndExecute">
              <span>PlanAndExecute</span>
              <span style="color: #8492a6; font-size: 12px; margin-left: 10px">
                规划-执行模式；未配置同名 Prompt 时使用内置回退
              </span>
            </el-option>
          </el-select>
        </el-form-item>

        <el-form-item :label="t('agent.runtimePrompt')">
          <div class="prompt-preview" v-loading="promptLoading">
            <template v-if="currentPrompt">
              <div class="prompt-preview__header">
                <el-tag type="success" size="small">DB 激活</el-tag>
                <span>{{ currentPrompt.name }} v{{ currentPrompt.version }}</span>
              </div>
              <div class="prompt-preview__desc">
                {{ currentPrompt.description || '无描述' }}
              </div>
              <el-input :model-value="currentPrompt.content" type="textarea" :rows="4" readonly />
            </template>
            <template v-else>
              <div class="prompt-preview__header">
                <el-tag type="info" size="small">内置回退</el-tag>
                <span>prompts/{{ promptName }}.txt</span>
              </div>
              <div class="prompt-preview__desc">
                Prompt 管理中没有名称为 {{ promptName }} 的激活版本，执行时将使用 agent-core 内置 Prompt。
              </div>
            </template>
          </div>
        </el-form-item>

        <el-form-item :label="t('agent.description')" prop="description">
          <el-input
            v-model="formData.description"
            type="textarea"
            :rows="3"
            placeholder="请输入 Agent 描述"
          />
        </el-form-item>

        <el-divider content-position="left">LLM 配置</el-divider>

        <el-form-item label="模型提供商" prop="llmConfig.provider">
          <el-select
            v-model="formData.llmConfig.provider"
            placeholder="请选择模型提供商"
            style="width: 100%"
          >
            <el-option label="OpenAI" value="openai" />
            <el-option label="Anthropic" value="anthropic" />
            <el-option label="Azure OpenAI" value="azure" />
            <el-option label="通义千问" value="qwen" />
            <el-option label="智谱 AI" value="zhipu" />
          </el-select>
        </el-form-item>

        <el-form-item label="模型名称" prop="llmConfig.modelName">
          <el-input
            v-model="formData.llmConfig.modelName"
            placeholder="例如：gpt-4、claude-3-sonnet"
          />
        </el-form-item>

        <el-form-item label="API Key" prop="llmConfig.apiKey">
          <el-input
            v-model="formData.llmConfig.apiKey"
            type="password"
            placeholder="请输入 API Key"
            show-password
          />
        </el-form-item>

        <el-form-item label="Base URL" prop="llmConfig.baseUrl">
          <el-input
            v-model="formData.llmConfig.baseUrl"
            placeholder="请输入 Base URL（可选）"
          />
        </el-form-item>

        <el-form-item label="温度" prop="llmConfig.temperature">
          <el-slider
            v-model="formData.llmConfig.temperature"
            :min="0"
            :max="2"
            :step="0.1"
            :marks="{ 0: '精确', 1: '平衡', 2: '创造性' }"
            show-stops
          />
        </el-form-item>

        <el-form-item label="最大 Token 数" prop="llmConfig.maxTokens">
          <el-input-number
            v-model="formData.llmConfig.maxTokens"
            :min="1"
            :max="128000"
            :step="1000"
            style="width: 100%"
          />
        </el-form-item>

        <el-divider content-position="left">工具配置</el-divider>

        <el-form-item label="可用工具">
          <el-checkbox-group v-model="selectedTools">
            <el-checkbox
              v-for="tool in availableTools"
              :key="tool.name"
              :label="tool.name"
              :value="tool.name"
            >
              <div style="margin-left: 8px">
                <div>{{ tool.label }}</div>
                <div style="color: #8492a6; font-size: 12px">{{ tool.description }}</div>
              </div>
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="handleSubmit" :loading="submitting">
            {{ isEdit ? '更新' : '创建' }}
          </el-button>
          <el-button @click="handleBack">{{ t('common.cancel') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { createAgent, updateAgent, getAgent } from '@/api/modules/agent'
import { getActivePrompt, type PromptVO } from '@/api/modules/prompt'
import { getTools, type ToolDefinitionVO } from '@/api/modules/tools'
import type { CreateAgentDTO, UpdateAgentDTO } from '@/types/api'
import PageHeader from '@/components/common/PageHeader.vue'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()

const isEdit = computed(() => !!route.params.id)
const pageTitle = computed(() => (isEdit.value ? t('agent.edit') : t('agent.create')))
const agentId = computed(() => Number(route.params.id))

const loading = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const promptLoading = ref(false)
const currentPrompt = ref<PromptVO | null>(null)

const availableTools = ref<ToolDefinitionVO[]>([])

const selectedTools = ref<string[]>([])

// 表单数据
const formData = reactive({
  agentName: '',
  agentType: 'ReAct',
  description: '',
  llmConfig: {
    provider: 'openai',
    modelName: 'gpt-4',
    apiKey: '',
    baseUrl: '',
    temperature: 0.7,
    maxTokens: 4096
  },
  tools: [] as string[]
})

const promptName = computed(() => formData.agentType.toLowerCase())

const loadActivePrompt = async () => {
  if (!promptName.value) {
    currentPrompt.value = null
    return
  }
  promptLoading.value = true
  try {
    const res = await getActivePrompt(promptName.value)
    currentPrompt.value = res.data || null
  } catch {
    currentPrompt.value = null
  } finally {
    promptLoading.value = false
  }
}

const loadTools = async () => {
  try {
    const res = await getTools()
    availableTools.value = res.data || []
  } catch (error) {
    console.error('加载工具列表失败:', error)
    availableTools.value = []
  }
}

const formRules: FormRules = {
  agentName: [
    { required: true, message: '请输入 Agent 名称', trigger: 'blur' }
  ],
  agentType: [
    { required: true, message: '请选择 Agent 类型', trigger: 'change' }
  ],
  'llmConfig.provider': [
    { required: true, message: '请选择模型提供商', trigger: 'change' }
  ],
  'llmConfig.modelName': [
    { required: true, message: '请输入模型名称', trigger: 'blur' }
  ],
  'llmConfig.apiKey': [
    { required: true, message: '请输入 API Key', trigger: 'blur' }
  ]
}

// 加载 Agent 详情
const loadAgentDetail = async () => {
  if (!isEdit.value) return

  loading.value = true
  try {
    const res = await getAgent(agentId.value)
    const agent = res.data

    formData.agentName = agent.agentName
    formData.agentType = agent.agentType
    formData.description = agent.description || ''

    // 加载 LLM 配置（假设后端返回包含这些字段）
    if ((agent as any).llmConfig) {
      Object.assign(formData.llmConfig, (agent as any).llmConfig)
    }

    // 加载工具列表
    if ((agent as any).tools) {
      selectedTools.value = (agent as any).tools
      formData.tools = (agent as any).tools
    }

  } catch (error) {
    console.error('加载 Agent 详情失败:', error)
    ElMessage.error('加载 Agent 详情失败')
  } finally {
    loading.value = false
  }
}

// 提交表单
const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (valid) {
      submitting.value = true
      try {
        // 准备提交数据
        const submitData: any = {
          agentName: formData.agentName,
          agentType: formData.agentType,
          description: formData.description,
          llmConfig: formData.llmConfig,
          tools: selectedTools.value
        }

        if (isEdit.value) {
          await updateAgent(agentId.value, submitData as UpdateAgentDTO)
          ElMessage.success(t('common.updateSuccess'))
        } else {
          await createAgent(submitData as CreateAgentDTO)
          ElMessage.success(t('common.createSuccess'))
        }

        // 跳转回列表页
        router.push('/agent')
      } catch (error) {
        console.error('操作失败:', error)
        ElMessage.error(isEdit.value ? '更新失败' : '创建失败')
      } finally {
        submitting.value = false
      }
    }
  })
}

// 返回列表页
const handleBack = () => {
  router.push('/agent')
}

onMounted(async () => {
  await loadTools()
  await loadAgentDetail()
  await loadActivePrompt()
})

watch(() => formData.agentType, () => {
  loadActivePrompt()
})
</script>

<style scoped lang="scss">
.agent-form-page {
  max-width: 1000px;
  margin: 0 auto;

  :deep(.el-checkbox) {
    display: flex;
    align-items: flex-start;
    margin-bottom: 12px;
    white-space: normal;

    .el-checkbox__label {
      width: 100%;
    }
  }

  :deep(.el-divider) {
    margin: 30px 0 20px;

    .el-divider__text {
      font-weight: 500;
      color: var(--lumina-primary);
    }
  }

  :deep(.el-slider__marks-text) {
    font-size: 12px;
  }

  .prompt-preview {
    width: 100%;
    padding: 12px;
    border: 1px solid #e4e7ed;
    border-radius: 6px;
    background: #fafafa;

    &__header {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 8px;
      font-weight: 500;
    }

    &__desc {
      margin-bottom: 10px;
      color: #606266;
      font-size: 13px;
    }
  }
}
</style>
