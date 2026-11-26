import type { DevRole } from '../devActor'

export function canManagePartners(role: DevRole | null): boolean {
  return role === 'ADMIN' || role === 'OPERATOR'
}

export function canListPartners(role: DevRole | null): boolean {
  return canManagePartners(role)
}

export function canReadPartnerById(role: DevRole | null): boolean {
  return canManagePartners(role) || role === 'STORE'
}

export function isValidPartnerId(value: string): boolean {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(
    value.trim(),
  )
}
