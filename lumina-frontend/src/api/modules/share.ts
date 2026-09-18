/**
 * 分享中心 API（角色包 = Agent 模板 + 技能集合；技能 URL/Git 拉取）
 */
import request from '../request'
import type { R } from '@/types/api'
import type { SkillImportResult } from './skill'

export interface AgentTemplateVO {
  id: number
  name: string
  agentType: string
  description?: string
  skillNames: string[]
  source: 'IMPORT' | 'EXPORT'
  version: number
  createTime: string
}

export function listTemplates(name?: string) {
  return request.get<R<AgentTemplateVO[]>>('/api/v1/agent-templates', { params: { name } })
}

export function importBundle(file: Blob) {
  const form = new FormData()
  form.append('file', file, 'bundle.zip')
  return request.post<R<AgentTemplateVO>>('/api/v1/agent-templates/import', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000
  })
}

export function instantiateTemplate(id: number, name?: string) {
  return request.post<R<{ agentId: number; agentName: string; agentType: string }>>(
    `/api/v1/agent-templates/${id}/instantiate`,
    null,
    { params: name ? { name } : {} }
  )
}

export function deleteTemplate(id: number) {
  return request.delete<R<void>>(`/api/v1/agent-templates/${id}`)
}

/** 导出角色包（blob zip） */
export function exportBundle(agentId: number, includeSkills = true) {
  return request.get<Blob>(`/api/v1/agents/${agentId}/export-bundle`, {
    responseType: 'blob',
    params: { includeSkills }
  })
}

/** 从 URL/Git 仓库导入技能 */
export function importSkillFromUrl(url: string) {
  return request.post<R<SkillImportResult>>('/api/v1/skills/import-url', { url }, { timeout: 180000 })
}
