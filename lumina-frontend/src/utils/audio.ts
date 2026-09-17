/**
 * 音频工具：录音 Blob → 16k 单声道 16bit WAV（paraformer 最佳输入格式）
 *
 * 浏览器 MediaRecorder 产出 webm/opus（Safari 为 mp4/aac），服务端 ASR 以
 * WAV 16k 最稳，故在前端统一解码重采样编码，消除服务端格式分支。
 */

/** AudioContext 类型兼容（Safari 前缀） */
function createAudioContext(sampleRate: number): AudioContext {
  const Ctor = window.AudioContext || (window as any).webkitAudioContext
  return new Ctor({ sampleRate })
}

function createOfflineContext(channels: number, length: number, sampleRate: number): OfflineAudioContext {
  const Ctor = window.OfflineAudioContext || (window as any).webkitOfflineAudioContext
  return new Ctor(channels, length, sampleRate)
}

/** 任意音频 Blob → 16k 单声道 AudioBuffer */
async function decodeToMono16k(blob: Blob): Promise<AudioBuffer> {
  const arrayBuffer = await blob.arrayBuffer()
  const decodeContext = createAudioContext(16000)
  try {
    const decoded = await decodeContext.decodeAudioData(arrayBuffer.slice(0))
    if (decoded.sampleRate === 16000 && decoded.numberOfChannels === 1) {
      return decoded
    }
    // 重采样 + 下混为 16k 单声道
    const frames = Math.ceil(decoded.length * (16000 / decoded.sampleRate))
    const offline = createOfflineContext(1, frames, 16000)
    const source = offline.createBufferSource()
    source.buffer = decoded
    source.connect(offline.destination)
    source.start()
    return await offline.startRendering()
  } finally {
    void decodeContext.close()
  }
}

/** AudioBuffer → 16bit PCM WAV Blob */
function encodeWav(buffer: AudioBuffer): Blob {
  const channels = buffer.numberOfChannels
  const sampleRate = buffer.sampleRate
  const frames = buffer.length
  const bytesPerSample = 2
  const blockAlign = channels * bytesPerSample
  const dataSize = frames * blockAlign
  const arrayBuffer = new ArrayBuffer(44 + dataSize)
  const view = new DataView(arrayBuffer)

  const writeString = (offset: number, text: string) => {
    for (let i = 0; i < text.length; i++) {
      view.setUint8(offset + i, text.charCodeAt(i))
    }
  }

  writeString(0, 'RIFF')
  view.setUint32(4, 36 + dataSize, true)
  writeString(8, 'WAVE')
  writeString(12, 'fmt ')
  view.setUint32(16, 16, true)
  view.setUint16(20, 1, true) // PCM
  view.setUint16(22, channels, true)
  view.setUint32(24, sampleRate, true)
  view.setUint32(28, sampleRate * blockAlign, true)
  view.setUint16(32, blockAlign, true)
  view.setUint16(34, 16, true)
  writeString(36, 'data')
  view.setUint32(40, dataSize, true)

  const channelData: Float32Array[] = []
  for (let ch = 0; ch < channels; ch++) {
    channelData.push(buffer.getChannelData(ch))
  }
  let offset = 44
  for (let i = 0; i < frames; i++) {
    // 多声道取平均下混
    let sample = 0
    for (let ch = 0; ch < channels; ch++) {
      sample += channelData[ch][i]
    }
    sample /= channels
    const clamped = Math.max(-1, Math.min(1, sample))
    view.setInt16(offset, clamped < 0 ? clamped * 0x8000 : clamped * 0x7fff, true)
    offset += bytesPerSample
  }
  return new Blob([arrayBuffer], { type: 'audio/wav' })
}

/** 录音 Blob（webm/mp4 等）→ 16k 单声道 WAV Blob */
export async function audioBlobToWav16k(blob: Blob): Promise<Blob> {
  return encodeWav(await decodeToMono16k(blob))
}
