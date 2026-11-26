import { describe, expect, it } from 'vitest'
import { validateOrigin, type OriginAddressForm } from './originValidation'

const storeId = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'
const address: OriginAddressForm = {
  line1: 'Rua Exemplo, 100',
  line2: '',
  city: 'Cidade',
  state: 'SC',
  postalCode: '00000-000',
  country: 'BR',
  latitude: '-26.48',
  longitude: '-49.07',
}

describe('validação da origem', () => {
  it('aceita AddressSnapshot válido e aplica os limites textuais do contrato', () => {
    expect(validateOrigin('STORE', storeId, address)).toBeNull()
    expect(validateOrigin('DISTRIBUTION_CENTER', storeId, {
      ...address,
      line1: 'A'.repeat(200),
      line2: 'B'.repeat(100),
      city: 'C'.repeat(100),
      postalCode: '12345678',
    })).toBeNull()
  })

  it('rejeita tipo, loja ou campos textuais fora do contrato', () => {
    expect(validateOrigin('UNKNOWN', storeId, address)).toBe('Selecione o tipo da origem.')
    expect(validateOrigin('STORE', 'invalid', address)).toBe('Selecione uma loja existente.')
    expect(validateOrigin('STORE', storeId, { ...address, line1: ' ' })).toBe(
      'Informe o endereço (linha 1).',
    )
    expect(validateOrigin('STORE', storeId, { ...address, line1: 'A'.repeat(201) })).toBe(
      'A linha 1 do endereço deve ter no máximo 200 caracteres.',
    )
    expect(validateOrigin('STORE', storeId, { ...address, line2: 'B'.repeat(101) })).toBe(
      'A linha 2 do endereço deve ter no máximo 100 caracteres.',
    )
    expect(validateOrigin('STORE', storeId, { ...address, city: 'C'.repeat(101) })).toBe(
      'A cidade deve ter no máximo 100 caracteres.',
    )
    expect(validateOrigin('STORE', storeId, { ...address, state: 'sc' })).toBe(
      'Informe a UF com duas letras maiúsculas.',
    )
    expect(validateOrigin('STORE', storeId, { ...address, postalCode: '12345-6789' })).toBe(
      'O CEP deve ter 8 dígitos, com ou sem hífen.',
    )
    expect(validateOrigin('STORE', storeId, { ...address, country: 'US' })).toBe(
      'O país deve ser BR.',
    )
  })

  it('valida limites e presença das coordenadas geográficas', () => {
    expect(validateOrigin('STORE', storeId, { ...address, latitude: '-90', longitude: '180' })).toBeNull()
    expect(validateOrigin('STORE', storeId, { ...address, latitude: '-90.01' })).toBe(
      'A latitude deve estar entre -90 e 90.',
    )
    expect(validateOrigin('STORE', storeId, { ...address, longitude: '180.01' })).toBe(
      'A longitude deve estar entre -180 e 180.',
    )
    expect(validateOrigin('STORE', storeId, { ...address, latitude: '' })).toBe(
      'A latitude deve estar entre -90 e 90.',
    )
  })
})
