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
  },
  {
    path: '/401',
    name: 'Unauthorized',
    component: () => import('@/views/error/401.vue'),
    meta: {
      title: '未授权',
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
          hidden: true,
          activeMenu: '/agent/list'
        }
      },
      {
        path: 'detail/:id',
        name: 'AgentDetail',
        component: () => import('@/views/agent/detail.vue'),
        meta: {
          title: 'Agent 详情',
          requiresAuth: true,
          hidden: true,
          activeMenu: '/agent/list'
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
      },
      {
        path: 'triggers',
        name: 'AgentTriggers',
        component: () => import('@/views/trigger/index.vue'),
        meta: {
          title: '定时触发器',
          requiresAuth: true,
          keepAlive: true
        }
      },
      {
        path: 'trace',
        name: 'AgentTrace',
        component: () => import('@/views/agent/trace.vue'),
        meta: {
          title: '推理追踪',
          requiresAuth: true,
          keepAlive: true,
          permissions: ['agent:trace']
        }
      }
    ]
  }
]

// 系统路由
export const systemRoutes: AppRouteRecordRaw[] = [
  {
    path: '/profile',
    name: 'Profile',
    component: () => import('@/views/profile/index.vue'),
    meta: {
      title: '个人中心',
      requiresAuth: true
    }
  },
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
          permissions: ['user:view'],
          keepAlive: true
        }
      },
      {
        path: 'role',
        name: 'SystemRole',
        component: () => import('@/views/system/role.vue'),
        meta: {
          title: '角色管理',
          requiresAuth: true,
          permissions: ['role:view'],
          keepAlive: true
        }
      },
      {
        path: 'permission',
        name: 'SystemPermission',
        component: () => import('@/views/system/permission.vue'),
        meta: {
          title: '权限管理',
          requiresAuth: true,
          permissions: ['permission:view'],
          keepAlive: true
        }
      },
      {
        path: 'menu',
        name: 'SystemMenu',
        component: () => import('@/views/system/menu.vue'),
        meta: {
          title: '菜单管理',
          requiresAuth: true,
          keepAlive: true
        }
      },
      {
        path: 'dict',
        name: 'SystemDict',
        component: () => import('@/views/system/dict.vue'),
        meta: {
          title: '字典管理',
          requiresAuth: true,
          keepAlive: true
        }
      },
      {
        path: 'online',
        name: 'SystemOnline',
        component: () => import('@/views/system/online.vue'),
        meta: {
          title: '在线用户',
          requiresAuth: true,
          keepAlive: true
        }
      },
      {
        path: 'tenant',
        name: 'SystemTenant',
        component: () => import('@/views/system/tenant.vue'),
        meta: {
          title: '租户管理',
          requiresAuth: true,
          permissions: ['tenant:view'],
          keepAlive: true
        }
      },
      {
        path: 'audit',
        name: 'SystemAudit',
        component: () => import('@/views/system/audit.vue'),
        meta: {
          title: '审计日志',
          requiresAuth: true,
          keepAlive: true
        }
      },
      {
        path: 'models',
        name: 'SystemModels',
        component: () => import('@/views/system/models.vue'),
        meta: {
          title: '模型管理',
          requiresAuth: true,
          permissions: ['model:list'],
          keepAlive: true
        }
      },
      {
        path: 'api-tokens',
        name: 'SystemApiTokens',
        component: () => import('@/views/system/api-tokens.vue'),
        meta: {
          title: 'API Token',
          requiresAuth: true,
          keepAlive: true
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
      requiresAuth: true,
      keepAlive: true
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
      },
      {
        path: 'mcp',
        name: 'MonitorMcp',
        component: () => import('@/views/monitor/mcp.vue'),
        meta: {
          title: 'MCP 管理',
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
          hidden: true,
          activeMenu: '/workflow/list'
        }
      },
      {
        path: 'designer',
        name: 'WorkflowDesignerNew',
        component: () => import('@/views/workflow/designer/index.vue'),
        meta: {
          title: '可视化设计',
          requiresAuth: true,
          hidden: true,
          activeMenu: '/workflow/list'
        }
      },
      {
        path: 'designer/:id',
        name: 'WorkflowDesignerEdit',
        component: () => import('@/views/workflow/designer/index.vue'),
        meta: {
          title: '编辑工作流',
          requiresAuth: true,
          hidden: true,
          activeMenu: '/workflow/list'
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
      requiresAuth: true,
      keepAlive: true
    }
  }
]

// 技能管理路由（渐进披露）
export const skillRoutes: AppRouteRecordRaw[] = [
  {
    path: '/skill',
    name: 'Skill',
    component: () => import('@/views/skill/index.vue'),
    meta: {
      title: '技能管理',
      icon: 'MagicStick',
      requiresAuth: true,
      keepAlive: true
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
      requiresAuth: true,
      keepAlive: true
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
      requiresAuth: true,
      keepAlive: true
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
      requiresAuth: true,
      keepAlive: true
    }
  }
]

// 知识库联邦路由（已合并到知识库页面，重定向）
export const knowledgeBaseRoutes: AppRouteRecordRaw[] = [
  {
    path: '/knowledge-base',
    redirect: '/knowledge'
  }
]

// A/B 测试路由
export const abTestRoutes: AppRouteRecordRaw[] = [
  {
    path: '/ab-test',
    name: 'AbTest',
    component: () => import('@/views/ab-test/index.vue'),
    meta: {
      title: 'A/B 测试',
      icon: 'Aim',
      requiresAuth: true,
      keepAlive: true
    }
  }
]

// 长期记忆路由
export const memoryRoutes: AppRouteRecordRaw[] = [
  {
    path: '/memory',
    name: 'LongTermMemory',
    component: () => import('@/views/memory/index.vue'),
    meta: {
      title: '长期记忆',
      icon: 'Coin',
      requiresAuth: true,
      keepAlive: true
    }
  }
]

// 通知中心路由
export const notificationRoutes: AppRouteRecordRaw[] = [
  {
    path: '/notification',
    name: 'Notification',
    redirect: '/notification/list',
    meta: {
      title: '通知中心',
      icon: 'Bell',
      requiresAuth: true
    },
    children: [
      {
        path: 'list',
        name: 'NotificationList',
        component: () => import('@/views/notification/index.vue'),
        meta: {
          title: '通知列表',
          requiresAuth: true,
          keepAlive: true
        }
      },
      {
        path: 'webhooks',
        name: 'NotificationWebhooks',
        component: () => import('@/views/notification/webhooks.vue'),
        meta: {
          title: 'Webhook 订阅',
          requiresAuth: true
        }
      }
    ]
  }
]
