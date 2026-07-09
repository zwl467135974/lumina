import { describe, it, expect, vi, beforeEach } from 'vitest'
import { flushPromises, shallowMount } from '@vue/test-utils'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key })
}))

vi.mock('@/api/modules/prompt', () => ({
  listPrompts: vi.fn(),
  createPrompt: vi.fn(),
  updatePrompt: vi.fn(),
  publishPrompt: vi.fn(),
  newPromptVersion: vi.fn(),
  deletePrompt: vi.fn(),
  getPromptVersions: vi.fn(),
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() },
  ElMessageBox: { confirm: vi.fn().mockResolvedValue(undefined) },
}))

import { listPrompts, createPrompt, publishPrompt, deletePrompt } from '@/api/modules/prompt'
import PromptIndex from '../index.vue'
import type { R } from '@/types/api'
import type { PromptVO } from '@/api/modules/prompt'

const stubs = { PageHeader: true, 'el-table-column': { template: '<slot :row="{}" />' } }

function res<T>(data: T): R<T> {
  return { code: 200, msg: 'ok', data, timestamp: Date.now() }
}

const mockPrompt: PromptVO = {
  id: 1, name: 'react', version: 1, content: 'test', description: 'd',
  variables: 'task', status: 1, isActive: 1, tenantId: 0,
  createTime: '2024-01-01T00:00:00', updateTime: '2024-01-01T00:00:00',
}

describe('Prompt 管理页', () => {
  beforeEach(() => vi.clearAllMocks())

  it('挂载时加载列表', async () => {
    vi.mocked(listPrompts).mockResolvedValue(res([mockPrompt]))
    shallowMount(PromptIndex, { global: { stubs } })
    await flushPromises()
    expect(listPrompts).toHaveBeenCalled()
  })

  it('查询传入关键词', async () => {
    vi.mocked(listPrompts).mockResolvedValue(res([]))
    const w = shallowMount(PromptIndex, { global: { stubs } })
    const vm = w.vm as any
    await flushPromises()
    vi.mocked(listPrompts).mockClear()
    vm.queryForm.name = 'react'
    await vm.loadList()
    expect(listPrompts).toHaveBeenCalledWith({ name: 'react', pageNum: 1, pageSize: 50 })
  })

  it('发布调用 publishPrompt', async () => {
    vi.mocked(listPrompts).mockResolvedValue(res([mockPrompt]))
    vi.mocked(publishPrompt).mockResolvedValue(res(mockPrompt))
    const w = shallowMount(PromptIndex, { global: { stubs } })
    await flushPromises()
    await (w.vm as any).handlePublish(1)
    expect(publishPrompt).toHaveBeenCalledWith(1)
  })

  it('删除调用 deletePrompt', async () => {
    vi.mocked(listPrompts).mockResolvedValue(res([mockPrompt]))
    vi.mocked(deletePrompt).mockResolvedValue({ code: 200, msg: 'ok', data: undefined as any, timestamp: 0 })
    const w = shallowMount(PromptIndex, { global: { stubs } })
    await flushPromises()
    await (w.vm as any).handleDelete(1)
    expect(deletePrompt).toHaveBeenCalledWith(1)
  })

  it('新建对话框重置表单', async () => {
    vi.mocked(listPrompts).mockResolvedValue(res([]))
    const w = shallowMount(PromptIndex, { global: { stubs } })
    const vm = w.vm as any
    await flushPromises()
    vm.showCreateDialog()
    expect(vm.dialogVisible).toBe(true)
    expect(vm.formData.name).toBe('')
  })

  it('保存调用 createPrompt', async () => {
    vi.mocked(listPrompts).mockResolvedValue(res([]))
    vi.mocked(createPrompt).mockResolvedValue(res(mockPrompt))
    const w = shallowMount(PromptIndex, { global: { stubs } })
    const vm = w.vm as any
    await flushPromises()
    vm.showCreateDialog()
    vm.formData.name = 'cs'
    vm.formData.content = 'prompt'
    await vm.handleSave()
    expect(createPrompt).toHaveBeenCalledWith(expect.objectContaining({ name: 'cs', content: 'prompt' }))
  })

  it('名称为空不保存', async () => {
    vi.mocked(listPrompts).mockResolvedValue(res([]))
    const w = shallowMount(PromptIndex, { global: { stubs } })
    const vm = w.vm as any
    await flushPromises()
    vm.showCreateDialog()
    vm.formData.name = ''
    await vm.handleSave()
    expect(createPrompt).not.toHaveBeenCalled()
  })
})
