export function validateSupplier(
  name: string,
  contactEmail: string,
  storeId: string,
): string | null {
  if (name.trim().length === 0) {
    return 'Informe o nome do fornecedor.'
  }
  if (name.length > 160) {
    return 'O nome do fornecedor deve ter no máximo 160 caracteres.'
  }
  if (contactEmail.length > 254) {
    return 'O email deve ter no máximo 254 caracteres.'
  }
  if (contactEmail !== '' && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(contactEmail.trim())) {
    return 'Informe um email válido.'
  }
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(
    storeId.trim(),
  )) {
    return 'Selecione uma loja existente.'
  }
  return null
}
