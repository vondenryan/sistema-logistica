export function validateStoreName(name: string): string | null {
  if (name.trim().length === 0) {
    return 'Informe o nome da loja.'
  }
  if (name.length > 160) {
    return 'O nome deve ter no máximo 160 caracteres.'
  }
  return null
}
