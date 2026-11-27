import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { devActor } from '../devActor'
import StoresView from './StoresView.vue'

const apiMocks = vi.hoisted(() => ({
  listStores: vi.fn(),
  getStore: vi.fn(),
  createStore: vi.fn(),
  patchStore: vi.fn(),
}))

vi.mock('../api', () => ({
  api: apiMocks,
  formatApiError: (error: unknown) =>
    error instanceof Error ? error.message : 'Ocorreu um erro inesperado.',
}))

const store = { id: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', name: 'Loja Central', active: true }

describe('autorização da tela de lojas', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  beforeEach(() => {
    vi.clearAllMocks()
    devActor.role = 'ADMIN'
    devActor.storeId = ''
    apiMocks.listStores.mockResolvedValue({
      items: [store],
      page: 0,
      size: 20,
      totalItems: 1,
    })
    apiMocks.getStore.mockResolvedValue(store)
  })

  it('exibe ações de cadastro para ADMIN', async () => {
    const wrapper = mount(StoresView)
    await flushPromises()

    expect(wrapper.text()).toContain('Cadastrar loja')
    expect(wrapper.get('button.text-button').text()).toBe('Editar')
    expect(wrapper.text()).toContain('Inativar')
  })

  it('não exibe ações de criação ou alteração para STORE', async () => {
    devActor.role = 'STORE'
    devActor.storeId = store.id
    const wrapper = mount(StoresView)
    await flushPromises()

    expect(wrapper.text()).toContain('Minha loja')
    expect(wrapper.text()).not.toContain('Cadastrar loja')
    expect(wrapper.text()).not.toContain('Editar')
    expect(wrapper.text()).not.toContain('Inativar')
    expect(apiMocks.getStore).toHaveBeenCalledWith(store.id)
    expect(apiMocks.listStores).not.toHaveBeenCalled()
  })

  it('inativa a loja com PATCH active=false', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    apiMocks.patchStore.mockResolvedValue({ ...store, active: false })
    const wrapper = mount(StoresView)
    await flushPromises()

    await wrapper.get('button.danger-text').trigger('click')
    await flushPromises()

    expect(apiMocks.patchStore).toHaveBeenCalledWith(store.id, { active: false })
  })
})
