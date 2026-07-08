/**
 * Dashboard 聚合 API — 汇总多个模块数据
 */
import { listAgents, listAgentTasks } from './modules/agent'
import { getCostSummary } from './modules/cost'

export interface DashboardStats {
  agentCount: number
  todayTasks: number
  totalTokens: number
  totalCost: number
}

/**
 * 获取 Dashboard 统计数据（聚合 Agent 数量 + 任务 + 成本）
 */
export async function getStats(): Promise<DashboardStats> {
  const [agentsRes, tasksRes, costRes] = await Promise.allSettled([
    listAgents({ pageNum: 1, pageSize: 1 }),
    listAgentTasks({ pageNum: 1, pageSize: 1 }),
    getCostSummary()
  ])

  return {
    agentCount: agentsRes.status === 'fulfilled' ? agentsRes.value.data.total : 0,
    todayTasks: tasksRes.status === 'fulfilled' ? tasksRes.value.data.total : 0,
    totalTokens: costRes.status === 'fulfilled' ? (costRes.value.data as any)?.totalTokens ?? 0 : 0,
    totalCost: costRes.status === 'fulfilled' ? Number((costRes.value.data as any)?.totalCost ?? 0) : 0,
  }
}

/**
 * 获取近期任务列表
 */
export async function getRecentTasks(limit = 5) {
  const res = await listAgentTasks({ pageNum: 1, pageSize: limit })
  return res.data.list || []
}
