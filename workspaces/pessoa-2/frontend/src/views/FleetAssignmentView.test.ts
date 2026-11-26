import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api'
import { devActor } from '../devActor'
import FleetAssignmentView from './FleetAssignmentView.vue'

const apiMocks = vi.hoisted(() => ({
  getFleetAssignment: vi.fn(),
  getDriver: vi.fn(),
  getVehicle: vi.fn(),
  getCarrier: vi.fn(),
  createFleetAssignment: vi.fn(),
}))

vi.mock('../api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api')>()
  return { ...actual, api: apiMocks }
})

const routeId = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'
const carrierId = '77777777-7777-4777-8777-777777777777'
const snapshot = {
  assignmentId: 'cccccccc-cccc-4ccc-8ccc-cccccccccccc',
  routeId,
  driverId: '55555555-5555-4555-8555-555555555557',
  vehicleId: '66666666-6666-4666-8666-666666666668',
  carrierId,
  fleetType: 'THIRD_PARTY' as const,
}

describe('tela de atribuição de frota', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    devActor.role = 'ADMIN'
    apiMocks.getFleetAssignment.mockResolvedValue(snapshot)
    apiMocks.getDriver.mockResolvedValue({
      id: snapshot.driverId,
      name: 'Motorista Terceirizado 1',
      carrierId,
      active: true,
    })
    apiMocks.getVehicle.mockResolvedValue({
      id: snapshot.vehicleId,
      identifier: 'TER-001',
      capacityKg: '2000.000',
      fleetType: 'THIRD_PARTY',
      carrierId,
      active: true,
    })
    apiMocks.getCarrier.mockResolvedValue({
      id: carrierId,
      name: 'Transportadora Parceira Alfa',
      active: true,
    })
  })

  it('consulta qualquer UUID e exibe motorista, veículo, transportadora e tipo', async () => {
    const wrapper = mount(FleetAssignmentView)
    await wrapper.get('#assignment-route-id').setValue(routeId)
    await wrapper.get('.lookup-form').trigger('submit')
    await flushPromises()

    expect(apiMocks.getFleetAssignment).toHaveBeenCalledWith(routeId)
    expect(wrapper.text()).toContain('Motorista Terceirizado 1')
    expect(wrapper.text()).toContain('TER-001')
    expect(wrapper.text()).toContain('Transportadora Parceira Alfa')
    expect(wrapper.text()).toContain('Terceirizada (THIRD_PARTY)')
  })

  it('envia valores editáveis ao POST de demonstração e exibe o resultado', async () => {
    apiMocks.createFleetAssignment.mockResolvedValue(snapshot)
    const wrapper = mount(FleetAssignmentView)
    await wrapper.get('#demo-capacity').setValue('125.500')
    await wrapper.get('.store-form-panel form').trigger('submit')
    await flushPromises()

    expect(apiMocks.createFleetAssignment).toHaveBeenCalledWith({
      routeId: '33333333-3333-4333-8333-333333333333',
      originId: '11111111-1111-4111-8111-111111111111',
      requiredCapacityKg: '125.500',
    })
    expect(wrapper.text()).toContain('Atribuição criada.')
    expect(wrapper.text()).toContain('Transportadora Parceira Alfa')
  })

  it('mostra mensagens próprias para falhas de atribuição', async () => {
    const errors = [
      {
        error: new ApiError('No eligible resource.', 409, 'NO_ELIGIBLE_RESOURCE', undefined),
        message: 'Não há motorista e veículo elegíveis',
      },
      {
        error: new ApiError('Idempotency conflict.', 409, 'IDEMPOTENCY_CONFLICT', undefined),
        message: 'Esta rota já foi atribuída com dados diferentes',
      },
      {
        error: new ApiError('Not found.', 404, 'NOT_FOUND', undefined),
        message: 'Não foi encontrada uma atribuição, rota ou origem',
      },
    ]
    for (const { error } of errors) {
      apiMocks.createFleetAssignment.mockRejectedValueOnce(error)
    }
    const wrapper = mount(FleetAssignmentView)

    for (const { message } of errors) {
      await wrapper.get('.store-form-panel form').trigger('submit')
      await flushPromises()
      expect(wrapper.get('.store-form-panel [role="alert"]').text()).toContain(message)
    }
  })

  it('não mostra consulta ou formulário para STORE', async () => {
    devActor.role = 'STORE'
    const wrapper = mount(FleetAssignmentView)
    await flushPromises()

    expect(wrapper.text()).toContain('não tem acesso')
    expect(wrapper.find('#demo-assignment-title').exists()).toBe(false)
    expect(wrapper.find('#lookup-title').exists()).toBe(false)
    expect(apiMocks.getFleetAssignment).not.toHaveBeenCalled()
    expect(apiMocks.createFleetAssignment).not.toHaveBeenCalled()
  })
})
