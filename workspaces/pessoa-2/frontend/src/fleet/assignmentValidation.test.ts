import { describe, expect, it } from 'vitest'
import {
  isValidAssignmentRouteId,
  validateFleetAssignment,
} from './assignmentValidation'

const routeId = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'
const originId = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'

describe('validação da atribuição de frota', () => {
  it('aceita qualquer UUID sintaticamente válido para consulta', () => {
    expect(isValidAssignmentRouteId(routeId)).toBe(true)
    expect(isValidAssignmentRouteId('33333333-3333-4333-8333-333333333337')).toBe(true)
    expect(isValidAssignmentRouteId('not-a-uuid')).toBe(false)
  })

  it('valida UUIDs e capacidade positiva com até três casas decimais', () => {
    expect(validateFleetAssignment(routeId, originId, '40.000')).toBeNull()
    expect(validateFleetAssignment(routeId, originId, '40')).toBeNull()
    expect(validateFleetAssignment('invalid', originId, '40.000')).toBe(
      'Informe um UUID válido para consultar a atribuição.',
    )
    expect(validateFleetAssignment(routeId, 'invalid', '40.000')).toBe(
      'Informe um UUID válido para a origem.',
    )
    for (const capacityKg of ['0.000', '-1.000', '1.0000', '1000000000.000']) {
      expect(validateFleetAssignment(routeId, originId, capacityKg)).not.toBeNull()
    }
  })
})
