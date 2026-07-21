/**
 * 路由入口
 */
import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { basicRoutes, agentRoutes, systemRoutes, knowledgeRoutes, monitorRoutes, workflowRoutes, promptRoutes, costRoutes, budgetRoutes, evaluationRoutes, knowledgeBaseRoutes, notificationRoutes, abTestRoutes, memoryRoutes } from './modules'
import { setupRouterGuards } from './guards'

const routes = [
  {
    path: '/',
    component: () => import('@/layouts/DefaultLayout.vue'),
    children: [...agentRoutes, ...systemRoutes, ...knowledgeRoutes, ...monitorRoutes, ...workflowRoutes, ...promptRoutes, ...costRoutes, ...budgetRoutes, ...evaluationRoutes, ...knowledgeBaseRoutes, ...notificationRoutes, ...abTestRoutes, ...memoryRoutes]
  },
  ...basicRoutes,
  {
    path: '/:pathMatch(.*)*',
    redirect: '/404'
  }
] as RouteRecordRaw[]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior() {
    const el = document.querySelector('.page-content')
    if (el) el.scrollTop = 0
    return { top: 0 }
  }
})

// 设置路由守卫
setupRouterGuards(router)

export default router
