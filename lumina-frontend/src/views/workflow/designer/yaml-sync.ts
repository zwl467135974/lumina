/**
 * YAML ↔ Vue Flow 双向同步工具
 */
import * as yaml from 'js-yaml'

export interface FlowNodeData {
  id: string
  type: string
  position: { x: number; y: number }
  data: {
    label: string
    nodeType: string
    properties: Record<string, any>
  }
}

export interface FlowEdge {
  id: string
  source: string
  target: string
  sourceHandle?: string | null
}

export interface WorkflowYAML {
  name?: string
  description?: string
  inputs?: Array<{ name: string; type?: string; required?: boolean }>
  nodes?: Array<Record<string, any>>
  edges?: Array<Record<string, any>>
  outputs?: Record<string, string>
}

/**
 * 将 Vue Flow 节点+边导出为 YAML 字符串
 */
export function graphToYaml(
  nodes: FlowNodeData[],
  edges: FlowEdge[],
  meta: { name: string; description?: string }
): string {
  const yamlNodes = nodes.map(n => {
    const node: Record<string, any> = {
      id: n.id,
      type: n.data.nodeType,
      name: n.data.label || n.id
    }
    const props = n.data.properties || {}
    for (const [key, value] of Object.entries(props)) {
      if (value !== '' && value !== null && value !== undefined) {
        node[key] = value
      }
    }
    return node
  })

  const yamlEdges: Array<Record<string, any>> = edges.map(e => {
    const edge: Record<string, any> = { from: e.source, to: e.target }
    if ((e as any).condition) {
      edge.condition = (e as any).condition
    }
    return edge
  })

  const doc: WorkflowYAML = {
    name: meta.name || 'workflow',
    description: meta.description || '',
    nodes: yamlNodes,
    edges: yamlEdges
  }

  return yaml.dump(doc, { indent: 2, lineWidth: 120 })
}

/**
 * 将 YAML 字符串解析为 Vue Flow 节点+边
 */
export function yamlToGraph(yamlStr: string): {
  nodes: FlowNodeData[]
  edges: FlowEdge[]
  meta: { name: string; description?: string }
} {
  const doc = yaml.load(yamlStr) as WorkflowYAML

  const nodes: FlowNodeData[] = []
  const edges: FlowEdge[] = []

  const xStart = 100
  const yStart = 80
  const xStep = 280
  const yStep = 160

  const rawNodes = doc.nodes || []
  rawNodes.forEach((raw, idx) => {
    const nodeType = raw.type || 'agent'

    const properties: Record<string, any> = {}
    for (const [key, value] of Object.entries(raw)) {
      if (!['id', 'type', 'name'].includes(key)) {
        properties[key] = value
      }
    }

    nodes.push({
      id: raw.id || `node-${idx}`,
      type: 'workflowNode',
      position: {
        x: xStart + (idx % 3) * xStep,
        y: yStart + Math.floor(idx / 3) * yStep
      },
      data: {
        label: raw.name || raw.id || `Node ${idx + 1}`,
        nodeType,
        properties
      }
    })
  })

  const rawEdges = doc.edges || []
  rawEdges.forEach((raw, idx) => {
    edges.push({
      id: `edge-${idx}`,
      source: raw.from,
      target: raw.to,
      sourceHandle: null
    })
  })

  // 从 condition 节点的 branches 解析额外的 edges
  rawNodes.forEach((raw) => {
    if ((raw.type === 'condition' || raw.type === 'loop' || raw.type === 'parallel') && raw.branches) {
      raw.branches.forEach((branch: Record<string, any>, bidx: number) => {
        if (branch.to) {
          edges.push({
            id: `edge-branch-${raw.id}-${bidx}`,
            source: raw.id,
            target: branch.to,
            sourceHandle: null
          })
        }
      })
    }
  })

  return {
    nodes,
    edges,
    meta: {
      name: doc.name || '',
      description: doc.description || ''
    }
  }
}
