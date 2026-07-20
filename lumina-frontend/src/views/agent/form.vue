<template>
  <div class="agent-form-page">
    <page-header :title="pageTitle">
      <el-button @click="handleBack">{{ t('agent.form.back') }}</el-button>
    </page-header>

    <el-card v-loading="loading">
      <el-form :model="formData" :rules="formRules" ref="formRef" label-width="120px">
        <el-divider content-position="left">{{ t('agent.form.basicInfo') }}</el-divider>

        <el-form-item :label="t('agent.name')" prop="agentName">
          <el-input v-model="formData.agentName" :placeholder="t('agent.form.namePlaceholder')" />
        </el-form-item>

        <el-form-item :label="t('agent.type')" prop="agentType">
          <el-select v-model="formData.agentType" :placeholder="t('agent.form.typePlaceholder')" style="width: 100%">
            <el-option label="ReAct" value="ReAct">
              <span>ReAct</span>
              <span style="color: var(--lumina-text-muted); font-size: 12px; margin-left: 10px">
                {{ t('agent.form.typeReactDesc') }}
              </span>
            </el-option>
            <el-option label="Simple" value="simple">
              <span>Simple</span>
              <span style="color: var(--lumina-text-muted); font-size: 12px; margin-left: 10px">
                {{ t('agent.form.typeSimpleDesc') }}
              </span>
            </el-option>
            <el-option label="Tool" value="tool">
              <span>Tool</span>
              <span style="color: var(--lumina-text-muted); font-size: 12px; margin-left: 10px">
                {{ t('agent.form.typeToolDesc') }}
              </span>
            </el-option>
            <el-option label="PlanAndExecute" value="PlanAndExecute">
              <span>PlanAndExecute</span>
              <span style="color: var(--lumina-text-muted); font-size: 12px; margin-left: 10px">
                {{ t('agent.form.typePlanDesc') }}
              </span>
            </el-option>
          </el-select>
        </el-form-item>

        <el-form-item :label="t('agent.runtimePrompt')">
          <div class="prompt-preview" v-loading="promptLoading">
            <template v-if="currentPrompt">
              <div class="prompt-preview__header">
                <el-tag type="success" size="small">{{ t('agent.promptActive') }}</el-tag>
                <span>{{ currentPrompt.name }} v{{ currentPrompt.version }}</span>
              </div>
              <div class="prompt-preview__desc">
                {{ currentPrompt.description || t('agent.form.noDescription') }}
              </div>
              <el-input :model-value="currentPrompt.content" type="textarea" :rows="4" readonly />
            </template>
            <template v-else>
              <div class="prompt-preview__header">
                <el-tag type="info" size="small">{{ t('agent.promptFallback') }}</el-tag>
                <span>prompts/{{ promptName }}.txt</span>
              </div>
              <div class="prompt-preview__desc">
                {{ t('agent.form.promptNotFound', { name: promptName }) }}
              </div>
            </template>
          </div>
        </el-form-item>

        <el-form-item :label="t('agent.description')" prop="description">
          <el-input
            v-model="formData.description"
            type="textarea"
            :rows="3"
            :placeholder="t('agent.form.descriptionPlaceholder')"
          />
        </el-form-item>

        <el-divider content-position="left">{{ t('agent.form.llmConfig') }}</el-divider>

        <el-form-item :label="t('agent.form.provider')" prop="llmConfig.provider">
          <el-select
            v-model="formData.llmConfig.provider"
            :placeholder="t('agent.form.providerPlaceholder')"
            style="width: 100%"
          >
            <el-option :label="t('agent.form.providerOpenai')" value="openai" />
            <el-option :label="t('agent.form.providerAnthropic')" value="anthropic" />
            <el-option :label="t('agent.form.providerAzure')" value="azure" />
            <el-option :label="t('agent.form.providerQwen')" value="qwen" />
            <el-option :label="t('agent.form.providerZhipu')" value="zhipu" />
          </el-select>
        </el-form-item>

        <el-form-item :label="t('agent.form.modelName')" prop="llmConfig.modelName">
          <el-input
            v-model="formData.llmConfig.modelName"
            :placeholder="t('agent.form.modelNamePlaceholder')"
          />
        </el-form-item>

        <el-form-item :label="t('agent.form.apiKey')" prop="llmConfig.apiKey">
          <el-input
            v-model="formData.llmConfig.apiKey"
            type="password"
            :placeholder="t('agent.form.apiKeyPlaceholder')"
            show-password
          />
        </el-form-item>

        <el-form-item :label="t('agent.form.baseUrl')" prop="llmConfig.baseUrl">
          <el-input
            v-model="formData.llmConfig.baseUrl"
            :placeholder="t('agent.form.baseUrlPlaceholder')"
          />
        </el-form-item>

        <el-form-item :label="t('agent.form.temperature')" prop="llmConfig.temperature">
          <el-slider
            v-model="formData.llmConfig.temperature"
            :min="0"
            :max="2"
            :step="0.1"
            :marks="{ 0: t('agent.form.tempPrecise'), 1: t('agent.form.tempBalanced'), 2: t('agent.form.tempCreative') }"
            show-stops
          />
        </el-form-item>

        <el-form-item :label="t('agent.form.maxTokens')" prop="llmConfig.maxTokens">
          <el-input-number
            v-model="formData.llmConfig.maxTokens"
            :min="1"
            :max="128000"
            :step="1000"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item :label="t('agent.form.topP')">
          <el-slider
            v-model="formData.llmConfig.topP"
            :min="0"
            :max="1"
            :step="0.05"
            show-input
            :show-input-controls="false"
          />
        </el-form-item>

        <el-form-item :label="t('agent.form.frequencyPenalty')">
          <el-slider
            v-model="formData.llmConfig.frequencyPenalty"
            :min="-2"
            :max="2"
            :step="0.1"
            show-input
            :show-input-controls="false"
          />
        </el-form-item>

        <el-form-item :label="t('agent.form.presencePenalty')">
          <el-slider
            v-model="formData.llmConfig.presencePenalty"
            :min="-2"
            :max="2"
            :step="0.1"
            show-input
            :show-input-controls="false"
          />
        </el-form-item>

        <!-- Provider Failover 配置 -->
        <el-divider content-position="left">{{ t('agent.form.failoverConfig') }}</el-divider>
        <div v-for="(fp, i) in formData.llmConfig.fallbackProviders" :key="i" class="fallback-row">
          <el-input v-model="fp.modelType" :placeholder="t('system.model.providerPlaceholder')" style="width: 140px" />
          <el-input v-model="fp.modelName" :placeholder="t('system.model.defaultModel')" style="width: 160px" />
          <el-input v-model="fp.apiKey" placeholder="API Key" style="width: 200px" type="password" show-password />
          <el-input v-model="fp.baseUrl" :placeholder="t('system.model.baseUrlPlaceholder')" style="flex: 1" />
          <el-button link type="danger" @click="formData.llmConfig.fallbackProviders.splice(i, 1)">
            <el-icon><Delete /></el-icon>
          </el-button>
        </div>
        <el-button text @click="addFallbackProvider">
          <el-icon><Plus /></el-icon> {{ t('agent.form.addFallback') }}
        </el-button>

        <el-divider content-position="left">{{ t('agent.form.toolConfig') }}</el-divider>

        <el-form-item :label="t('agent.form.availableTools')">
          <el-checkbox-group v-model="selectedTools">
            <el-checkbox
              v-for="tool in availableTools"
              :key="tool.name"
              :label="tool.name"
              :value="tool.name"
            >
              <div style="margin-left: 8px">
                <div>{{ tool.label }}</div>
                <div style="color: var(--lumina-text-muted); font-size: 12px">{{ tool.description }}</div>
              </div>
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>

        <el-divider content-position="left">{{ t('agent.form.knowledgeMount') }}</el-divider>

        <el-form-item :label="t('agent.form.linkedKnowledge')">
          <el-select
            v-model="selectedKbIds"
            multiple
            filterable
            :placeholder="t('common.pleaseSelect')"
            style="width: 100%"
          >
            <el-option
              v-for="kb in availableKbs"
              :key="kb.id"
              :label="kb.name"
              :value="kb.id"
            />
          </el-select>
          <div v-if="availableKbs.length === 0" class="form-tip">
            {{ t('agent.form.noKnowledge') }}
          </div>
        </el-form-item>

        <el-divider content-position="left">{{ t('agent.form.advancedConfig') }}</el-divider>

        <el-form-item :label="t('agent.form.rateLimit')">
          <el-input-number
            v-model="formData.rateLimit"
            :min="0"
            :max="10000"
            :step="10"
            style="width: 100%"
          />
          <div class="form-tip">{{ t('agent.form.rateLimitTip') }}</div>
        </el-form-item>

        <el-form-item :label="t('agent.form.maxConcurrent')">
          <el-input-number
            v-model="formData.maxConcurrent"
            :min="0"
            :max="100"
            style="width: 100%"
          />
          <div class="form-tip">{{ t('agent.form.maxConcurrentTip') }}</div>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="handleSubmit" :loading="submitting">
            {{ isEdit ? t('agent.form.updateBtn') : t('agent.form.createBtn') }}
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
import { Delete, Plus } from '@element-plus/icons-vue'
import { createAgent, updateAgent, getAgent } from '@/api/modules/agent'
import { getActivePrompt, type PromptVO } from '@/api/modules/prompt'
import { getTools, type ToolDefinitionVO } from '@/api/modules/tools'
import { listKnowledgeBases } from '@/api/modules/knowledge-base'
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
    maxTokens: 4096,
    topP: 0.9,
    frequencyPenalty: 0,
    presencePenalty: 0,
    fallbackProviders: [] as Array<{ modelType: string; modelName: string; apiKey: string; baseUrl: string }>
  },
  tools: [] as string[],
  rateLimit: 0,
  maxConcurrent: 0
})

