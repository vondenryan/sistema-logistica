import { devActorHeaders } from './devActor'

export interface Store {
  id: string
  name: string
  active: boolean
}

export interface StorePage {
  items: Store[]
  page: number
  size: number
  totalItems: number
}

export interface ProblemDetail {
  code?: string
  detail?: string
  details?: unknown
}

export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly details: unknown

  constructor(
    message: string,
    status: number,
    code: string,
    details: unknown,
  ) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.details = details
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json, application/problem+json')
  if (init.body !== undefined) {
    headers.set('Content-Type', 'application/json')
  }
  for (const [name, value] of Object.entries(devActorHeaders())) {
    headers.set(name, value)
  }

  const response = await fetch(path, { ...init, headers })
  const responseText = await response.text()
  let body: unknown
  if (responseText !== '') {
    try {
      body = JSON.parse(responseText)
    } catch {
      throw new Error(`O servidor retornou uma resposta inválida (HTTP ${response.status}).`)
    }
  }

  if (!response.ok) {
    const problem = isProblemDetail(body) ? body : {}
    throw new ApiError(
      problem.detail ?? `A solicitação falhou (HTTP ${response.status}).`,
      response.status,
      problem.code ?? `HTTP_${response.status}`,
      problem.details,
    )
  }
  if (body === undefined) {
    throw new Error('O servidor retornou uma resposta vazia.')
  }
  return body as T
}

function isProblemDetail(value: unknown): value is ProblemDetail {
  if (typeof value !== 'object' || value === null) {
    return false
  }
  const problem = value as Record<string, unknown>
  return (
    (problem.code === undefined || typeof problem.code === 'string') &&
    (problem.detail === undefined || typeof problem.detail === 'string')
  )
}

export const api = {
  listStores(page: number, size: number): Promise<StorePage> {
    const query = new URLSearchParams({ page: String(page), size: String(size) })
    return request<StorePage>(`/api/v1/stores?${query}`)
  },

  getStore(storeId: string): Promise<Store> {
    return request<Store>(`/api/v1/stores/${encodeURIComponent(storeId)}`)
  },

  createStore(name: string): Promise<Store> {
    return request<Store>('/api/v1/stores', {
      method: 'POST',
      body: JSON.stringify({ name, active: true }),
    })
  },

  patchStore(storeId: string, patch: { name?: string; active?: false }): Promise<Store> {
    return request<Store>(`/api/v1/stores/${encodeURIComponent(storeId)}`, {
      method: 'PATCH',
      body: JSON.stringify(patch),
    })
  },
}

export function formatApiError(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return error instanceof Error ? error.message : 'Ocorreu um erro inesperado.'
  }

  const parts = [`Código ${error.code}: ${error.message}`]
  const details = formatProblemDetails(error.details)
  if (details.length > 0) {
    parts.push(details.join(' '))
  }
  return parts.join(' — ')
}

function formatProblemDetails(value: unknown): string[] {
  if (!Array.isArray(value)) {
    return value === undefined ? [] : [JSON.stringify(value)]
  }

  return value.map((item: unknown) => {
    if (typeof item !== 'object' || item === null) {
      return String(item)
    }
    const detail = item as Record<string, unknown>
    const field = typeof detail.field === 'string' ? `${detail.field}: ` : ''
    const message = typeof detail.message === 'string' ? detail.message : JSON.stringify(item)
    return `${field}${message}`
  })
}
