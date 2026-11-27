import { describe, expect, it } from 'vitest'
import { canManageStores, isValidStoreId } from './storePolicy'
import { validateStoreName } from './storeValidation'

describe('validação do nome da loja', () => {
  it('exige pelo menos um caractere não branco', () => {
    expect(validateStoreName('')).toBe('Informe o nome da loja.')
    expect(validateStoreName('   ')).toBe('Informe o nome da loja.')
  })

  it('aceita nomes de até 160 caracteres e rejeita os maiores', () => {
    expect(validateStoreName('A')).toBeNull()
    expect(validateStoreName('L'.repeat(160))).toBeNull()
    expect(validateStoreName('L'.repeat(161))).toBe('O nome deve ter no máximo 160 caracteres.')
  })
})

describe('autorização da tela de lojas', () => {
  it('permite ações administrativas somente para ADMIN e OPERATOR', () => {
    expect(canManageStores('ADMIN')).toBe(true)
    expect(canManageStores('OPERATOR')).toBe(true)
    expect(canManageStores('STORE')).toBe(false)
    expect(canManageStores(null)).toBe(false)
  })

  it('valida o UUID exigido pelo perfil STORE', () => {
    expect(isValidStoreId('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa')).toBe(true)
    expect(isValidStoreId('not-a-uuid')).toBe(false)
  })
})