const availableKbs = ref<Array<{ id: number; name: string }>>([])
const selectedKbIds = ref<number[]>([])

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
    console.error(t('agent.form.loadToolsFail'), error)
    availableTools.value = []
  }
}

const formRules: FormRules = {
  agentName: [
    { required: true, message: () => t('agent.form.nameRequired'), trigger: 'blur' }
  ],
  agentType: [
    { required: true, message: () => t('agent.form.typeRequired'), trigger: 'change' }
  ],
  'llmConfig.provider': [
    { required: true, message: () => t('agent.form.providerRequired'), trigger: 'change' }
  ],
  'llmConfig.modelName': [
    { required: true, message: () => t('agent.form.modelNameRequired'), trigger: 'blur' }
  ],
  'llmConfig.apiKey': [
    { required: true, message: () => t('agent.form.apiKeyRequired'), trigger: 'blur' }
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

    // 加载 LLM 配置
    if (agent.llmConfig) {
      Object.assign(formData.llmConfig, agent.llmConfig)
    }

    // 加载工具列表
    if (agent.tools) {
      selectedTools.value = agent.tools
      formData.tools = agent.tools
    }

  } catch (error) {
    console.error(t('agent.form.loadDetailFail'), error)
    ElMessage.error(t('agent.form.loadDetailFail'))
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
        console.error(isEdit.value ? t('agent.form.updateFail') : t('agent.form.createFail'), error)
        ElMessage.error(isEdit.value ? t('agent.form.updateFail') : t('agent.form.createFail'))
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

function addFallbackProvider() {
  formData.llmConfig.fallbackProviders.push({
    modelType: '', modelName: '', apiKey: '', baseUrl: ''
  })
}

const loadKnowledgeBases = async () => {
  try {
    const res = await listKnowledgeBases()
    availableKbs.value = (res.data || []).map((kb: any) => ({ id: kb.id, name: kb.name }))
  } catch {
    availableKbs.value = []
  }
}

onMounted(async () => {
  await loadTools()
  await loadKnowledgeBases()
  await loadAgentDetail()
  await loadActivePrompt()
})

watch(() => formData.agentType, () => {
  loadActivePrompt()
})
</script>

<style scoped lang="scss">
.agent-form-page {
  .form-tip {
    font-size: 12px;
    color: var(--lumina-text-muted);
    margin-top: 4px;
  }
  .fallback-row {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 8px;
  }
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
    border: 1px solid var(--lumina-border);
    border-radius: var(--lumina-radius-sm);
    background: var(--lumina-bg-input);

    &__header {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 8px;
      font-weight: 500;
    }

    &__desc {
      margin-bottom: 10px;
      color: var(--lumina-text-secondary);
      font-size: 13px;
    }
  }
}

@media (max-width: 768px) {
  :deep(.el-col) {
    max-width: 100%;
    flex: 0 0 100%;
  }
  :deep(.el-form--inline .el-form-item) {
    display: block;
    margin-right: 0;
  }
  :deep(.el-table) {
    font-size: 12px;
  }
}
</style>
