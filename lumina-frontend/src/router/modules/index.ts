/**
 * 路由模块
 */
import type { AppRouteRecordRaw } from '@/types/router'

// 基础路由（不需要认证）
export const basicRoutes: AppRouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: {
      title: '登录',
      requiresAuth: false
    }
  },
  {
    path: '/404',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue'),
    meta: {
      title: '页面不存在',
      requiresAuth: false
    }
  }
]

// Agent 路由
export const agentRoutes: AppRouteRecordRaw[] = [
  {
    path: '/',
    name: 'Dashboard',
    component: () => import('@/views/dashboard/index.vue'),
    meta: {
      title: '仪表盘',
      icon: 'Odometer',
      requiresAuth: true
    }
  },
  {
    path: '/agent',
    name: 'Agent',
    redirect: '/agent/list',
    meta: {
      title: 'Agent 管理',
      icon: 'Agent',
      requiresAuth: true
    },
    children: [
      {
        path: 'list',
        name: 'AgentList',
        component: () => import('@/views/agent/index.vue'),
        meta: {
          title: 'Agent 列表',
          requiresAuth: true,
          keepAlive: true
        }
      },
      {
        path: 'create',
        name: 'AgentCreate',
        component: () => import('@/views/agent/form.vue'),
        meta: {
          title: '创建 Agent',
          requiresAuth: true,
          permissions: ['agent:create']
        }
      },
      {
        path: 'edit/:id',
        name: 'AgentEdit',
        component: () => import('@/views/agent/form.vue'),
        meta: {
          title: '编辑 Agent',
          requiresAuth: true,
          permissions: ['agent:update'],
          hidden: true
        }
      },
      {
        path: 'detail/:id',
        name: 'AgentDetail',
        component: () => import('@/views/agent/detail.vue'),
        meta: {
          title: 'Agent 详情',
          requiresAuth: true,
          hidden: true
        }
      },
      {
        path: 'tasks',
        name: 'AgentTasks',
        component: () => import('@/views/task/index.vue'),
        meta: {
          title: '异步任务',
          requiresAuth: true,
          keepAlive: true
        }
      }
    ]
  }
]

// 系统路由
export const systemRoutes: AppRouteRecordRaw[] = [
  {
    path: '/system',
    name: 'System',
    redirect: '/system/user',
    meta: {
      title: '系统管理',
      icon: 'Setting',
      requiresAuth: true
    },
    children: [
      {
        path: 'user',
        name: 'SystemUser',
        component: () => import('@/views/system/user.vue'),
        meta: {
          title: '用户管理',
          requiresAuth: true,
          permissions: ['user:view']
        }
      },
      {
        path: 'role',
        name: 'SystemRole',
        component: () => import('@/views/system/role.vue'),
        meta: {
          title: '角色管理',
          requiresAuth: true,
          permissions: ['role:view']
        }
      },
      {
        path: 'permission',
        name: 'SystemPermission',
        component: () => import('@/views/system/permission.vue'),
        meta: {
          title: '权限管理',
          requiresAuth: true,
          permissions: ['permission:view']
        }
      },
      {
        path: 'tenant',
        name: 'SystemTenant',
        component: () => import('@/views/system/tenant.vue'),
        meta: {
          title: '租户管理',
          requiresAuth: true,
          permissions: ['tenant:view']
        }
      }
    ]
  }
]

// 知识库路由
export const knowledgeRoutes: AppRouteRecordRaw[] = [
  {
    path: '/knowledge',
    name: 'Knowledge',
    component: () => import('@/views/knowledge/index.vue'),
    meta: {
      title: '知识库',
      icon: 'Document',
      requiresAuth: true
    }
  }
]

// 监控路由
export const monitorRoutes: AppRouteRecordRaw[] = [
  {
    path: '/monitor',
    name: 'Monitor',
    redirect: '/monitor/tools',
    meta: {
      title: '监控中心',
      icon: 'Monitor',
      requiresAuth: true
    },
    children: [
      {
        path: 'tools',
        name: 'MonitorTools',
        component: () => import('@/views/monitor/tools.vue'),
        meta: {
          title: '工具监控',
          requiresAuth: true
        }
      }
    ]
  }
]

// 工作流路由
export const workflowRoutes: AppRouteRecordRaw[] = [
  {
    path: '/workflow',
    name: 'Workflow',
    redirect: '/workflow/list',
    meta: {
      title: '工作流',
      icon: 'Connection',
      requiresAuth: true
    },
    children: [
      {
        path: 'list',
        name: 'WorkflowList',
        component: () => import('@/views/workflow/index.vue'),
        meta: {
          title: '工作流管理',
          requiresAuth: true,
          keepAlive: true
        }
      },
      {
        path: 'detail/:id',
        name: 'WorkflowDetail',
        component: () => import('@/views/workflow/detail.vue'),
        meta: {
          title: '执行详情',
          requiresAuth: true,
          hidden: true
        }
      },
      {
        path: 'designer',
        name: 'WorkflowDesignerNew',
        component: () => import('@/views/workflow/designer/index.vue'),
        meta: {
          title: '可视化设计',
          requiresAuth: true,
          hidden: true
        }
      },
      {
        path: 'designer/:id',
        name: 'WorkflowDesignerEdit',
        component: () => import('@/views/workflow/designer/index.vue'),
        meta: {
          title: '编辑工作流',
          requiresAuth: true,
          hidden: true
        }
      }
    ]
  }
]

// Prompt 路由
export const promptRoutes: AppRouteRecordRaw[] = [
  {
    path: '/prompt',
    name: 'Prompt',
    component: () => import('@/views/prompt/index.vue'),
    meta: {
      title: 'Prompt 管理',
      icon: 'EditPen',
      requiresAuth: true
    }
  }
]

// 成本仪表盘路由
export const costRoutes: AppRouteRecordRaw[] = [
  {
    path: '/cost',
    name: 'Cost',
    component: () => import('@/views/cost/index.vue'),
    meta: {
      title: '成本仪表盘',
      icon: 'Money',
      requiresAuth: true
    }
  }
]

// 预算管理路由
export const budgetRoutes: AppRouteRecordRaw[] = [
  {
    path: '/budget',
    name: 'Budget',
    component: () => import('@/views/budget/index.vue'),
    meta: {
      title: '预算管理',
      icon: 'Wallet',
      requiresAuth: true
    }
  }
]

// Agent 评估路由
export const evaluationRoutes: AppRouteRecordRaw[] = [
  {
    path: '/evaluation',
    name: 'Evaluation',
    component: () => import('@/views/evaluation/index.vue'),
    meta: {
      title: 'Agent 评估',
      icon: 'DataAnalysis',
      requiresAuth: true
    }
  }
]

// 知识库联邦路由
export const knowledgeBaseRoutes: AppRouteRecordRaw[] = [
  {
    path: '/knowledge-base',
    name: 'KnowledgeBase',
    component: () => import('@/views/knowledge-base/index.vue'),
    meta: {
      title: '知识库联邦',
      icon: 'Collection',
      requiresAuth: true
    }
  }
]
