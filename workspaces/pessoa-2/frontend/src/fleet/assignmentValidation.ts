import { isValidFleetDecimal } from './fleetValidation'

export function isValidAssignmentRouteId(value: string): boolean {
  return isUuid(value)
}

export function validateFleetAssignment(
  routeId: string,
  originId: string,
  requiredCapacityKg: string,
): string | null {
  if (!isUuid(routeId)) {
    return 'Informe um UUID válido para consultar a atribuição.'
  }
  if (!isUuid(originId)) {
    return 'Informe um UUID válido para a origem.'
  }
  if (!isValidFleetDecimal(requiredCapacityKg)) {
    return 'A capacidade solicitada deve ser positiva, compatível com decimal(12,3) e ter até três casas decimais.'
  }
  return null
}

function isUuid(value: string): boolean {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(
    value.trim(),
  )
}
