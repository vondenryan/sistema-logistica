import type { FleetType } from '../api'

export interface VehicleForm {
  identifier: string
  capacityKg: string
  fleetType: FleetType
  carrierId: string
}

export type VehicleDetails =
  | {
      identifier: string
      capacityKg: string
      fleetType: 'OWN'
    }
  | {
      identifier: string
      capacityKg: string
      fleetType: 'THIRD_PARTY'
      carrierId: string
    }

export function validateCarrierName(name: string): string | null {
  return validateFleetName(name, 'transportadora')
}

export function validateDriverForm(name: string, carrierId: string): string | null {
  const nameError = validateFleetName(name, 'motorista')
  if (nameError !== null) {
    return nameError
  }
  if (carrierId !== '' && !isUuid(carrierId)) {
    return 'Selecione uma transportadora válida.'
  }
  return null
}

export function validateVehicleForm(form: VehicleForm): string | null {
  const identifier = form.identifier.trim()
  if (identifier.length === 0) {
    return 'Informe o identificador do veículo.'
  }
  if (identifier.length > 40) {
    return 'O identificador do veículo deve ter no máximo 40 caracteres.'
  }
  if (!isValidFleetDecimal(form.capacityKg, true)) {
    return 'A capacidade deve ser uma string decimal positiva, com até três casas, compatível com decimal(12,3).'
  }
  if (form.fleetType !== 'OWN' && form.fleetType !== 'THIRD_PARTY') {
    return 'Selecione o tipo de frota.'
  }
  if (form.fleetType === 'THIRD_PARTY' && !isUuid(form.carrierId)) {
    return 'Selecione uma transportadora para a frota terceirizada.'
  }
  return null
}

export function toVehicleDetails(form: VehicleForm): VehicleDetails {
  const details = {
    identifier: form.identifier,
    capacityKg: form.capacityKg,
  }
  if (form.fleetType === 'OWN') {
    return { ...details, fleetType: 'OWN' }
  }
  return { ...details, fleetType: 'THIRD_PARTY', carrierId: form.carrierId }
}

function validateFleetName(name: string, entity: string): string | null {
  const normalizedName = name.trim()
  if (normalizedName.length === 0) {
    return `Informe o nome da ${entity}.`
  }
  if (normalizedName.length > 160) {
    return `O nome da ${entity} deve ter no máximo 160 caracteres.`
  }
  return null
}

export function isValidFleetDecimal(value: string, requireFraction = false): boolean {
  const pattern = requireFraction ? /^\d+\.\d{1,3}$/ : /^\d+(?:\.\d{1,3})?$/
  if (!pattern.test(value)) {
    return false
  }

  const [whole, fraction = ''] = value.split('.')
  const significantWholeDigits = whole.replace(/^0+/, '').length
  const significantDigits = `${whole}${fraction}`.replace(/^0+/, '')
  return significantWholeDigits <= 9 && significantDigits.length <= 12 && /[1-9]/.test(value)
}

function isUuid(value: string): boolean {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(
    value.trim(),
  )
}
