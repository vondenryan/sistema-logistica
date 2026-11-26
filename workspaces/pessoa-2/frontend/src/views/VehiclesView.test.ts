import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api'
import { devActor } from '../devActor'
import VehiclesView from './VehiclesView.vue'

const apiMocks = vi.hoisted(() => ({
  listVehicles: vi.fn(),
  createVehicle: vi.fn(),
  patchVehicle: vi.fn(),
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
const vehicle = {
  id: '66666666-6666-4666-8666-666666666668',
  identifier: 'TER-001',
  capacityKg: '2000.000',
  fleetType: 'THIRD_PARTY' as const,
  carrierId: carrier.id,
  active: true,
}

describe('tela de veículos', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    devActor.role = 'ADMIN'
    apiMocks.listVehicles.mockResolvedValue({
      items: [vehicle],
      page: 0,
      size: 20,
      totalItems: 1,
    })
    apiMocks.listAllCarriers.mockResolvedValue([carrier])
  })

  it('cria frota OWN sem carrierId, troca THIRD_PARTY para OWN e inativa', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const wrapper = mount(VehiclesView)
    await flushPromises()
    expect(apiMocks.listVehicles).toHaveBeenCalledWith(0, 20)

    await wrapper.get('button.primary-button').trigger('click')
    await wrapper.get('#vehicle-identifier').setValue('VEI-003')
    await wrapper.get('#vehicle-capacity').setValue('750.000')
    await wrapper.get('.store-form-panel form').trigger('submit')
    await flushPromises()
    expect(apiMocks.createVehicle).toHaveBeenCalledWith({
      identifier: 'VEI-003',
      capacityKg: '750.000',
      fleetType: 'OWN',
      active: true,
    })

    await wrapper.get('button.text-button').trigger('click')
    await wrapper.get('#vehicle-fleet-type').setValue('OWN')
    await wrapper.get('#vehicle-fleet-type').trigger('change')
    expect(wrapper.find('#vehicle-carrier').exists()).toBe(false)
    await wrapper.get('.store-form-panel form').trigger('submit')
    await flushPromises()
    expect(apiMocks.patchVehicle).toHaveBeenCalledWith(vehicle.id, {
      identifier: vehicle.identifier,
      capacityKg: vehicle.capacityKg,
      fleetType: 'OWN',
    })

    await wrapper.get('button.danger-text').trigger('click')
    await flushPromises()
    expect(apiMocks.patchVehicle).toHaveBeenCalledWith(vehicle.id, { active: false })
  })

  it('mostra claramente erro 409 por identifier duplicado', async () => {
    apiMocks.createVehicle.mockRejectedValue(
      new ApiError('Vehicle identifier already exists.', 409, 'CONFLICT', undefined),
    )
    const wrapper = mount(VehiclesView)
    await flushPromises()
    await wrapper.get('button.primary-button').trigger('click')
    await wrapper.get('#vehicle-identifier').setValue('TER-001')
    await wrapper.get('#vehicle-capacity').setValue('2000.000')
    await wrapper.get('.store-form-panel form').trigger('submit')
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('HTTP 409')
    expect(wrapper.get('[role="alert"]').text()).toContain(
      'Já existe um veículo com esse identifier.',
    )
  })

  it('mostra erros HTTP 400 da API em linguagem clara', async () => {
    apiMocks.createVehicle.mockRejectedValue(
      new ApiError('Request validation failed.', 400, 'VALIDATION_ERROR', [
        { field: 'capacityKg', message: 'Must fit decimal(12,3).' },
      ]),
    )
    const wrapper = mount(VehiclesView)
    await flushPromises()
    await wrapper.get('button.primary-button').trigger('click')
    await wrapper.get('#vehicle-identifier').setValue('VEI-004')
    await wrapper.get('#vehicle-capacity').setValue('1.000')
    await wrapper.get('.store-form-panel form').trigger('submit')
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('HTTP 400')
    expect(wrapper.get('[role="alert"]').text()).toContain('capacityKg')
    expect(wrapper.get('[role="alert"]').text()).toContain('decimal(12,3)')
  })

  it('navega páginas usando os parâmetros da API', async () => {
    apiMocks.listVehicles.mockResolvedValue({
      items: [vehicle],
      page: 0,
      size: 20,
      totalItems: 21,
    })
    const wrapper = mount(VehiclesView)
    await flushPromises()
    await wrapper.get('.pagination button:last-child').trigger('click')
    await flushPromises()

    expect(apiMocks.listVehicles).toHaveBeenLastCalledWith(1, 20)
  })

  it('não lista nem exibe ações para STORE', async () => {
    devActor.role = 'STORE'
    const wrapper = mount(VehiclesView)
    await flushPromises()

    expect(wrapper.text()).toContain('não tem acesso')
    expect(wrapper.text()).not.toContain('Cadastrar veículo')
    expect(apiMocks.listVehicles).not.toHaveBeenCalled()
    expect(apiMocks.listAllCarriers).not.toHaveBeenCalled()
  })
})
