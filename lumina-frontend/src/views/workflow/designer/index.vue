<template>
  <div class="designer-page">
    <PageHeader :title="title" :description="description">
      <template #actions>
        <el-button @click="$router.back()">返回</el-button>
        <el-button-group>
          <el-button :type="mode === 'visual' ? 'primary' : ''" @click="mode = 'visual'">可视化</el-button>
          <el-button :type="mode === 'yaml' ? 'primary' : ''" @click="switchToYaml">YAML</el-button>
        </el-button-group>
        <el-button type="success" @click="handleSave" :loading="saving">{{ t('common.save') }}</el-button>
      </template>
    </PageHeader>

    <div class="designer-body">
      <!-- 左侧：节点面板 -->
      <div class="palette-panel">
        <div class="palette-title">节点类型</div>
        <div class="palette-list">
          <div
            v-for="nt in PALETTE_NODES"
            :key="nt.type"
            class="palette-item"
            draggable="true"
            @dragstart="onDragStart($event, nt)"
            :style="{ borderColor: nt.color, background: nt.bgColor }"
          >
            <span class="palette-icon">{{ nt.icon }}</span>
            <span class="palette-label">{{ nt.label }}</span>
          </div>
        </div>
        <div class="palette-hint">拖拽节点到画布</div>
      </div>

      <!-- 中间：画布 / YAML 编辑 -->
      <div class="canvas-area" v-loading="loading">
        <div v-if="mode === 'visual'" class="flow-container" @drop="onDrop" @dragover.prevent>
          <VueFlow
            v-model:nodes="nodes"
            v-model:edges="edges"
            :node-types="nodeTypes"
            :default-viewport="{ zoom: 1 }"
            fit-view-on-init
            @node-click="onNodeClick"
            @pane-click="onPaneClick"
            @connect="onConnect"
          >
            <Background pattern-color="#dcdfe6" :gap="20" />
            <Controls />
            <MiniMap />
          </VueFlow>

          <div v-if="nodes.length === 0" class="canvas-empty">
            <el-empty description="拖拽左侧节点到画布开始设计" :image-size="80" />
          </div>
        </div>

        <div v-else class="yaml-editor-area">
          <el-input
            v-model="yamlText"
            type="textarea"
            :rows="30"
            placeholder="输入工作流 YAML..."
            class="yaml-textarea"
          />
          <div class="yaml-actions">
            <el-button size="small" @click="syncYamlToGraph">从 YAML 同步到画布</el-button>
          </div>
        </div>
      </div>

      <!-- 右侧：属性面板 -->
      <div class="property-panel">
        <template v-if="selectedNode">
          <div class="property-title">
            <span>{{ NODE_TYPES[selectedNode.data.nodeType]?.icon }} {{ selectedNode.data.label }}</span>
          </div>
          <el-form label-width="80px" size="small">
            <el-form-item label="节点 ID">
              <el-input v-model="selectedNode.id" disabled />
            </el-form-item>
            <el-form-item label="名称">
              <el-input v-model="selectedNode.data.label" />
            </el-form-item>
            <el-form-item label="类型">
              <el-tag size="small">{{ selectedNode.data.nodeType }}</el-tag>
            </el-form-item>

            <template v-if="selectedNode.data.nodeType === 'agent'">
              <el-form-item label="Agent ID">
                <el-input-number v-model="selectedNode.data.properties.agentId" :min="1" style="width: 100%" />
              </el-form-item>
              <el-form-item label="输入表达式">
                <el-input v-model="selectedNode.data.properties.input" placeholder="#variable" />
              </el-form-item>
              <el-form-item label="输出变量">
                <el-input v-model="selectedNode.data.properties.outputVar" placeholder="result_var" />
              </el-form-item>
            </template>

            <template v-else-if="selectedNode.data.nodeType === 'condition'">
              <el-form-item label="条件表达式">
                <el-input v-model="selectedNode.data.properties.expression" type="textarea" :rows="2" placeholder="#result == 'yes'" />
              </el-form-item>
            </template>

            <template v-else-if="selectedNode.data.nodeType === 'transform'">
              <el-form-item label="转换表达式">
                <el-input v-model="selectedNode.data.properties.expression" type="textarea" :rows="2" />
              </el-form-item>
            </template>

            <template v-else-if="selectedNode.data.nodeType === 'parallel'">
              <el-form-item label="等待全部">
                <el-switch v-model="selectedNode.data.properties.waitAll" />
              </el-form-item>
            </template>

            <template v-else-if="selectedNode.data.nodeType === 'loop'">
              <el-form-item label="迭代次数">
                <el-input-number v-model="selectedNode.data.properties.iterations" :min="1" style="width: 100%" />
              </el-form-item>
            </template>

            <template v-else-if="selectedNode.data.nodeType === 'human'">
              <el-form-item label="决策变量">
                <el-input v-model="selectedNode.data.properties.decisionVar" />
              </el-form-item>
            </template>

            <template v-else-if="selectedNode.data.nodeType === 'http'">
              <el-form-item label="请求方法">
                <el-select v-model="selectedNode.data.properties.method" style="width: 100%">
                  <el-option label="GET" value="GET" />
                  <el-option label="POST" value="POST" />
                  <el-option label="PUT" value="PUT" />
                  <el-option label="DELETE" value="DELETE" />
                  <el-option label="PATCH" value="PATCH" />
                </el-select>
              </el-form-item>
              <el-form-item label="URL">
                <el-input v-model="selectedNode.data.properties.url" placeholder="https://api.example.com/data" />
              </el-form-item>
              <el-form-item label="Headers">
                <el-input v-model="selectedNode.data.properties.headers" type="textarea" :rows="2" placeholder='{"Content-Type": "application/json"}' />
              </el-form-item>
              <el-form-item label="Body">
                <el-input v-model="selectedNode.data.properties.body" type="textarea" :rows="3" placeholder="请求体（POST/PUT）" />
              </el-form-item>
              <el-form-item label="响应变量">
                <el-input v-model="selectedNode.data.properties.responseVar" placeholder="httpResult" />
              </el-form-item>
            </template>

            <template v-else-if="selectedNode.data.nodeType === 'delay'">
              <el-form-item label="等待时长">
                <el-input-number v-model="selectedNode.data.properties.duration" :min="1" style="width: 100%" />
              </el-form-item>
              <el-form-item label="时间单位">
                <el-select v-model="selectedNode.data.properties.timeUnit" style="width: 100%">
                  <el-option label="秒" value="seconds" />
                  <el-option label="分钟" value="minutes" />
                  <el-option label="小时" value="hours" />
                </el-select>
              </el-form-item>
            </template>

            <template v-else-if="selectedNode.data.nodeType === 'code'">
              <el-form-item label="语言">
                <el-select v-model="selectedNode.data.properties.language" style="width: 100%">
                  <el-option label="JavaScript" value="javascript" />
                  <el-option label="Python" value="python" />
                </el-select>
              </el-form-item>
              <el-form-item label="脚本">
                <el-input v-model="selectedNode.data.properties.script" type="textarea" :rows="6" placeholder="// 输入脚本代码" style="font-family: var(--lumina-font-mono)" />
              </el-form-item>
              <el-form-item label="输出变量">
                <el-input v-model="selectedNode.data.properties.outputVar" placeholder="codeResult" />
              </el-form-item>
            </template>

            <template v-else-if="selectedNode.data.nodeType === 'start' || selectedNode.data.nodeType === 'end'">
              <div style="color: var(--lumina-text-muted); font-size: 12px; padding: 8px 0">
                此节点无需配置属性
              </div>
            </template>
          </el-form>

          <el-button type="danger" size="small" @click="deleteNode(selectedNode.id)" style="width: 100%">
            删除节点
          </el-button>
        </template>

        <template v-else>
          <div class="property-title">工作流属性</div>
          <el-form label-width="80px" size="small">
            <el-form-item label="名称">
              <el-input v-model="workflowMeta.name" :placeholder="t('workflow.name')" />
            </el-form-item>
            <el-form-item label="描述">
              <el-input v-model="workflowMeta.description" placeholder="简短描述" />
            </el-form-item>
          </el-form>
          <div class="property-stats">
            <div class="stat-row"><span>节点数</span><span>{{ nodes.length }}</span></div>
            <div class="stat-row"><span>连接数</span><span>{{ edges.length }}</span></div>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, markRaw } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { VueFlow } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import { MiniMap } from '@vue-flow/minimap'
