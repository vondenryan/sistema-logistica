export type Role = 'ADMIN' | 'OPERATOR' | 'STORE' | 'DRIVER'
export interface Actor { actorId: string; role: Role; storeId: string | null; driverId: string | null }
export interface LoginResponse { accessToken: string; tokenType: 'Bearer'; expiresAt: string; actor: Actor }
export interface ProblemDetail { type: string; title: string; status: number; detail: string; code: string; details: { field: string; reason: string }[]; traceId?: string }
export interface Address { line1: string; line2: string | null; city: string; state: string; postalCode?: string; country: 'BR'; latitude?: number; longitude?: number }
export interface Item { description: string; quantity: number; unitWeightKg: string }
export interface OrderInput { storeId: string; originId: string; destination: Address; recipientName: string; recipientEmail?: string; items: Item[] }
export type OrderStatus = 'DRAFT' | 'CONFIRMED' | 'CANCELLED'
export interface Order extends OrderInput { orderId: string; status: OrderStatus; origin?: Address; trackingCode: string; totalWeightKg: string; freightAmount: string | null; currency: 'BRL'; createdAt: string }
export interface Page<T> { items: T[]; page: number; size: number; totalItems: number }
export class ApiError extends Error { constructor(public problem: ProblemDetail) { super(problem.detail || problem.title) } }
export class Api {
  constructor(private token: () => string | null) {}
  private async request<T>(path: string, method = 'GET', body?: unknown): Promise<T> {
    const token = this.token()
    let response: Response
    try { response = await fetch(`/api/v1${path}`, { method, headers: { ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}), ...(token ? { Authorization: `Bearer ${token}` } : {}) }, ...(body !== undefined ? { body: JSON.stringify(body) } : {}) }) }
    catch { throw new Error('Não foi possível conectar ao servidor. Tente novamente.') }
    if (!response.ok) {
      let problem: ProblemDetail
      try { problem = await response.json() as ProblemDetail } catch { problem = { type: 'about:blank', title: 'Erro', status: response.status, detail: 'Não foi possível concluir a operação.', code: 'UNKNOWN', details: [] } }
      throw new ApiError(problem)
    }
    return response.json() as Promise<T>
  }
  login(email: string, password: string) { return this.request<LoginResponse>('/auth/login', 'POST', { email, password }) }
  me() { return this.request<Actor>('/auth/me') }
  list(page = 0, size = 20) { return this.request<Page<Order>>(`/orders?page=${page}&size=${size}`) }
  get(id: string) { return this.request<Order>(`/orders/${encodeURIComponent(id)}`) }
  create(input: OrderInput) { return this.request<Order>('/orders', 'POST', input) }
  update(id: string, input: OrderInput) { return this.request<Order>(`/orders/${encodeURIComponent(id)}`, 'PATCH', input) }
  confirm(id: string) { return this.request<Order>(`/orders/${encodeURIComponent(id)}/confirm`, 'POST', {}) }
  quote(id: string) { return this.request<FreightQuote>(`/orders/${encodeURIComponent(id)}/freight-quote`, 'POST', {}) }
  suggest(originId: string, orderIds: string[]) { return this.request<Suggestion>('/routes/suggest', 'POST', { originId, orderIds }) }
  createRoute(originId: string, orderIds: string[]) { return this.request<RouteSnapshot>('/routes', 'POST', { originId, orderIds }) }
  getRoute(id: string) { return this.request<RouteSnapshot>(`/routes/${encodeURIComponent(id)}`) }
  assignRoute(id: string) { return this.request<RouteSnapshot>(`/routes/${encodeURIComponent(id)}/assign`, 'POST', {}) }
  startRoute(id: string) { return this.request<RouteSnapshot>(`/routes/${encodeURIComponent(id)}/start`, 'POST', {}) }
  reorderRoute(id: string, orderedOrderIds: string[]) { return this.request<RouteSnapshot>(`/routes/${encodeURIComponent(id)}/stops`, 'PUT', { orderedOrderIds }) }
  cancel(id: string, reason: string) { return this.request<Order>(`/orders/${encodeURIComponent(id)}/cancel`, 'POST', { reason }) }
}

export interface FreightQuote { quoteId: string; orderId: string; distanceKm: string; tariffPerKm: string; amount: string; currency: 'BRL'; calculatedAt: string; source: string }
export type RouteStatus = 'PLANNED' | 'ASSIGNED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'
export interface RouteSnapshot { routeId: string; storeId: string; originId: string; status: RouteStatus; orderIds: string[]; driverId: string | null; vehicleId: string | null; startedAt: string | null }
export interface Suggestion { originId: string; orderedOrderIds: string[]; estimatedDistanceKm: string }
