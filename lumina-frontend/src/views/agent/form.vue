<template>
  <div class="agent-form-page">
    <page-header :title="pageTitle">
      <el-button @click="handleBack">返回</el-button>
    </page-header>

    <el-card v-loading="loading">
      <el-form :model="formData" :rules="formRules" ref="formRef" label-width="120px">
        <el-divider content-position="left">基本信息</el-divider>

        <el-form-item label="Agent 名称" prop="agentName">
          <el-input v-model="formData.agentName" placeholder="请输入 Agent 名称" />
        </el-form-item>

        <el-form-item label="Agent 类型" prop="agentType">
          <el-select v-model="formData.agentType" placeholder="请选择 Agent 类型" style="width: 100%">
            <el-option label="ReAct" value="ReAct">
              <span>ReAct</span>
              <span style="color: #8492a6; font-size: 12px; margin-left: 10px">
                推理-行动模式，适合复杂任务
              </span>
            </el-option>
            <el-option label="PlanAndExecute" value="PlanAndExecute">
              <span>PlanAndExecute</span>
              <span style="color: #8492a6; font-size: 12px; margin-left: 10px">
                规划-执行模式，适合多步骤任务
              </span>
            </el-option>
          </el-select>
        </el-form-item>

        <el-form-item label="描述" prop="description">
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

        <el-divider content-position="left">提示词配置</el-divider>

        <el-form-item label="系统提示词" prop="systemPrompt">
          <el-input
            v-model="formData.systemPrompt"
            type="textarea"
            :rows="6"
            placeholder="请输入系统提示词，定义 Agent 的角色和行为"
          />
        </el-form-item>

        <el-form-item label="用户提示词模板" prop="userPromptTemplate">
          <el-input
            v-model="formData.userPromptTemplate"
            type="textarea"
            :rows="4"
            placeholder="请输入用户提示词模板，使用 {task} 作为任务占位符"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="handleSubmit" :loading="submitting">
            {{ isEdit ? '更新' : '创建' }}
          </el-button>
          <el-button @click="handleBack">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { createAgent, updateAgent, getAgent } from '@/api/modules/agent'
import type { CreateAgentDTO, UpdateAgentDTO } from '@/types/api'
import PageHeader from '@/components/common/PageHeader.vue'

const route = useRoute()
const router = useRouter()

const isEdit = computed(() => !!route.params.id)
const pageTitle = computed(() => (isEdit.value ? '编辑 Agent' : '创建 Agent'))
const agentId = computed(() => Number(route.params.id))

const loading = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()

// 可用工具列表
const availableTools = [
  { name: 'web_search', label: '网络搜索', description: '搜索互联网获取最新信息' },
  { name: 'web_reader', label: '网页阅读', description: '读取并解析网页内容' },
  { name: 'code_interpreter', label: '代码执行', description: '执行 Python 代码进行计算和分析' },
  { name: 'file_manager', label: '文件管理', description: '读写、管理本地文件' },
  { name: 'database', label: '数据库操作', description: '查询和操作数据库' }
]

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
  tools: [] as string[],
  systemPrompt: `你是一个智能助手，能够使用各种工具来帮助用户完成任务。

请遵循以下原则：
1. 理解用户需求，分析任务目标
2. 选择合适的工具来完成任务
3. 根据工具返回结果，给出准确、有用的回答
4. 如果需要更多信息，主动向用户询问

让我们开始吧！`,
  userPromptTemplate: '任务：{task}\n\n请使用你掌握的工具来帮助完成这个任务。'
})

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
  ],
  systemPrompt: [
    { required: true, message: '请输入系统提示词', trigger: 'blur' }
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

    // 加载提示词
    if ((agent as any).systemPrompt) {
      formData.systemPrompt = (agent as any).systemPrompt
    }
    if ((agent as any).userPromptTemplate) {
      formData.userPromptTemplate = (agent as any).userPromptTemplate
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
          tools: selectedTools.value,
          systemPrompt: formData.systemPrompt,
          userPromptTemplate: formData.userPromptTemplate
        }

        if (isEdit.value) {
          await updateAgent(agentId.value, submitData as UpdateAgentDTO)
          ElMessage.success('更新成功')
        } else {
          await createAgent(submitData as CreateAgentDTO)
          ElMessage.success('创建成功')
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

onMounted(() => {
  loadAgentDetail()
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
      color: #409eff;
    }
  }

  :deep(.el-slider__marks-text) {
    font-size: 12px;
  }
}
</style>
