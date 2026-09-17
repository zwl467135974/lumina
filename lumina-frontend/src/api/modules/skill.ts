/**
 * 技能管理 API（渐进披露：目录进上下文，全文按需加载；SKILL.md 开放标准导入/导出）
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
  source?: string
  scanStatus?: string
  scanReport?: string
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

export interface SkillImportResult {
  imported: { id: number; name: string; scanStatus: string; enabled: boolean }[]
  rejected: { name: string; reason: string }[]
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

/** 导入 SKILL.md（.md 单个 / .zip 多技能，导入前强制安全体检） */
export function importSkills(file: File) {
  const form = new FormData()
  form.append('file', file)
  return request.post<R<SkillImportResult>>('/api/v1/skills/import', form, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/** 重跑安全体检（REJECTED 将强制禁用） */
export function rescanSkill(id: number) {
  return request.post<R<SkillVO>>(`/api/v1/skills/${id}/rescan`)
}

/** 导出单个技能为 SKILL.md（blob） */
export function exportSkill(id: number) {
  return request.get<Blob>(`/api/v1/skills/${id}/export`, { responseType: 'blob' })
}

/** 导出全部技能为 zip（blob） */
export function exportAllSkills() {
  return request.get<Blob>('/api/v1/skills/export-all', { responseType: 'blob' })
}
