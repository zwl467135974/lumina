/**
 * 路由类型定义
 */
import type { RouteRecordRaw } from 'vue-router'

/**
 * 路由元信息
 */
export interface RouteMeta {
  /** 标题 */
  title?: string
  /** 图标 */
  icon?: string
  /** 是否需要认证 */
  requiresAuth?: boolean
  /** 权限标识 */
  permissions?: string[]
  /** 是否隐藏 */
  hidden?: boolean
  /** 是否缓存 */
  keepAlive?: boolean
  /** 链接地址 */
  link?: string
}

/**
 * 扩展的路由配置
 */
export interface AppRouteRecordRaw extends Omit<RouteRecordRaw, 'meta' | 'children'> {
  meta?: RouteMeta
  children?: AppRouteRecordRaw[]
}

/**
 * 菜单项
 */
export interface MenuItem {
  path: string
  name: string
  meta?: RouteMeta
  children?: MenuItem[]
}
