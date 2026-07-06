/**
 * 工作流节点类型配置
 */
export interface NodeTypeConfig {
  type: string
  label: string
  icon: string
  color: string
  bgColor: string
  defaultProperties: Record<string, any>
}

export const NODE_TYPES: Record<string, NodeTypeConfig> = {
  agent: {
    type: 'agent',
    label: 'Agent',
    icon: '🤖',
    color: '#409eff',
    bgColor: '#ecf5ff',
    defaultProperties: { agentId: 1, input: '', outputVar: '' }
  },
  condition: {
    type: 'condition',
    label: '条件分支',
    icon: '🔀',
    color: '#e6a23c',
    bgColor: '#fdf6ec',
    defaultProperties: { expression: '' }
  },
  loop: {
    type: 'loop',
    label: '循环',
    icon: '🔁',
    color: '#f56c6c',
    bgColor: '#fef0f0',
    defaultProperties: { iterations: 3, input: '' }
  },
  parallel: {
    type: 'parallel',
    label: '并行',
    icon: '⚡',
    color: '#67c23a',
    bgColor: '#f0f9eb',
    defaultProperties: { waitAll: true }
  },
  transform: {
    type: 'transform',
    label: '数据转换',
    icon: '🔄',
    color: '#909399',
    bgColor: '#f4f4f5',
    defaultProperties: { expression: '' }
  },
  human: {
    type: 'human',
    label: '人工审批',
    icon: '✋',
    color: '#9c27b0',
    bgColor: '#f3e5f5',
    defaultProperties: { decisionVar: '' }
  }
}

export const PALETTE_NODES = Object.values(NODE_TYPES)
