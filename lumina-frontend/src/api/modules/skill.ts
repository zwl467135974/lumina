/**
 * 技能管理 API（渐进披露：目录进上下文，全文按需加载）
 */
import request from '../request'
import type { R } from '@/types/api'

export interface SkillVO {
  id: number
  name: string
  description: string
  whenToUse?: string
  content: string
  enabled: boolean
  createTime?: string
  updateTime?: string
}

export interface SkillDTO {
  name: string
  description: string
  whenToUse?: string
  content: string
  enabled?: boolean
}

export function listSkills(params?: { name?: string; pageNum?: number; pageSize?: number }) {
  return request.get<R<SkillVO[]>>('/api/v1/skills', { params })
}

export function createSkill(data: SkillDTO) {
  return request.post<R<SkillVO>>('/api/v1/skills', data)
}

export function updateSkill(id: number, data: SkillDTO) {
  return request.put<R<SkillVO>>(`/api/v1/skills/${id}`, data)
}

export function setSkillEnabled(id: number, enabled: boolean) {
  return request.post<R<SkillVO>>(`/api/v1/skills/${id}/enabled`, null, {
    params: { enabled }
  })
}

export function deleteSkill(id: number) {
  return request.delete<R<void>>(`/api/v1/skills/${id}`)
}
