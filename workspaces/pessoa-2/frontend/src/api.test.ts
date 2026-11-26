import { afterEach, describe, expect, it, vi } from 'vitest'
import { api, ApiError, formatApiError, listAllCarriers } from './api'

describe('erros ProblemDetail', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('mostra o código e os detalhes de validação de forma legível', () => {
    const error = new ApiError('Request validation failed.', 400, 'VALIDATION_ERROR', [
      { field: 'name', message: 'Must not be blank.' },
    ])

    expect(formatApiError(error)).toBe(
      'Código VALIDATION_ERROR: Request validation failed. — name: Must not be blank.',
    )
  })

  it('mostra detalhes no formato reason definido pelo contrato', () => {
    const error = new ApiError('Request validation failed.', 400, 'VALIDATION_ERROR', [
      { field: 'latitude', reason: 'MUST_BE_IN_RANGE' },
    ])

    expect(formatApiError(error)).toBe(
      'Código VALIDATION_ERROR: Request validation failed. — latitude: MUST_BE_IN_RANGE',
    )
  })
})

describe('endpoints de origens e fornecedores', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('envia atualizações para os endpoints PATCH por ID', async () => {
    const response = { id: 'resource-id' }
    const fetchMock = vi.fn().mockImplementation(
      () => new Response(JSON.stringify(response), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    vi.stubGlobal('fetch', fetchMock)

    await api.patchOrigin('origin-id', { active: false })
    await api.patchSupplier('supplier-id', { name: 'Fornecedor atualizado' })

    expect(fetchMock).toHaveBeenNthCalledWith(
      1,
      '/api/v1/origins/origin-id',
      expect.objectContaining({
        method: 'PATCH',
        body: JSON.stringify({ active: false }),
      }),
    )
    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      '/api/v1/suppliers/supplier-id',
      expect.objectContaining({
        method: 'PATCH',
        body: JSON.stringify({ name: 'Fornecedor atualizado' }),
      }),
    )
  })
})

describe('endpoints de frota', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('usa os endpoints de listagem, consulta, criação, PATCH e atribuição', async () => {
    const fetchMock = vi.fn().mockImplementation(
      () => new Response(JSON.stringify({ id: 'resource-id' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    vi.stubGlobal('fetch', fetchMock)

    await api.listCarriers(0, 20)
    await api.getCarrier('carrier-id')
    await api.createCarrier({ name: 'Alfa', active: true })
    await api.patchCarrier('carrier-id', { active: false })
    await api.listDrivers(0, 20)
    await api.getDriver('driver-id')
    await api.createDriver({ name: 'Motorista', active: true })
    await api.patchDriver('driver-id', { carrierId: 'carrier-id' })
    await api.listVehicles(0, 20)
    await api.getVehicle('vehicle-id')
    await api.createVehicle({
      identifier: 'VEI-001',
      capacityKg: '1000.000',
      fleetType: 'OWN',
      active: true,
    })
    await api.patchVehicle('vehicle-id', { active: false })
    await api.getFleetAssignment('route-id')
    await api.createFleetAssignment({
      routeId: 'route-id',
      originId: 'origin-id',
      requiredCapacityKg: '40.000',
    })

    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual([
      '/api/v1/carriers?page=0&size=20',
      '/api/v1/carriers/carrier-id',
      '/api/v1/carriers',
      '/api/v1/carriers/carrier-id',
      '/api/v1/drivers?page=0&size=20',
      '/api/v1/drivers/driver-id',
      '/api/v1/drivers',
      '/api/v1/drivers/driver-id',
      '/api/v1/vehicles?page=0&size=20',
      '/api/v1/vehicles/vehicle-id',
      '/api/v1/vehicles',
      '/api/v1/vehicles/vehicle-id',
      '/api/v1/fleet/assignments/route-id',
      '/api/v1/fleet/assignments',
    ])
    expect(fetchMock.mock.calls[2][1]).toEqual(expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ name: 'Alfa', active: true }),
    }))
    expect(fetchMock.mock.calls[3][1]).toEqual(expect.objectContaining({
      method: 'PATCH',
      body: JSON.stringify({ active: false }),
    }))
    expect(fetchMock.mock.calls[10][1]).toEqual(expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({
        identifier: 'VEI-001',
        capacityKg: '1000.000',
        fleetType: 'OWN',
        active: true,
      }),
    }))
    expect(fetchMock.mock.calls[13][1]).toEqual(expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({
        routeId: 'route-id',
        originId: 'origin-id',
        requiredCapacityKg: '40.000',
      }),
    }))
  })

  it('carrega transportadoras de todas as páginas para os seletores', async () => {
    const items = Array.from({ length: 101 }, (_, index) => ({
      id: `carrier-${index}`,
      name: `Transportadora ${index}`,
      active: true,
    }))
    const fetchMock = vi.fn().mockImplementation((input: RequestInfo | URL) => {
      const url = new URL(String(input), 'http://localhost')
      const page = Number(url.searchParams.get('page'))
      const pageItems = page === 0 ? items.slice(0, 100) : items.slice(100)
      return new Response(JSON.stringify({
        items: pageItems,
        page,
        size: 100,
        totalItems: items.length,
      }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      })
    })
    vi.stubGlobal('fetch', fetchMock)

    const carriers = await listAllCarriers()

    expect(carriers).toHaveLength(101)
    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(fetchMock.mock.calls[1][0]).toBe('/api/v1/carriers?page=1&size=100')
  })
})
