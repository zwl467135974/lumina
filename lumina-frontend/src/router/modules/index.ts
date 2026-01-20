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
