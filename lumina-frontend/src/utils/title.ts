/**
 * Route/menu title localization helpers.
 */
const titleKeyMap: Record<string, string> = {
  '登录': 'route.login',
  '页面不存在': 'route.notFound',
  '未授权': 'route.unauthorized',
  '仪表盘': 'menu.dashboard',
  'Agent 管理': 'menu.agent',
  'Agent 列表': 'menu.agentList',
  '创建 Agent': 'agent.create',
  '编辑 Agent': 'agent.edit',
  'Agent 详情': 'agent.detail',
  '异步任务': 'menu.agentTasks',
  '工作流': 'menu.workflowRoot',
  '工作流管理': 'menu.workflow',
  '执行详情': 'workflow.detail',
  '可视化设计': 'workflow.designer',
  '编辑工作流': 'workflow.edit',
  '知识库': 'menu.knowledge',
  '知识库联邦': 'menu.knowledgeBase',
  'Prompt 管理': 'menu.prompt',
  '成本仪表盘': 'menu.cost',
  '预算管理': 'menu.budget',
  'Agent 评估': 'menu.evaluation',
  '系统管理': 'menu.system',
  '用户管理': 'menu.user',
  '角色管理': 'menu.role',
  '权限管理': 'menu.permission',
  '租户管理': 'menu.tenant',
  '审计日志': 'menu.audit',
  '监控中心': 'menu.monitorCenter',
  '工具监控': 'menu.monitor'
}

export function localizeTitle(title: string | undefined, t: (key: string) => string): string {
  if (!title) return ''
  const key = titleKeyMap[title]
  return key ? t(key) : title
}
