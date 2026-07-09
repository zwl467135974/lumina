/**
 * 数据字典 API
 */
import request from '../request'
import type { R } from '@/types/api'

export interface DictTypeVO {
  id: number
  dictType: string
  dictName: string
  status: number
  remark: string
  createTime: string
  updateTime: string
}

export interface DictItemVO {
  id: number
  dictType: string
  dictLabel: string
  dictValue: string
  sortOrder: number
  status: number
  remark: string
  createTime: string
  updateTime: string
}

// ---- 字典类型 ----
export function listDictTypes(dictName?: string) {
  return request.get<R<DictTypeVO[]>>('/api/v1/base/dict/types', { params: { dictName } })
}

export function createDictType(data: Partial<DictTypeVO>) {
  return request.post<R<DictTypeVO>>('/api/v1/base/dict/types', data)
}

export function updateDictType(id: number, data: Partial<DictTypeVO>) {
  return request.put<R<DictTypeVO>>(`/api/v1/base/dict/types/${id}`, data)
}

export function deleteDictType(id: number) {
  return request.delete<R<void>>(`/api/v1/base/dict/types/${id}`)
}

// ---- 字典项 ----
export function listDictItems(dictType: string) {
  return request.get<R<DictItemVO[]>>('/api/v1/base/dict/items', { params: { dictType } })
}

export function createDictItem(data: Partial<DictItemVO>) {
  return request.post<R<DictItemVO>>('/api/v1/base/dict/items', data)
}

export function updateDictItem(id: number, data: Partial<DictItemVO>) {
  return request.put<R<DictItemVO>>(`/api/v1/base/dict/items/${id}`, data)
}

export function deleteDictItem(id: number) {
  return request.delete<R<void>>(`/api/v1/base/dict/items/${id}`)
}
