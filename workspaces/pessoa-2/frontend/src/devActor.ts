import { computed, reactive } from 'vue'

export type DevRole = 'ADMIN' | 'OPERATOR' | 'STORE'
export type Role = DevRole | 'DRIVER'

export const isDevMode = import.meta.env.DEV && import.meta.env.MODE !== 'integration'

const sessionKey = 'logistica.frota'
type Session = {
  accessToken: string
  actor: { role: Role; storeId?: string | null } | null
  expiresAt: string
}

function restoreSession(): Session {
  const empty = { accessToken: '', actor: null, expiresAt: '' }
  if (isDevMode) return empty
  try {
    const saved = JSON.parse(sessionStorage.getItem(sessionKey) ?? 'null') as Session | null
    if (saved?.accessToken && saved.actor && Date.parse(saved.expiresAt) > Date.now()) return saved
  } catch {
    // A malformed session is treated as signed out.
  }
  return empty
}

export const session = reactive<Session>(restoreSession())

export function saveSession(next: Session) {
  Object.assign(session, next)
  sessionStorage.setItem(sessionKey, JSON.stringify(next))
}

export function clearSession() {
  session.accessToken = ''
  session.actor = null
  session.expiresAt = ''
  sessionStorage.removeItem(sessionKey)
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
