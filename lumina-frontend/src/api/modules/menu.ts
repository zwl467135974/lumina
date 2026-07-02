/**
 * 菜单 API
 */
import request from '../request'
import type { R } from '@/types/api'

/**
 * 菜单项（后端下发）
 */
export interface MenuVO {
  name: string
  path: string
  title: string
  icon?: string
  redirect?: string
  permission?: string
  keepAlive?: boolean
  children?: MenuVO[]
}

/**
 * 获取当前用户菜单（按权限过滤）
 */
export function getUserMenus() {
  return request.get<R<MenuVO[]>>('/api/v1/base/menus')
}
