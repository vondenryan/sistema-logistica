import { computed, shallowRef } from 'vue'

export type DeliveryStatus =
  | 'PENDING'
  | 'ASSIGNED'
  | 'IN_TRANSIT'
  | 'FAILED_ATTEMPT'
  | 'DELIVERED'
  | 'CANCELLED'

export interface DeliverySnapshot {
  deliveryId: string
  orderId: string
  routeId: string | null
  status: DeliveryStatus
  updatedAt: string
  receiverName: string | null
}

export interface DeliveryPage {
  items: DeliverySnapshot[]
  page: number
  size: number
  totalItems: number
}

export interface DeliveryEvent {
  id: string
  deliveryId: string
  fromStatus: DeliveryStatus | null
  toStatus: DeliveryStatus
  actorId: string
  actorRole: string
  reason: string | null
  receiverName: string | null
  occurredAt: string
}

export interface TrackingMilestone {
  status: DeliveryStatus
  at: string
}

export interface PublicTrackingView {
  status: DeliveryStatus
  milestones: TrackingMilestone[]
}

export type DocumentType = 'NFE_SIMULATED' | 'CTE_SIMULATED'

export interface DocumentReceipt {
  documentId: string
  orderId: string
  documentType: DocumentType
  status: 'GENERATED'
}

export interface ProblemDetail {
  type?: string
  title?: string
  status: number
  detail?: string
  code?: string
}

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string
  ) {
    super(message)
  }
}

async function parseResponse<T>(res: Response): Promise<T> {
  if (res.ok) {
    return (await res.json()) as T
  }
  let code = 'INTERNAL_ERROR'
  let message = `Erro HTTP ${res.status}`
  try {
    const problem = (await res.json()) as ProblemDetail
    if (problem.code) code = problem.code
    if (problem.detail) message = problem.detail
  } catch {
    // keep generic fallback
  }
  throw new ApiError(res.status, code, message)
}

interface DeliverySession {
  accessToken: string
  expiresAt: string
}

const session = shallowRef<DeliverySession | null>(null)
let expiryTimer: ReturnType<typeof setTimeout> | undefined

export const deliveryAuthenticated = computed(() => session.value !== null)

export function clearDeliverySession() {
  clearTimeout(expiryTimer)
  session.value = null
}

export function setDeliverySession(next: DeliverySession) {
  const remaining = Date.parse(next.expiresAt) - Date.now()
  if (!next.accessToken || !Number.isFinite(remaining) || remaining <= 0) {
    throw new Error('Sessão expirada. Entre novamente.')
  }
  clearDeliverySession()
  session.value = next
  expiryTimer = setTimeout(clearDeliverySession, remaining)
}

async function authenticatedFetch(path: string, init?: RequestInit) {
  const current = session.value
  if (!current) return init ? fetch(path, init) : fetch(path)
  if (Date.parse(current.expiresAt) <= Date.now()) {
    clearDeliverySession()
    throw new ApiError(401, 'UNAUTHENTICATED', 'Sessão expirada. Entre novamente.')
  }
  const headers = new Headers(init?.headers)
  headers.set('Authorization', `Bearer ${current.accessToken}`)
  const response = await fetch(path, { ...init, headers })
  if (response.status === 401 && session.value === current) clearDeliverySession()
  return response
}

export async function signIn(email: string, password: string) {
  const response = await fetch('/api/v1/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  })
  return parseResponse<DeliverySession>(response)
}

export const deliveriesApi = {
  async listMyDeliveries(page = 0, size = 20): Promise<DeliveryPage> {
    const res = await authenticatedFetch(`/api/v1/deliveries/my?page=${page}&size=${size}`)
    return parseResponse<DeliveryPage>(res)
  },

  async getEvents(deliveryId: string): Promise<DeliveryEvent[]> {
    const res = await authenticatedFetch(`/api/v1/deliveries/${encodeURIComponent(deliveryId)}/events`)
    return parseResponse<DeliveryEvent[]>(res)
  },

  async updateStatus(
    deliveryId: string,
    status: 'IN_TRANSIT' | 'FAILED_ATTEMPT' | 'ASSIGNED',
    reason?: string
  ): Promise<DeliverySnapshot> {
    const payload: Record<string, string> = { status }
    if (reason !== undefined && reason.trim().length > 0) {
      payload.reason = reason.trim()
    }
    const res = await authenticatedFetch(`/api/v1/deliveries/${encodeURIComponent(deliveryId)}/status`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    })
    return parseResponse<DeliverySnapshot>(res)
  },

  async completeDelivery(deliveryId: string, receiverName: string): Promise<DeliverySnapshot> {
    const res = await authenticatedFetch(`/api/v1/deliveries/${encodeURIComponent(deliveryId)}/complete`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ receiverName: receiverName.trim() })
    })
    return parseResponse<DeliverySnapshot>(res)
  },

  async trackByCode(code: string): Promise<PublicTrackingView> {
    const res = await fetch(`/api/v1/tracking/${encodeURIComponent(code.trim())}`)
    return parseResponse<PublicTrackingView>(res)
  },

  async generateDocument(
    orderId: string,
    documentType: DocumentType
  ): Promise<DocumentReceipt> {
    const res = await authenticatedFetch('/api/v1/documents', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ orderId: orderId.trim(), documentType })
    })
    return parseResponse<DocumentReceipt>(res)
  },

  async documentPdf(documentId: string): Promise<Blob> {
    const response = await authenticatedFetch(`/api/v1/documents/${encodeURIComponent(documentId)}/pdf`)
    if (!response.ok) await parseResponse<never>(response)
    return response.blob()
  }
}
