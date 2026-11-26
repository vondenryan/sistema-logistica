import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api'
import { devActor } from '../devActor'
import DriversView from './DriversView.vue'

const apiMocks = vi.hoisted(() => ({
  listDrivers: vi.fn(),
  createDriver: vi.fn(),
  patchDriver: vi.fn(),
  listAllCarriers: vi.fn(),
}))

vi.mock('../api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api')>()
  return { ...actual, api: apiMocks, listAllCarriers: apiMocks.listAllCarriers }
})

const carrier = {
  id: '77777777-7777-4777-8777-777777777777',
  name: 'Transportadora Alfa',
  active: true,
}
const driver = {
  id: '55555555-5555-4555-8555-555555555555',
  name: 'Motorista Próprio 1',
  active: true,
}

describe('tela de motoristas', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  beforeEach(() => {
    vi.clearAllMocks()
    devActor.role = 'ADMIN'
    apiMocks.listDrivers.mockResolvedValue({
      items: [driver],
      page: 0,
      size: 20,
      totalItems: 1,
    })
    apiMocks.listAllCarriers.mockResolvedValue([carrier])
  })

  it('cria motorista sem transportadora e inativa via PATCH', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const wrapper = mount(DriversView)
    await flushPromises()
    expect(apiMocks.listDrivers).toHaveBeenCalledWith(0, 20)

    await wrapper.get('button.primary-button').trigger('click')
    await wrapper.get('#driver-name').setValue('Motorista Novo')
    await wrapper.get('.store-form-panel form').trigger('submit')
    await flushPromises()
    expect(apiMocks.createDriver).toHaveBeenCalledWith({
      name: 'Motorista Novo',
      active: true,
    })

    await wrapper.get('button.text-button').trigger('click')
    await wrapper.get('#driver-name').setValue('Motorista Atualizado')
    await wrapper.get('.store-form-panel form').trigger('submit')
    await flushPromises()
    expect(apiMocks.patchDriver).toHaveBeenCalledWith(driver.id, {
      name: 'Motorista Atualizado',
    })

    await wrapper.get('button.danger-text').trigger('click')
    await flushPromises()
    expect(apiMocks.patchDriver).toHaveBeenCalledWith(driver.id, { active: false })
  })

  it('inclui carrierId selecionado ao criar motorista terceirizado', async () => {
    const wrapper = mount(DriversView)
    await flushPromises()
    await wrapper.get('button.primary-button').trigger('click')
    await wrapper.get('#driver-name').setValue('Motorista Terceirizado')
    await wrapper.get('#driver-carrier').setValue(carrier.id)
    await wrapper.get('.store-form-panel form').trigger('submit')
    await flushPromises()

    expect(apiMocks.createDriver).toHaveBeenCalledWith({
      name: 'Motorista Terceirizado',
      carrierId: carrier.id,
      active: true,
    })
  })

  it('mostra claramente erro 404 ao salvar transportadora inexistente', async () => {
    apiMocks.createDriver.mockRejectedValue(
      new ApiError('Carrier was not found.', 404, 'NOT_FOUND', undefined),
    )
    const wrapper = mount(DriversView)
    await flushPromises()
    await wrapper.get('button.primary-button').trigger('click')
    await wrapper.get('#driver-name').setValue('Motorista Novo')
    await wrapper.get('#driver-carrier').setValue(carrier.id)
    await wrapper.get('.store-form-panel form').trigger('submit')
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('HTTP 404')
    expect(wrapper.get('[role="alert"]').text()).toContain('NOT_FOUND')
  })

  it('navega páginas usando os parâmetros da API', async () => {
    apiMocks.listDrivers.mockResolvedValue({
      items: [driver],
      page: 0,
      size: 20,
      totalItems: 21,
    })
    const wrapper = mount(DriversView)
    await flushPromises()
    await wrapper.get('.pagination button:last-child').trigger('click')
    await flushPromises()

    expect(apiMocks.listDrivers).toHaveBeenLastCalledWith(1, 20)
  })

  it('não lista nem exibe ações para STORE', async () => {
    devActor.role = 'STORE'
    const wrapper = mount(DriversView)
    await flushPromises()

    expect(wrapper.text()).toContain('não tem acesso')
    expect(wrapper.text()).not.toContain('Cadastrar motorista')
    expect(apiMocks.listDrivers).not.toHaveBeenCalled()
    expect(apiMocks.listAllCarriers).not.toHaveBeenCalled()
  })
})
