import type { DevRole } from '../devActor'

export function canManageStores(role: DevRole | null): boolean {
  return role === 'ADMIN' || role === 'OPERATOR'
}

export function isValidStoreId(value: string): boolean {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(
    value.trim(),
  )
}
