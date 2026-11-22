import { reactive } from 'vue'

export type DevRole = 'ADMIN' | 'OPERATOR' | 'STORE'

export const isDevMode = import.meta.env.DEV

export const devActor = reactive<{
  role: DevRole | null
  storeId: string
}>({
  role: isDevMode ? 'ADMIN' : null,
  storeId: '',
})

export function devActorHeaders(): Record<string, string> {
  if (!isDevMode || devActor.role === null) {
    return {}
  }

  const headers: Record<string, string> = { 'X-Dev-Role': devActor.role }
  if (devActor.role === 'STORE' && devActor.storeId.trim() !== '') {
    headers['X-Dev-Store-Id'] = devActor.storeId.trim()
  }
  return headers
}