import type { Node, Edge, Connection } from '@vue-flow/core'
import PageHeader from '@/components/common/PageHeader.vue'
import WorkflowNode from './WorkflowNode.vue'
import { NODE_TYPES, PALETTE_NODES, type NodeTypeConfig } from './node-types'
import { graphToYaml, yamlToGraph } from './yaml-sync'
import { getWorkflow, createWorkflow, updateWorkflow } from '@/api/modules/workflow'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const workflowId = computed(() => route.params.id ? Number(route.params.id) : null)
const title = computed(() => workflowId.value ? t('workflow.edit') : t('workflow.create'))
const description = computed(() => '拖拽节点设计工作流，可视化编辑或 YAML 模式')

const mode = ref<'visual' | 'yaml'>('visual')
const loading = ref(false)
const saving = ref(false)
const nodes = ref<Node[]>([])
const edges = ref<Edge[]>([])
const selectedNode = ref<Node | null>(null)
const yamlText = ref('')
const workflowMeta = reactive({ name: '', description: '' })

const nodeTypes = computed(() => ({
  workflowNode: markRaw(WorkflowNode)
}) as any)

let nodeCounter = 0

// 加载已有工作流
const loadWorkflow = async () => {
  if (!workflowId.value) return
  loading.value = true
  try {
    const res = await getWorkflow(workflowId.value)
    const wf = res.data
    workflowMeta.name = wf.name
    workflowMeta.description = wf.description || ''
    const { nodes: parsedNodes, edges: parsedEdges } = yamlToGraph(wf.definitionYaml)
    nodes.value = parsedNodes as any
    edges.value = parsedEdges as any
  } catch (e: any) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

// 拖拽
const onDragStart = (e: DragEvent, nt: NodeTypeConfig) => {
  if (e.dataTransfer) {
    e.dataTransfer.setData('application/nodeType', nt.type)
    e.dataTransfer.effectAllowed = 'move'
  }
}

const onDrop = (e: DragEvent) => {
  const type = e.dataTransfer?.getData('application/nodeType')
  if (!type) return

  const config = NODE_TYPES[type]
  if (!config) return

  const rect = (e.currentTarget as HTMLElement).getBoundingClientRect()
  const x = e.clientX - rect.left - 80
  const y = e.clientY - rect.top - 30

  nodeCounter++
  const newNode: Node = {
    id: `${type}-${Date.now()}`,
    type: 'workflowNode',
    position: { x, y },
    data: {
      label: `${config.label} ${nodeCounter}`,
      nodeType: type,
      properties: { ...config.defaultProperties }
    }
  }
  nodes.value = [...nodes.value, newNode]
}

// 节点交互
const onNodeClick = ({ node }: { node: Node }) => {
  selectedNode.value = node
}

const onPaneClick = () => {
  selectedNode.value = null
}

const onConnect = (params: Connection) => {
  const newEdge: Edge = {
    id: `edge-${Date.now()}`,
    source: params.source,
    target: params.target,
    sourceHandle: params.sourceHandle
  }
  edges.value = [...edges.value, newEdge as any]
}

const deleteNode = (id: string) => {
  nodes.value = nodes.value.filter(n => n.id !== id)
  edges.value = edges.value.filter(e => e.source !== id && e.target !== id)
  selectedNode.value = null
}

// YAML 同步
const switchToYaml = () => {
  yamlText.value = graphToYaml(nodes.value as any, edges.value as any, workflowMeta)
  mode.value = 'yaml'
}

const syncYamlToGraph = () => {
  try {
    const { nodes: parsedNodes, edges: parsedEdges, meta } = yamlToGraph(yamlText.value)
    nodes.value = parsedNodes as any
    edges.value = parsedEdges as any
    workflowMeta.name = meta.name
    workflowMeta.description = meta.description || ''
    mode.value = 'visual'
    ElMessage.success('已同步到画布')
  } catch (e: any) {
    ElMessage.error('YAML 解析失败: ' + e.message)
  }
}

// 保存
const handleSave = async () => {
  if (!workflowMeta.name.trim()) {
    ElMessage.warning('请输入工作流名称')
    return
  }
  if (nodes.value.length === 0) {
    ElMessage.warning('请至少添加一个节点')
    return
  }

  saving.value = true
  try {
    const yaml = mode.value === 'yaml' ? yamlText.value : graphToYaml(nodes.value as any, edges.value as any, workflowMeta)
    const data = { name: workflowMeta.name, description: workflowMeta.description, definitionYaml: yaml }

    if (workflowId.value) {
      await updateWorkflow(workflowId.value, data)
      ElMessage.success(t('common.updateSuccess'))
    } else {
      await createWorkflow(data)
      ElMessage.success(t('common.createSuccess'))
      router.push('/workflow/list')
    }
  } catch (e: any) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

// 初始化
if (workflowId.value) {
  loadWorkflow()
}
</script>

<style scoped>
@import '@vue-flow/core/dist/style.css';
@import '@vue-flow/core/dist/theme-default.css';
@import '@vue-flow/controls/dist/style.css';
@import '@vue-flow/minimap/dist/style.css';

.designer-page { height: calc(100vh - 60px); display: flex; flex-direction: column; }

.designer-body {
  flex: 1;
  display: flex;
  gap: 0;
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  margin: 0;
}

/* 左侧节点面板 */
.palette-panel {
  width: 160px;
  flex-shrink: 0;
  background: var(--el-fill-color-light);
  border-right: 1px solid var(--el-border-color-lighter);
  padding: 12px;
  overflow-y: auto;
}
.palette-title { font-size: 13px; font-weight: 600; margin-bottom: 10px; color: var(--el-text-color-secondary); }
.palette-list { display: flex; flex-direction: column; gap: 8px; }
.palette-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
  border: 2px solid;
  border-radius: 6px;
  cursor: grab;
  font-size: 13px;
  font-weight: 500;
  transition: transform 0.15s;
}
.palette-item:hover { transform: scale(1.03); }
.palette-item:active { cursor: grabbing; }
.palette-icon { font-size: 16px; }
.palette-hint { margin-top: 12px; font-size: 11px; color: var(--el-text-color-placeholder); text-align: center; }

/* 中间画布 */
.canvas-area { flex: 1; position: relative; overflow: hidden; }
.flow-container { width: 100%; height: 100%; }
.canvas-empty {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  pointer-events: none;
}

/* 修复 Vue Flow Background SVG 拦截节点点击事件 */
.canvas-area :deep(.vue-flow__background) {
  pointer-events: none !important;
}

/* YAML 编辑 */
.yaml-editor-area { padding: 12px; height: 100%; display: flex; flex-direction: column; gap: 8px; }
.yaml-textarea :deep(.el-textarea__inner) {
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 13px;
  flex: 1;
}
.yaml-actions { display: flex; gap: 8px; }

/* 右侧属性面板 */
.property-panel {
  width: 280px;
  flex-shrink: 0;
  background: var(--el-fill-color-light);
  border-left: 1px solid var(--el-border-color-lighter);
  padding: 12px;
  overflow-y: auto;
}
.property-title {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.property-stats { margin-top: 16px; }
.stat-row {
  display: flex;
  justify-content: space-between;
  padding: 4px 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
</style>
