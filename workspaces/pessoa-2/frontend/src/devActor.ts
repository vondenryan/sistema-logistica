import { computed, reactive } from 'vue'

export type DevRole = 'ADMIN' | 'OPERATOR' | 'STORE'
export type Role = DevRole | 'DRIVER'

export const isDevMode = import.meta.env.DEV && import.meta.env.MODE !== 'integration'

export const session = reactive<{
  accessToken: string
  actor: { role: Role; storeId?: string | null } | null
}>({ accessToken: '', actor: null })

export function clearSession() {
  session.accessToken = ''
  session.actor = null
}

export const devActor = reactive<{
  role: DevRole | null
  storeId: string
}>({
  role: isDevMode ? 'ADMIN' : null,
  storeId: '',
})

export const currentRole = computed(() => isDevMode ? devActor.role : session.actor?.role ?? null)
export const currentStoreId = computed(() => isDevMode ? devActor.storeId : session.actor?.storeId ?? '')

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
