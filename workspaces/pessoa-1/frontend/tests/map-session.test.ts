import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import App from '../src/App.vue'
import { createSession } from '../src/session'

const events = vi.hoisted(() => ({ click: undefined as undefined | ((event: {latlng:{lat:number;lng:number}}) => void), tileerror: undefined as undefined | (() => void) }))
vi.mock('leaflet', () => {
 const map = { setView: vi.fn(function () { return map }), on: vi.fn((_name: string, fn: typeof events.click) => { events.click = fn }), remove: vi.fn() }
 return { map: vi.fn(() => map), tileLayer: vi.fn(() => ({ on: (_name: string, fn: () => void) => { events.tileerror = fn }, addTo: vi.fn() })), circleMarker: vi.fn(() => ({ addTo: vi.fn(function () { return { remove: vi.fn() } }) })) }
})
const storeId='22222222-2222-4222-8222-222222222222', originId='11111111-1111-4111-8111-111111111111', orderId='44444444-4444-4444-8444-444444444444'
const actor = {actorId:'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',role:'STORE',storeId,driverId:null}
const order = {orderId,storeId,originId,status:'CONFIRMED',destination:{line1:'Rua A',line2:null,city:'Cidade',state:'SC',country:'BR',latitude:-26,longitude:-49},recipientName:'Cliente',items:[{description:'Caixa',quantity:1,unitWeightKg:'1.000'}],totalWeightKg:'1.000',freightAmount:null,currency:'BRL',trackingCode:'TRK',createdAt:'2026-11-14T12:00:00.000Z'}
const quote = {quoteId:'88888888-8888-4888-8888-888888888888',orderId,distanceKm:'12.000',tariffPerKm:'2.50',amount:'30.00',currency:'BRL',calculatedAt:'2026-11-14T12:00:00.000Z',source:'FAKE'}
function fake(expiresAt='2099-01-01T00:00:00.000Z') {
 const calls: {path:string;body:any}[]=[]
 vi.stubGlobal('fetch', vi.fn(async (url: string, init: RequestInit) => {
  const path=String(url), body=init.body ? JSON.parse(String(init.body)) : undefined; calls.push({path,body})
  let result: unknown = order
  if(path.endsWith('/auth/login')) result={accessToken:'fixture',tokenType:'Bearer',expiresAt,actor}
  else if(path.includes('/orders?')) result={items:[order],page:0,size:20,totalItems:1}
  else if(path.endsWith('/freight-quote')) result=quote
  return new Response(JSON.stringify(result),{status:200,headers:{'Content-Type':'application/json'}})
 }))
 return calls
}
async function login(wrapper: ReturnType<typeof mount>) { await wrapper.get('input[type=email]').setValue('store@example.invalid'); await wrapper.get('input[type=password]').setValue('fixture'); await wrapper.get('form').trigger('submit'); await flushPromises() }
beforeEach(() => { sessionStorage.clear(); events.click=undefined; events.tileerror=undefined })
afterEach(() => { vi.useRealTimers(); vi.unstubAllGlobals() })
describe('mapa e cotação na interface', () => {
 it('aplica clique do mapa e aceita coordenadas manuais quando os blocos falham', async () => {
  const calls=fake(); const w=mount(App); await flushPromises(); await login(w)
  await w.findAll('button').find(b=>b.text().includes('Novo pedido'))!.trigger('click')
  expect(events.click).toBeTypeOf('function'); expect(events.tileerror).toBeTypeOf('function')
  events.click!({latlng:{lat:-26.1234567,lng:-49.7654321}}); await w.vm.$nextTick()
  const lat=w.get('input[inputmode=decimal]'); const lon=w.findAll('input[inputmode=decimal]')[1]
  expect((lat.element as HTMLInputElement).value).toBe('-26.123457'); expect((lon.element as HTMLInputElement).value).toBe('-49.765432')
  events.tileerror!(); await w.vm.$nextTick(); expect(w.text()).toContain('Mapa indisponível')
  await lat.setValue('-25.5'); await lon.setValue('-48.75'); await w.get('form').trigger('submit'); await flushPromises()
  const create=calls.find(c=>c.path.endsWith('/orders') && c.body)
  expect(create?.body.destination.latitude).toBe(-25.5); expect(create?.body.destination.longitude).toBe(-48.75)
  w.unmount()
 })
 it('identifica cotação com distância simulada', async () => {
  fake(); const w=mount(App); await flushPromises(); await login(w); await w.get('tbody button').trigger('click'); await flushPromises()
  await w.findAll('button').find(b=>b.text()==='Consultar cotação de frete')!.trigger('click'); await flushPromises()
  expect(w.text()).toContain('Distância simulada para desenvolvimento'); expect(w.text()).not.toContain('FAKE'); expect(w.text()).toContain('30.00'); w.unmount()
 })
})
describe('isolamento da cotação por pedido', () => {
 const secondOrderId = '44444444-4444-4444-8444-444444444445'
 const secondQuote = { ...quote, quoteId: '88888888-8888-4888-8888-888888888889', orderId: secondOrderId, amount: '91.23', distanceKm: '36.492' }
 function orderFlow(mismatchedQuote = false) {
  let second = { ...order, orderId: secondOrderId, recipientName: 'Pedido B', status: 'DRAFT' }
  vi.stubGlobal('fetch', vi.fn(async (url: string, init: RequestInit) => {
   const path = String(url), method = init.method || 'GET'
   let result: unknown; let status = 200
   if (path.endsWith('/auth/login')) result = { accessToken: 'fixture', tokenType: 'Bearer', expiresAt: '2099-01-01T00:00:00.000Z', actor }
   else if (path.includes('/orders?')) result = { items: [order], page: 0, size: 20, totalItems: 1 }
   else if (path.endsWith('/freight-quote')) result = path.includes(secondOrderId) && !mismatchedQuote ? secondQuote : quote
   else if (path.endsWith('/orders') && method === 'POST') { result = second; status = 201 }
   else if (path.endsWith('/confirm')) { second = { ...second, status: 'CONFIRMED' }; result = second }
   else if (path.endsWith('/' + orderId)) result = order
   else throw new Error('Unexpected request: ' + path)
   return new Response(JSON.stringify(result), { status, headers: { 'Content-Type': 'application/json' } })
  }))
 }
 async function createAndConfirmSecond(w: ReturnType<typeof mount>) {
  await w.findAll('button').find(b => b.text().includes('Voltar aos pedidos'))!.trigger('click'); await flushPromises()
  await w.findAll('button').find(b => b.text().includes('Novo pedido'))!.trigger('click')
  const fill = async (label: string, value: string) => { await w.findAll('label').find(l => l.text() === label)!.get('input').setValue(value) }
  await fill('ID da origem', originId); await fill('Nome', 'Pedido B'); await fill('Endereço', 'Rua B'); await fill('Cidade', 'Cidade'); await fill('UF', 'SC')
  await fill('Descrição do item 1', 'Caixa B'); await fill('Quantidade', '1'); await fill('Peso unitário (kg)', '2.000')
  await w.get('form').trigger('submit'); await flushPromises()
  await w.findAll('button').find(b => b.text() === 'Confirmar pedido')!.trigger('click'); await flushPromises()
 }
 it('limpa a cotação de A ao criar e confirmar B e permite consultar a cotação própria de B', async () => {
  orderFlow(); const w = mount(App)
  try {
   await flushPromises(); await login(w); await w.get('tbody button').trigger('click'); await flushPromises()
   await w.findAll('button').find(b => b.text() === 'Consultar cotação de frete')!.trigger('click'); await flushPromises()
   expect(w.text()).toContain(quote.quoteId); expect(w.text()).toContain('30.00')
   await createAndConfirmSecond(w)
   expect(w.text()).toContain('Pedido B'); expect(w.text()).toContain('Não cotado')
   expect(w.text()).not.toContain(quote.quoteId); expect(w.text()).not.toContain('Snapshot da cotação'); expect(w.text()).not.toContain('30.00')
   await w.findAll('button').find(b => b.text() === 'Consultar cotação de frete')!.trigger('click'); await flushPromises()
   expect(w.text()).toContain(secondQuote.quoteId); expect(w.text()).toContain('91.23'); expect(w.text()).not.toContain(quote.quoteId)
  } finally { w.unmount() }
 })
 it('não exibe nem aplica uma resposta de cotação pertencente a outro pedido', async () => {
  orderFlow(true); const w = mount(App)
  try {
   await flushPromises(); await login(w); await w.get('tbody button').trigger('click'); await flushPromises()
   await createAndConfirmSecond(w)
   await w.findAll('button').find(b => b.text() === 'Consultar cotação de frete')!.trigger('click'); await flushPromises()
   expect(w.text()).toContain('Não cotado'); expect(w.text()).not.toContain(quote.quoteId); expect(w.text()).not.toContain('Snapshot da cotação')
   expect(w.get('[role=alert]').text()).toContain('não corresponde ao pedido selecionado')
  } finally { w.unmount() }
 })
})
describe('expiração da sessão', () => {
 it('encerra sessão e remove armazenamento no instante da expiração', async () => {
  vi.useFakeTimers(); vi.setSystemTime(new Date('2026-11-16T12:00:00.000Z')); fake('2026-11-16T12:00:10.000Z')
  const session=createSession(); await session.login('store@example.invalid','fixture'); expect(session.state.actor).toEqual(actor); expect(sessionStorage.length).toBe(1)
  vi.advanceTimersByTime(9999); expect(session.state.token).toBe('fixture')
  vi.advanceTimersByTime(1); expect(session.state.token).toBeNull(); expect(session.state.actor).toBeNull(); expect(session.state.expiresAt).toBeNull(); expect(sessionStorage.length).toBe(0)
 })
})
