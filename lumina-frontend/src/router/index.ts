/**
 * 路由入口
 */
import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import type { AppRouteRecordRaw } from '@/types/router'
import { basicRoutes, agentRoutes, systemRoutes } from './modules'
import { setupRouterGuards } from './guards'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/agent/list'
  },
  {
    path: '/',
    component: () => import('@/layouts/DefaultLayout.vue'),
    children: [...agentRoutes, ...systemRoutes]
  },
  ...basicRoutes,
  {
    path: '/:pathMatch(.*)*',
    redirect: '/404'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ left: 0, top: 0 })
})

// 设置路由守卫
setupRouterGuards(router)

export default router
