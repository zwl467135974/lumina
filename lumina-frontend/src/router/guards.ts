/**
 * 路由守卫
 */
import type { Router } from 'vue-router'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'

// 配置 NProgress
NProgress.configure({ showSpinner: false })

export function setupRouterGuards(router: Router) {
  // 前置守卫
  router.beforeEach((to, from, next) => {
    NProgress.start()

    // 设置页面标题
    if (to.meta?.title) {
      document.title = `${to.meta.title} - ${import.meta.env.VITE_APP_TITLE}`
    }

    // 检查是否需要认证
    if (to.meta?.requiresAuth !== false) {
      const token = localStorage.getItem('lumina_token')
      if (!token) {
        next({
          path: '/login',
          query: { redirect: to.fullPath }
        })
        return
      }
    }

    next()
  })

  // 后置守卫
  router.afterEach(() => {
    NProgress.done()
  })

  // 错误处理
  router.onError((error) => {
    console.error('路由错误:', error)
    NProgress.done()
  })
}
