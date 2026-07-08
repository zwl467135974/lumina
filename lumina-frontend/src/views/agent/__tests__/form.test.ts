import { describe, it, expect, vi, beforeEach } from 'vitest'
import { flushPromises, shallowMount } from '@vue/test-utils'

const mockRoute: any = { params: {} }
const mockPush = vi.fn()

vi.mock('vue-router', () => ({
  useRoute: () => mockRoute,
  useRouter: () => ({ push: mockPush }),
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key })
}))

vi.mock('@/api/modules/agent', () => ({
  createAgent: vi.fn(),
  updateAgent: vi.fn(),
  getAgent: vi.fn(),
}))

vi.mock('@/api/modules/prompt', () => ({
  getActivePrompt: vi.fn(),
}))

vi.mock('@/api/modules/tools', () => ({
  getTools: vi.fn().mockResolvedValue({ code: 200, data: [], msg: 'ok', timestamp: 0 }),
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() },
}))

import { getAgent } from '@/api/modules/agent'
import { getActivePrompt } from '@/api/modules/prompt'
import AgentForm from '../form.vue'
import type { R } from '@/types/api'
import type { PromptVO } from '@/api/modules/prompt'

const stubs = { PageHeader: true, 'el-table-column': { template: '<slot :row="{}" />' } }

function res<T>(data: T): R<T> {
  return { code: 200, msg: 'ok', data, timestamp: Date.now() }
}

describe('Agent 表单', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockRoute.params = {}
  })

  it('默认 Agent 类型为 ReAct', async () => {
    vi.mocked(getActivePrompt).mockResolvedValue(res(null as unknown as PromptVO))
    const w = shallowMount(AgentForm, { global: { stubs } })
    await flushPromises()
    expect((w.vm as any).formData.agentType).toBe('ReAct')
  })

  it('切换类型加载 Prompt', async () => {
    vi.mocked(getActivePrompt).mockResolvedValue(res(null as unknown as PromptVO))
    const w = shallowMount(AgentForm, { global: { stubs } })
    const vm = w.vm as any
    await flushPromises()
    vi.mocked(getActivePrompt).mockClear()
    vm.formData.agentType = 'react'
    await flushPromises()
    expect(getActivePrompt).toHaveBeenCalledWith('react')
  })

  it('编辑模式加载详情', async () => {
    mockRoute.params = { id: '1' }
    vi.mocked(getAgent).mockResolvedValue(res({
      agentId: 1, agentName: 'existing', agentType: 'react',
      status: 1, description: 'desc', createTime: '', updateTime: '',
    } as any))
    vi.mocked(getActivePrompt).mockResolvedValue(res(null as unknown as PromptVO))
    shallowMount(AgentForm, { global: { stubs } })
    await flushPromises()
    expect(getAgent).toHaveBeenCalledWith(1)
  })
})
