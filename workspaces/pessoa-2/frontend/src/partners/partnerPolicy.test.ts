import { describe, expect, it } from 'vitest'
import {
  canListPartners,
  canManagePartners,
  canReadPartnerById,
  isValidPartnerId,
} from './partnerPolicy'

describe('autorização de origens e fornecedores', () => {
  it('permite listar, criar e editar somente para ADMIN e OPERATOR', () => {
    for (const role of ['ADMIN', 'OPERATOR'] as const) {
      expect(canListPartners(role)).toBe(true)
      expect(canManagePartners(role)).toBe(true)
    }

    expect(canListPartners('STORE')).toBe(false)
    expect(canManagePartners('STORE')).toBe(false)
    expect(canListPartners(null)).toBe(false)
    expect(canManagePartners(null)).toBe(false)
  })

  it('permite consulta por ID aos três perfis autorizados', () => {
    expect(canReadPartnerById('ADMIN')).toBe(true)
    expect(canReadPartnerById('OPERATOR')).toBe(true)
    expect(canReadPartnerById('STORE')).toBe(true)
    expect(canReadPartnerById(null)).toBe(false)
  })

  it('valida UUIDs de lojas, origens e fornecedores', () => {
    expect(isValidPartnerId('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa')).toBe(true)
    expect(isValidPartnerId('not-a-uuid')).toBe(false)
  })
})
