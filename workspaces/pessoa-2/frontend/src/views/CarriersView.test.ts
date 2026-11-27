import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api'
import { devActor } from '../devActor'
import CarriersView from './CarriersView.vue'

const apiMocks = vi.hoisted(() => ({
  listCarriers: vi.fn(),
  createCarrier: vi.fn(),
  patchCarrier: vi.fn(),
}))

vi.mock('../api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api')>()
  return { ...actual, api: apiMocks }
})

const carrier = {
  id: '77777777-7777-4777-8777-777777777777',
  name: 'Transportadora Alfa',
  active: true,
}

describe('tela de transportadoras', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  beforeEach(() => {
    vi.clearAllMocks()
    devActor.role = 'ADMIN'
    apiMocks.listCarriers.mockResolvedValue({
      items: [carrier],
      page: 0,
      size: 20,
      totalItems: 1,
    })
  })

  it('lista, cria, edita e inativa transportadoras sem exclusão física', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const wrapper = mount(CarriersView)
    await flushPromises()

    expect(apiMocks.listCarriers).toHaveBeenCalledWith(0, 20)
    expect(wrapper.text()).toContain('Transportadora Alfa')

    await wrapper.get('button.primary-button').trigger('click')
    await wrapper.get('#carrier-name').setValue('Nova Transportadora')
    await wrapper.get('.store-form-panel form').trigger('submit')
    await flushPromises()
    expect(apiMocks.createCarrier).toHaveBeenCalledWith({
      name: 'Nova Transportadora',
      active: true,
    })

    await wrapper.get('button.text-button').trigger('click')
    await wrapper.get('#carrier-name').setValue('Transportadora Renomeada')
    await wrapper.get('.store-form-panel form').trigger('submit')
    await flushPromises()
    expect(apiMocks.patchCarrier).toHaveBeenCalledWith(carrier.id, {
      name: 'Transportadora Renomeada',
    })

    await wrapper.get('button.danger-text').trigger('click')
    await flushPromises()
    expect(apiMocks.patchCarrier).toHaveBeenCalledWith(carrier.id, { active: false })
  })

  it('explica conflito 409 de nome duplicado', async () => {
    apiMocks.createCarrier.mockRejectedValue(
      new ApiError('Carrier name already exists.', 409, 'CONFLICT', undefined),
    )
    const wrapper = mount(CarriersView)
    await flushPromises()
    await wrapper.get('button.primary-button').trigger('click')
    await wrapper.get('#carrier-name').setValue('Duplicada')
    await wrapper.get('.store-form-panel form').trigger('submit')
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('HTTP 409')
    expect(wrapper.get('[role="alert"]').text()).toContain(
      'Já existe uma transportadora com esse nome.',
    )
  })

  it('navega páginas usando os parâmetros da API', async () => {
    apiMocks.listCarriers.mockResolvedValue({
      items: [carrier],
      page: 0,
      size: 20,
      totalItems: 21,
    })
    const wrapper = mount(CarriersView)
    await flushPromises()
    await wrapper.get('.pagination button:last-child').trigger('click')
    await flushPromises()

    expect(apiMocks.listCarriers).toHaveBeenLastCalledWith(1, 20)
  })

  it('não lista nem exibe ações para STORE', async () => {
    devActor.role = 'STORE'
    const wrapper = mount(CarriersView)
    await flushPromises()

    expect(wrapper.text()).toContain('não tem acesso')
    expect(wrapper.text()).not.toContain('Cadastrar transportadora')
    expect(apiMocks.listCarriers).not.toHaveBeenCalled()
  })
})
