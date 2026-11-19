import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { Api, ApiError } from '../src/api'
import RoutesView from '../src/RoutesView.vue'

const originId = '11111111-1111-4111-8111-111111111111', storeId = '22222222-2222-4222-8222-222222222222', orderId = '44444444-4444-4444-8444-444444444444', routeId = '33333333-3333-4333-8333-333333333333'
const order = { orderId, originId, storeId, status:'CONFIRMED', recipientName:'Cliente', totalWeightKg:'40.000' }
const route = { routeId, originId, storeId, status:'PLANNED', orderIds:[orderId], driverId:null, vehicleId:null, startedAt:null }
const quote = { quoteId:'88888888-8888-4888-8888-888888888888', orderId, distanceKm:'12.000', tariffPerKm:'2.50', amount:'30.00', currency:'BRL', calculatedAt:'2026-11-14T12:00:00.000Z', source:'OSRM' }
const problem = { type:'https://example.invalid/problems/dependency-unavailable', title:'Indisponível', status:503, detail:'Roteamento indisponível', code:'DEPENDENCY_UNAVAILABLE', details:[] }
function fake() {
 const calls: {path:string; method:string; body:unknown}[] = []
 vi.stubGlobal('fetch', vi.fn(async (url: string, init: RequestInit) => {
  const path = String(url), method = init.method || 'GET', body = init.body ? JSON.parse(String(init.body)) : undefined
  calls.push({path, method, body})
  let data: unknown = route, status = 200
  if (path.includes('/orders?')) data = {items:[order], page:0, size:20, totalItems:1}
  else if (path.endsWith('/freight-quote')) data = quote
  else if (path.endsWith('/suggest')) {data = problem; status = 503}
  else if (path.endsWith('/routes') && method === 'POST') status = 201
  else if (path.endsWith('/assign')) data = {...route,status:'ASSIGNED',driverId:'driver',vehicleId:'vehicle'}
  else if (path.endsWith('/start')) data = {...route,status:'IN_PROGRESS',driverId:'driver',vehicleId:'vehicle',startedAt:'2026-11-14T12:00:00.000Z'}
  return new Response(JSON.stringify(data),{status,headers:{'Content-Type':status >= 400 ? 'application/problem+json':'application/json'}})
 }))
 return calls
}
beforeEach(() => vi.unstubAllGlobals())
describe('Sprint 2 HTTP v1', () => {
 it('envia os corpos exatos e mantém valores decimais como strings', async () => {
  const calls = fake(), api = new Api(() => 'fixture')
  expect(await api.quote(orderId)).toEqual(quote)
  await expect(api.suggest(originId,[orderId])).rejects.toMatchObject({problem:{code:'DEPENDENCY_UNAVAILABLE'}})
  await api.createRoute(originId,[orderId]); await api.getRoute(routeId); await api.assignRoute(routeId); await api.startRoute(routeId); await api.reorderRoute(routeId,[orderId])
  expect(calls.map(c => [c.method,c.path,c.body])).toEqual([
   ['POST',`/api/v1/orders/${orderId}/freight-quote`,{}],['POST','/api/v1/routes/suggest',{originId,orderIds:[orderId]}],['POST','/api/v1/routes',{originId,orderIds:[orderId]}],['GET',`/api/v1/routes/${routeId}`,undefined],['POST',`/api/v1/routes/${routeId}/assign`,{}],['POST',`/api/v1/routes/${routeId}/start`,{}],['PUT',`/api/v1/routes/${routeId}/stops`,{orderedOrderIds:[orderId]}]
  ])
  expect(typeof quote.amount).toBe('string'); expect(typeof quote.distanceKm).toBe('string')
 })
 it('preserva ProblemDetail de tarifa ausente', async () => { vi.stubGlobal('fetch',vi.fn(async () => new Response(JSON.stringify({...problem,status:422,code:'PRECONDITION_FAILED',detail:'Tarifa não configurada'}),{status:422}))); await expect(new Api(()=>'x').quote(orderId)).rejects.toBeInstanceOf(ApiError) })
 it('permite criar após falha da sugestão', async () => { const calls=fake(); const w=mount(RoutesView,{props:{api:new Api(()=>'x'),role:'OPERATOR'}}); await flushPromises(); await w.get('input[type=checkbox]').setValue(true); await w.findAll('button').find(b=>b.text()==='Sugerir ordem')!.trigger('click'); await flushPromises(); expect(w.get('[role=alert]').text()).toContain('Roteamento indisponível'); await w.findAll('button').find(b=>b.text()==='Criar rota')!.trigger('click'); await flushPromises(); expect(calls.at(-1)?.body).toEqual({originId,orderIds:[orderId]}); expect(w.text()).toContain('Rota criada') })
 it('atribui e inicia rota e bloqueia edição após o início', async () => {
  const calls=fake(); const w=mount(RoutesView,{props:{api:new Api(()=>'x'),role:'OPERATOR'}}); await flushPromises()
  await w.get('input[type=checkbox]').setValue(true); await w.findAll('button').find(b=>b.text()==='Criar rota')!.trigger('click'); await flushPromises()
  expect(w.text()).toContain('Status: PLANNED'); await w.findAll('button').find(b=>b.text()==='Atribuir motorista e veículo')!.trigger('click'); await flushPromises()
  expect(w.text()).toContain('Status: ASSIGNED'); expect(w.text()).toContain('Motorista: driver'); expect(w.text()).toContain('Veículo: vehicle')
  await w.findAll('button').find(b=>b.text()==='Iniciar rota')!.trigger('click'); await flushPromises()
  expect(w.text()).toContain('Status: IN_PROGRESS'); expect(w.text()).not.toContain('Salvar ordem'); expect(w.text()).not.toContain('Iniciar rota')
  expect(calls.slice(-2).map(c=>[c.path,c.body])).toEqual([[`/api/v1/routes/${routeId}/assign`,{}],[`/api/v1/routes/${routeId}/start`,{}]])
 })
 it('motorista consulta somente por ID sem listar pedidos nem mostrar comandos', async () => { const calls=fake(); const w=mount(RoutesView,{props:{api:new Api(()=>'x'),role:'DRIVER'}}); await w.get('input').setValue(routeId); await w.get('form').trigger('submit'); await flushPromises(); expect(calls.map(c=>c.path)).toEqual([`/api/v1/routes/${routeId}`]); expect(w.text()).not.toContain('Criar rota'); expect(w.text()).not.toContain('Salvar ordem') })
})
