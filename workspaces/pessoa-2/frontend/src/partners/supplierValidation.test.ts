import { describe, expect, it } from 'vitest'
import { validateSupplier } from './supplierValidation'

const storeId = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'

describe('validação do fornecedor', () => {
  it('aceita email opcional e nome até 160 caracteres', () => {
    expect(validateSupplier('Fornecedor', '', storeId)).toBeNull()
    expect(validateSupplier('F'.repeat(160), 'contato@example.com', storeId)).toBeNull()
  })

  it('rejeita nome em branco, email inválido ou acima de 254 caracteres', () => {
    expect(validateSupplier('  ', '', storeId)).toBe('Informe o nome do fornecedor.')
    expect(validateSupplier('Fornecedor', 'email-invalido', storeId)).toBe(
      'Informe um email válido.',
    )
    expect(validateSupplier('Fornecedor', `${'a'.repeat(248)}@ex.com`, storeId)).toBe(
      'O email deve ter no máximo 254 caracteres.',
    )
    expect(validateSupplier('F'.repeat(161), '', storeId)).toBe(
      'O nome do fornecedor deve ter no máximo 160 caracteres.',
    )
    expect(validateSupplier('Fornecedor', '', 'invalid')).toBe('Selecione uma loja existente.')
  })
})
