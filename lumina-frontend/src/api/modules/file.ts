/**
 * 文件管理 API
 */
import request from '../request'
import type { R } from '@/types/api'

export interface FileVO {
  fileUuid: string
  originalName: string
  contentType: string
  fileSize: number
  url: string
}

/**
 * 上传文件
 */
export function uploadFile(file: File, bizType = 'chat_image') {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('bizType', bizType)
  return request.post<R<FileVO>>('/api/v1/files/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000
  })
}

/**
 * 获取文件元数据
 */
export function getFileInfo(fileUuid: string) {
  return request.get<R<FileVO>>(`/api/v1/files/${fileUuid}`)
}

/**
 * 删除文件
 */
export function deleteFile(fileUuid: string) {
  return request.delete<R<void>>(`/api/v1/files/${fileUuid}`)
}
