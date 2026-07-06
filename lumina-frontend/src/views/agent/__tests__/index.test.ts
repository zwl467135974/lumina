import { describe, it, expect, vi, beforeEach } from 'vitest'
import { flushPromises, shallowMount } from '@vue/test-utils'

vi.mock('vue-router', () => ({
  useRoute: () => ({}),
  useRouter: () => ({ push: vi.fn() }),
}))

vi.mock('@/api/modules/agent', () => ({
  listAgents: vi.fn(),
  deleteAgent: vi.fn(),
}))

vi.mock('@/api/modules/prompt', () => ({
  getActivePrompt: vi.fn(),
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() },
  ElMessageBox: { confirm: vi.fn().mockResolvedValue(undefined) },
}))

import { listAgents, deleteAgent } from '@/api/modules/agent'
import { getActivePrompt } from '@/api/modules/prompt'
import AgentIndex from '../index.vue'
import type { R, PageResult, AgentVO } from '@/types/api'

const stubs = { PageHeader: true, 'el-table-column': { template: '<slot :row="{}" />' } }

function pageRes(items: AgentVO[]): R<PageResult<AgentVO>> {
  return { code: 200, msg: 'ok', data: { list: items, total: items.length, pageNum: 1, pageSize: 10, pages: 1 }, timestamp: Date.now() }
}

const mockAgent: AgentVO = {
  agentId: 1, agentName: 'test', agentType: 'ReAct', status: 1,
  createTime: '2024-01-01', updateTime: '2024-01-01',
}

describe('Agent 列表', () => {
  beforeEach(() => vi.clearAllMocks())

  it('挂载时加载列表', async () => {
    vi.mocked(listAgents).mockResolvedValue(pageRes([mockAgent]))
    vi.mocked(getActivePrompt).mockResolvedValue({ code: 200, msg: 'ok', data: null, timestamp: 0 } as any)
    shallowMount(AgentIndex, { global: { stubs } })
    await flushPromises()
    expect(listAgents).toHaveBeenCalled()
  })

  it('加载后查询激活 Prompt', async () => {
    vi.mocked(listAgents).mockResolvedValue(pageRes([
      { ...mockAgent, agentType: 'react' },
      { ...mockAgent, agentId: 2, agentType: 'simple' },
    ]))
    vi.mocked(getActivePrompt).mockResolvedValue({ code: 200, msg: 'ok', data: null, timestamp: 0 } as any)
    shallowMount(AgentIndex, { global: { stubs } })
    await flushPromises()
    expect(getActivePrompt).toHaveBeenCalledWith('react')
    expect(getActivePrompt).toHaveBeenCalledWith('simple')
  })

  it('删除调用 deleteAgent', async () => {
    vi.mocked(listAgents).mockResolvedValue(pageRes([mockAgent]))
    vi.mocked(getActivePrompt).mockResolvedValue({ code: 200, msg: 'ok', data: null, timestamp: 0 } as any)
    vi.mocked(deleteAgent).mockResolvedValue({ code: 200, msg: 'ok', data: undefined as any, timestamp: 0 })
    const w = shallowMount(AgentIndex, { global: { stubs } })
    await flushPromises()
    await (w.vm as any).handleDelete(mockAgent)
    expect(deleteAgent).toHaveBeenCalledWith(1)
  })

  it('getPromptKey 转小写', () => {
    const w = shallowMount(AgentIndex, { global: { stubs } })
    const vm = w.vm as any
    expect(vm.getPromptKey('ReAct')).toBe('react')
    expect(vm.getPromptKey(undefined)).toBe('')
  })
})
