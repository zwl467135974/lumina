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
  start: {
    type: 'start',
    label: '开始',
    icon: '▶',
    color: '#10b981',
    bgColor: '#ecfdf5',
    defaultProperties: {}
  },
  end: {
    type: 'end',
    label: '结束',
    icon: '⏹',
    color: '#ef4444',
    bgColor: '#fef2f2',
    defaultProperties: {}
  },
  agent: {
    type: 'agent',
    label: 'Agent',
    icon: '🤖',
    color: '#4f46e5',
    bgColor: '#eef2ff',
    defaultProperties: { agentId: 1, input: '', outputVar: '' }
  },
  http: {
    type: 'http',
    label: 'HTTP 请求',
    icon: '🌐',
    color: '#0ea5e9',
    bgColor: '#f0f9ff',
    defaultProperties: { method: 'GET', url: '', headers: '{}', body: '', responseVar: 'httpResult' }
  },
  condition: {
    type: 'condition',
    label: '条件分支',
    icon: '🔀',
    color: '#f59e0b',
    bgColor: '#fffbeb',
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
  delay: {
    type: 'delay',
    label: '延迟',
    icon: '⏱',
    color: '#8b5cf6',
    bgColor: '#f5f3ff',
    defaultProperties: { duration: 5, timeUnit: 'seconds' }
  },
  code: {
    type: 'code',
    label: '代码执行',
    icon: '📝',
    color: '#6366f1',
    bgColor: '#eef2ff',
    defaultProperties: { language: 'javascript', script: '', outputVar: 'codeResult' }
  },
  transform: {
    type: 'transform',
    label: '数据转换',
    icon: '🔄',
    color: '#64748b',
    bgColor: '#f8fafc',
    defaultProperties: { expression: '' }
  },
  human: {
    type: 'human',
    label: '人工审批',
    icon: '✋',
    color: '#ec4899',
    bgColor: '#fdf2f8',
    defaultProperties: { decisionVar: '' }
  }
}

export const PALETTE_NODES = Object.values(NODE_TYPES)
