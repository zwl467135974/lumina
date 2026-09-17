/**
 * 语音 API（DashScope paraformer STT + cosyvoice TTS）
 */
import request from '../request'
import type { R } from '@/types/api'

/** 语音转文本（wav 16k 单声道最佳，≤10MB） */
export function transcribeAudio(file: Blob) {
  const form = new FormData()
  form.append('file', file, 'speech.wav')
  return request.post<R<string>>('/api/v1/agents/speech/transcribe', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000
  })
}

/** 文本转语音（返回 16k 单声道 WAV blob） */
export function synthesizeSpeech(text: string, voice?: string) {
  return request.post<Blob>('/api/v1/agents/speech/tts', { text, voice }, {
    responseType: 'blob',
    timeout: 120000
  })
}
