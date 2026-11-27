import type { OriginType } from '../api'

export interface OriginAddressForm {
  line1: string
  line2: string
  city: string
  state: string
  postalCode: string
  country: string
  latitude: string
  longitude: string
}

export function validateOrigin(
  type: string,
  storeId: string,
  address: OriginAddressForm,
): string | null {
  if (type !== 'STORE' && type !== 'DISTRIBUTION_CENTER') {
    return 'Selecione o tipo da origem.'
  }
  if (!isUuid(storeId)) {
    return 'Selecione uma loja existente.'
  }
  if (address.line1.trim().length === 0) {
    return 'Informe o endereço (linha 1).'
  }
  if (address.line1.length > 200) {
    return 'A linha 1 do endereço deve ter no máximo 200 caracteres.'
  }
  if (address.line2.length > 100) {
    return 'A linha 2 do endereço deve ter no máximo 100 caracteres.'
  }
  if (address.city.trim().length === 0) {
    return 'Informe a cidade.'
  }
  if (address.city.length > 100) {
    return 'A cidade deve ter no máximo 100 caracteres.'
  }
  if (!/^[A-Z]{2}$/.test(address.state)) {
    return 'Informe a UF com duas letras maiúsculas.'
  }
  if (address.postalCode !== '' && !/^(\d{8}|\d{5}-\d{3})$/.test(address.postalCode)) {
    return 'O CEP deve ter 8 dígitos, com ou sem hífen.'
  }
  if (address.country !== 'BR') {
    return 'O país deve ser BR.'
  }

  const latitude = Number(address.latitude)
  if (address.latitude.trim() === '' || !Number.isFinite(latitude) || latitude < -90 || latitude > 90) {
    return 'A latitude deve estar entre -90 e 90.'
  }
  const longitude = Number(address.longitude)
  if (
    address.longitude.trim() === '' ||
    !Number.isFinite(longitude) ||
    longitude < -180 ||
    longitude > 180
  ) {
    return 'A longitude deve estar entre -180 e 180.'
  }
  return null
}

export function isOriginType(value: string): value is OriginType {
  return value === 'STORE' || value === 'DISTRIBUTION_CENTER'
}

function isUuid(value: string): boolean {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(
    value.trim(),
  )
}
