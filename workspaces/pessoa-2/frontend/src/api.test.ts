import { afterEach, describe, expect, it, vi } from 'vitest'
import { api, ApiError, formatApiError } from './api'

describe('erros ProblemDetail', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('mostra o código e os detalhes de validação de forma legível', () => {
    const error = new ApiError('Request validation failed.', 400, 'VALIDATION_ERROR', [
      { field: 'name', reason: 'Must not be blank.' },
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
