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
            <Background :pattern-color="bgPatternColor" :gap="20" />
            <Controls />
            <MiniMap />
          </VueFlow>

          <div v-if="nodes.length === 0" class="canvas-empty">
            <el-empty :description="t('workflow.dragTip')" :image-size="80" />
          </div>
        </div>

        <div v-else class="yaml-editor-area">
          <el-input
            v-model="yamlText"
            type="textarea"
            :rows="30"
            :placeholder="t('workflow.yamlPlaceholder2')"
            class="yaml-textarea"
          />
          <div class="yaml-actions">
            <el-button size="small" @click="syncYamlToGraph">{{ t('workflow.syncFromYaml') }}</el-button>
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
            <el-form-item :label="t('workflow.nodeId')">
              <el-input v-model="selectedNode.id" disabled />
            </el-form-item>
            <el-form-item :label="t('workflow.designerName')">
              <el-input v-model="selectedNode.data.label" />
            </el-form-item>
            <el-form-item :label="t('workflow.type')">
              <el-tag size="small">{{ selectedNode.data.nodeType }}</el-tag>
            </el-form-item>

            <template v-if="selectedNode.data.nodeType === 'agent'">
              <el-form-item label="Agent ID">
                <el-input-number v-model="selectedNode.data.properties.agentId" :min="1" style="width: 100%" />
              </el-form-item>
              <el-form-item :label="t('workflow.inputExpr')">
                <el-input v-model="selectedNode.data.properties.input" placeholder="#variable" />
              </el-form-item>
              <el-form-item :label="t('workflow.outputVar')">
                <el-input v-model="selectedNode.data.properties.outputVar" placeholder="result_var" />
              </el-form-item>
            </template>

            <template v-else-if="selectedNode.data.nodeType === 'condition'">
              <el-form-item :label="t('workflow.conditionExpr')">
                <el-input v-model="selectedNode.data.properties.expression" type="textarea" :rows="2" placeholder="#result == 'yes'" />
              </el-form-item>
            </template>

            <template v-else-if="selectedNode.data.nodeType === 'transform'">
              <el-form-item :label="t('workflow.transitionExpr')">
                <el-input v-model="selectedNode.data.properties.expression" type="textarea" :rows="2" />
              </el-form-item>
            </template>

            <template v-else-if="selectedNode.data.nodeType === 'parallel'">
              <el-form-item :label="t('workflow.waitForAll')">
                <el-switch v-model="selectedNode.data.properties.waitAll" />
              </el-form-item>
            </template>

            <template v-else-if="selectedNode.data.nodeType === 'loop'">
              <el-form-item :label="t('workflow.iterations')">
                <el-input-number v-model="selectedNode.data.properties.iterations" :min="1" style="width: 100%" />
              </el-form-item>
            </template>

            <template v-else-if="selectedNode.data.nodeType === 'human'">
              <el-form-item :label="t('workflow.decisionVar')">
                <el-input v-model="selectedNode.data.properties.decisionVar" />
              </el-form-item>
            </template>

            <template v-else-if="selectedNode.data.nodeType === 'http'">
              <el-form-item :label="t('workflow.httpMethod')">
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
                <el-input v-model="selectedNode.data.properties.body" type="textarea" :rows="3" :placeholder="t('workflow.httpBody')" />
              </el-form-item>
              <el-form-item :label="t('workflow.responseVar')">
                <el-input v-model="selectedNode.data.properties.responseVar" placeholder="httpResult" />
              </el-form-item>
            </template>

            <template v-else-if="selectedNode.data.nodeType === 'delay'">
              <el-form-item :label="t('workflow.waitDuration')">
                <el-input-number v-model="selectedNode.data.properties.duration" :min="1" style="width: 100%" />
              </el-form-item>
              <el-form-item :label="t('workflow.timeUnit')">
                <el-select v-model="selectedNode.data.properties.timeUnit" style="width: 100%">
                  <el-option :label="t('workflow.second')" value="seconds" />
                  <el-option :label="t('workflow.minute')" value="minutes" />
                  <el-option :label="t('workflow.hour')" value="hours" />
                </el-select>
              </el-form-item>
            </template>

            <template v-else-if="selectedNode.data.nodeType === 'code'">
              <el-form-item :label="t('workflow.language')">
                <el-select v-model="selectedNode.data.properties.language" style="width: 100%">
                  <el-option label="JavaScript" value="javascript" />
                  <el-option label="Python" value="python" />
                </el-select>
              </el-form-item>
              <el-form-item :label="t('workflow.script')">
                <el-input v-model="selectedNode.data.properties.script" type="textarea" :rows="6" :placeholder="t('workflow.scriptPlaceholder')" style="font-family: var(--lumina-font-mono)" />
              </el-form-item>
              <el-form-item :label="t('workflow.outputVar')">
                <el-input v-model="selectedNode.data.properties.outputVar" placeholder="codeResult" />
              </el-form-item>
            </template>

            <template v-else-if="selectedNode.data.nodeType === 'start' || selectedNode.data.nodeType === 'end'">
              <div style="color: var(--lumina-text-muted); font-size: 12px; padding: 8px 0">
                {{ t('workflow.noNodeConfig') }}
              </div>
            </template>
          </el-form>

          <el-button type="danger" size="small" @click="deleteNode(selectedNode.id)" style="width: 100%">
            {{ t('workflow.deleteNode') }}
          </el-button>
        </template>

        <template v-else>
          <div class="property-title">{{ t('workflow.workflowProps') }}</div>
          <el-form label-width="80px" size="small">
            <el-form-item :label="t('workflow.designerName')">
              <el-input v-model="workflowMeta.name" :placeholder="t('workflow.name')" />
            </el-form-item>
            <el-form-item :label="t('workflow.designerDesc')">
              <el-input v-model="workflowMeta.description" :placeholder="t('workflow.designerDescPlaceholder')" />
            </el-form-item>
          </el-form>
          <div class="property-stats">
            <div class="stat-row"><span>{{ t('workflow.nodeCount') }}</span><span>{{ nodes.length }}</span></div>
            <div class="stat-row"><span>{{ t('workflow.edgeCount') }}</span><span>{{ edges.length }}</span></div>
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
import { useAppStore } from '@/stores'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const appStore = useAppStore()
const workflowId = computed(() => route.params.id ? Number(route.params.id) : null)
const title = computed(() => workflowId.value ? t('workflow.edit') : t('workflow.create'))
const description = computed(() => t('workflow.designerPageDesc'))
// VueFlow Background pattern-color 需要字符串值，按主题切换
const bgPatternColor = computed(() => appStore.theme === 'dark' ? '#475569' : '#cbd5e1')

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

/* ---------- Responsive: 窄屏隐藏面板，推荐 YAML 模式 ---------- */
@media (max-width: 1023px) {
  .palette-panel,
  .property-panel {
    position: absolute;
    z-index: 30;
    box-shadow: var(--lumina-shadow-lg);
  }
  .palette-panel { left: 0; top: 0; height: 100%; }
  .property-panel { right: 0; top: 0; height: 100%; }
  .yaml-textarea :deep(textarea) {
    rows: 16;
  }
}
</style>
