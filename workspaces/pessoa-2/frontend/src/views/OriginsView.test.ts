import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { devActor } from '../devActor'
import OriginsView from './OriginsView.vue'

const apiMocks = vi.hoisted(() => ({
  listOrigins: vi.fn(),
  getOrigin: vi.fn(),
  createOrigin: vi.fn(),
  patchOrigin: vi.fn(),
  listAllStores: vi.fn(),
}))

vi.mock('../api', () => ({
  api: apiMocks,
  formatApiError: (error: unknown) =>
    error instanceof Error ? error.message : 'Ocorreu um erro inesperado.',
  listAllStores: apiMocks.listAllStores,
}))

const storeId = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'
const origin = {
  id: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
  type: 'STORE' as const,
  storeId,
  address: {
    line1: 'Rua Exemplo, 100',
    line2: null,
    city: 'Cidade',
    state: 'SC',
    postalCode: '00000-000',
    country: 'BR',
    latitude: -26.48,
    longitude: -49.07,
  },
  active: true,
}

describe('autorização da tela de origens', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    devActor.role = 'ADMIN'
    devActor.storeId = ''
    apiMocks.listOrigins.mockResolvedValue({
      items: [origin],
      page: 0,
      size: 20,
      totalItems: 1,
    })
    apiMocks.listAllStores.mockResolvedValue([{ id: storeId, name: 'Loja Central', active: true }])
    apiMocks.getOrigin.mockResolvedValue(origin)
  })

  it('permite operações e inativação para ADMIN', async () => {
    const wrapper = mount(OriginsView)
    await flushPromises()

    expect(wrapper.text()).toContain('Cadastrar origem')
    expect(wrapper.text()).toContain('Editar')
    expect(wrapper.text()).toContain('Inativar')
    expect(apiMocks.listOrigins).toHaveBeenCalledWith(0, 20)
  })

  it('para STORE oculta listagem e ações e consulta somente por ID', async () => {
    devActor.role = 'STORE'
    devActor.storeId = storeId
    const wrapper = mount(OriginsView)
    await flushPromises()

    expect(wrapper.text()).toContain('Consultar origem da minha loja')
    expect(wrapper.text()).not.toContain('Origens cadastradas')
    expect(wrapper.text()).not.toContain('Cadastrar origem')
    expect(wrapper.text()).not.toContain('Editar')
    expect(wrapper.text()).not.toContain('Inativar')
    expect(apiMocks.listOrigins).not.toHaveBeenCalled()
    expect(apiMocks.listAllStores).not.toHaveBeenCalled()

    await wrapper.get('#origin-lookup-id').setValue(origin.id)
    await wrapper.get('.lookup-form').trigger('submit')
    await flushPromises()

    expect(apiMocks.getOrigin).toHaveBeenCalledWith(origin.id)
    expect(wrapper.text()).toContain('Rua Exemplo, 100')
  })
})
