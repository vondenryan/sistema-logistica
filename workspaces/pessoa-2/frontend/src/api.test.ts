import { describe, expect, it } from 'vitest'
import { ApiError, formatApiError } from './api'

describe('erros ProblemDetail', () => {
  it('mostra o código e os detalhes de validação de forma legível', () => {
    const error = new ApiError('Request validation failed.', 400, 'VALIDATION_ERROR', [
      { field: 'name', message: 'Must not be blank.' },
    ])

    expect(formatApiError(error)).toBe(
      'Código VALIDATION_ERROR: Request validation failed. — name: Must not be blank.',
    )
  })
})
