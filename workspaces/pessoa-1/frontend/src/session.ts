import { reactive } from 'vue'
import { Api, ApiError, type Actor } from './api'
const KEY = 'logistica.session'
export function createSession(storage: Storage = sessionStorage) {
  const state = reactive<{ token: string | null; actor: Actor | null; expiresAt: string | null }>({ token: null, actor: null, expiresAt: null })
  const api = new Api(() => state.token)
  function logout() { state.token = null; state.actor = null; state.expiresAt = null; storage.removeItem(KEY) }
  async function login(email: string, password: string) {
    const result = await api.login(email, password)
    state.token = result.accessToken; state.actor = result.actor; state.expiresAt = result.expiresAt
    storage.setItem(KEY, JSON.stringify({ token: result.accessToken, expiresAt: result.expiresAt }))
  }
  async function restore() {
    try {
      const saved = JSON.parse(storage.getItem(KEY) || 'null') as { token: string; expiresAt: string } | null
      if (!saved || !saved.token || !Number.isFinite(Date.parse(saved.expiresAt)) || Date.parse(saved.expiresAt) <= Date.now()) { logout(); return }
      state.token = saved.token; state.expiresAt = saved.expiresAt; state.actor = await api.me()
    } catch (error) { logout(); if (!(error instanceof ApiError && error.problem.status === 401)) throw error }
  }
  return { state, api, login, logout, restore }
}
