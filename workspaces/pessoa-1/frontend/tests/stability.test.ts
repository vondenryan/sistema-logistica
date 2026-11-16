import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import App from '../src/App.vue'
import RoutesView from '../src/RoutesView.vue'
import { Api } from '../src/api'
import { createSession } from '../src/session'

const actor = { actorId:'a', role:'OPERATOR', storeId:null, driverId:null }
const order = { orderId:'one', storeId:'store', originId:'origin', status:'CONFIRMED', recipientName:'Private A', totalWeightKg:'1.000', destination:{line1:'A',city:'C',state:'SC'}, items:[], freightAmount:'99.00' }
const json = (data: unknown, status = 200) => new Response(JSON.stringify(data), {status, headers:{'Content-Type':'application/json'}})
function deferred() { let resolve!: (response: Response) => void; const promise = new Promise<Response>(r => { resolve = r }); return {promise, resolve} }
beforeEach(() => { sessionStorage.clear() })
afterEach(() => { vi.unstubAllGlobals() })
describe('respostas atrasadas e estado de rota', () => {
 it('não restaura ator após logout durante consulta da sessão', async () => {
  const pending=deferred(); sessionStorage.setItem('logistica.session',JSON.stringify({token:'a',expiresAt:'2099-01-01T00:00:00Z'})); vi.stubGlobal('fetch',vi.fn(() => pending.promise))
  const s=createSession(); const restoring=s.restore(); s.logout(); pending.resolve(json(actor)); await restoring
  expect(s.state.actor).toBeNull(); expect(s.state.token).toBeNull()
 })
 it('não exibe pedido atrasado da sessão anterior após novo login', async () => {
  const pending=deferred(); let lists=0
  vi.stubGlobal('fetch',vi.fn((url: string) => { const p=String(url); if(p.endsWith('/auth/login')) return Promise.resolve(json({accessToken:'token'+lists,expiresAt:'2099-01-01T00:00:00Z',actor})); if(p.includes('/orders?')) { lists++; return Promise.resolve(json({items:lists===1?[order]:[],totalItems:lists===1?1:0})) } if(p.endsWith('/one')) return pending.promise; throw Error(p) }))
  const w=mount(App); await flushPromises(); await w.get('input[type=email]').setValue('a@example.invalid'); await w.get('input[type=password]').setValue('x'); await w.get('form').trigger('submit'); await flushPromises(); await w.get('tbody button').trigger('click'); await w.findAll('button').find(b=>b.text()==='Sair')!.trigger('click');
  pending.resolve(json(order)); await flushPromises(); await w.get('input[type=password]').setValue('x'); await w.get('form').trigger('submit'); await flushPromises(); expect(w.text()).not.toContain('Private A'); expect(w.text()).not.toContain('99.00'); w.unmount()
 })
 it('remove distância sugerida quando a seleção muda', async () => {
  vi.stubGlobal('fetch',vi.fn((url: string) => Promise.resolve(json(String(url).endsWith('/suggest')?{originId:'origin',orderedOrderIds:['one'],estimatedDistanceKm:'25.000'}:{items:[order],totalItems:1}))))
  const w=mount(RoutesView,{props:{api:new Api(()=>'x'),role:'OPERATOR'}}); await flushPromises(); await w.get('input[type=checkbox]').setValue(true); await w.findAll('button').find(b=>b.text()==='Sugerir ordem')!.trigger('click'); await flushPromises(); expect(w.text()).toContain('25.000'); await w.get('input[type=checkbox]').setValue(false); expect(w.text()).not.toContain('25.000'); w.unmount()
 })
})

describe('validação de rotas e erros HTTP', () => {
 it('rejeita mais de dez pedidos e pedidos de outra origem', async () => {
  const orders = Array.from({length:12}, (_, i) => ({...order, orderId:String(i), recipientName:'Cliente '+i, originId:i===11?'outra':'origin'}))
  vi.stubGlobal('fetch',vi.fn(() => Promise.resolve(json({items:orders,totalItems:12}))))
  const w=mount(RoutesView,{props:{api:new Api(()=>'x'),role:'OPERATOR'}}); await flushPromises()
  const boxes=w.findAll('input[type=checkbox]'); await boxes[0].setValue(true); await boxes[11].setValue(true); expect(w.get('[role=alert]').text()).toContain('mesma origem e loja'); expect((boxes[11].element as HTMLInputElement).checked).toBe(false)
  for (let i=1;i<10;i++) await boxes[i].setValue(true)
  await boxes[10].setValue(true); expect(w.get('[role=alert]').text()).toContain('máximo 10'); expect((boxes[10].element as HTMLInputElement).checked).toBe(false); expect(w.text()).toContain('(10/10)'); w.unmount()
 })
 it('limpa rota anterior quando a nova consulta falha com 404', async () => {
  vi.stubGlobal('fetch',vi.fn((url:string) => Promise.resolve(String(url).endsWith('/old') ? json({routeId:'old',storeId:'store',originId:'origin',status:'PLANNED',orderIds:['one']}) : json({status:404,detail:'Rota não encontrada',title:'Não encontrada',details:[]},404))))
  const w=mount(RoutesView,{props:{api:new Api(()=>'x'),role:'DRIVER'}}); await w.get('input').setValue('old'); await w.get('form').trigger('submit'); await flushPromises(); expect(w.text()).toContain('Rota old'); await w.get('input').setValue('missing'); await w.get('form').trigger('submit'); await flushPromises(); expect(w.get('[role=alert]').text()).toContain('Rota não encontrada'); expect(w.text()).not.toContain('Rota old'); w.unmount()
 })
 it.each([403,409,422,503])('apresenta detalhe do erro %i', async status => {
  vi.stubGlobal('fetch',vi.fn(() => Promise.resolve(json({status,detail:`Falha ${status}`,title:'Erro',details:[]},status))))
  const w=mount(RoutesView,{props:{api:new Api(()=>'x'),role:'DRIVER'}}); await w.get('input').setValue('id'); await w.get('form').trigger('submit'); await flushPromises(); expect(w.get('[role=alert]').text()).toContain(`Falha ${status}`); w.unmount()
 })
 it('apresenta falha de rede e emite expiração em 401', async () => {
  const fetch=vi.fn().mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce(json({status:401,detail:'Sessão expirada',title:'Erro',details:[]},401)); vi.stubGlobal('fetch',fetch)
  const w=mount(RoutesView,{props:{api:new Api(()=>'x'),role:'DRIVER'}}); await w.get('input').setValue('id'); await w.get('form').trigger('submit'); await flushPromises(); expect(w.get('[role=alert]').text()).toContain('Não foi possível conectar'); await w.get('form').trigger('submit'); await flushPromises(); expect(w.emitted('expired')).toHaveLength(1); w.unmount()
 })
})

describe('operações pendentes e troca de sessão', () => {
 it('habilita login ao sair e não libera o carregamento da nova sessão quando o pedido antigo termina', async () => {
  const old=deferred(), next=deferred(); let lists=0
  vi.stubGlobal('fetch',vi.fn((url:string) => { const path=String(url); if(path.endsWith('/auth/login')) return Promise.resolve(json({accessToken:'token',expiresAt:'2099-01-01T00:00:00Z',actor})); if(path.includes('/orders?')) return ++lists===1 ? Promise.resolve(json({items:[order],totalItems:1})) : next.promise; if(path.endsWith('/one')) return old.promise; throw Error(path) }))
  const w=mount(App,{attachTo:document.body}); await flushPromises(); await w.get('input[type=email]').setValue('a@example.invalid'); await w.get('input[type=password]').setValue('x'); await w.get('form button').trigger('click'); await flushPromises(); await w.get('tbody button').trigger('click'); await w.findAll('button').find(b=>b.text()==='Sair')!.trigger('click')
  expect((w.get('form button').element as HTMLButtonElement).disabled).toBe(false)
  await w.get('input[type=password]').setValue('x'); await w.get('form button').trigger('click'); await flushPromises(); expect(w.text()).toContain('Carregando')
  old.resolve(json(order)); await flushPromises(); expect(w.text()).toContain('Carregando'); expect((w.findAll('button').find(b=>b.text().includes('Novo pedido'))!.element as HTMLButtonElement).disabled).toBe(true)
  next.resolve(json({items:[],totalItems:0})); await flushPromises(); expect(w.text()).not.toContain('Carregando'); w.unmount()
 })
 it('mostra erro ao carregar pedidos depois do login', async () => {
  vi.stubGlobal('fetch',vi.fn((url:string) => Promise.resolve(String(url).endsWith('/auth/login') ? json({accessToken:'token',expiresAt:'2099-01-01T00:00:00Z',actor}) : json({status:503,title:'Indisponível',detail:'Pedidos indisponíveis',details:[]},503))))
  const w=mount(App,{attachTo:document.body}); await flushPromises(); await w.get('input[type=email]').setValue('a@example.invalid'); await w.get('input[type=password]').setValue('x'); await w.get('form button').trigger('click'); await flushPromises(); expect(w.get('[role=alert]').text()).toContain('Pedidos indisponíveis'); w.unmount()
 })
 it('não emite expiração para 401 recebido após desmontar rotas', async () => {
  const pending=deferred(); vi.stubGlobal('fetch',vi.fn(() => pending.promise)); const expired=vi.fn()
  const w=mount(RoutesView,{props:{api:new Api(()=>'x'),role:'DRIVER',onExpired:expired}}); await w.get('input').setValue('id'); await w.get('form').trigger('submit'); w.unmount(); pending.resolve(json({status:401,title:'Erro',detail:'Sessão expirada',details:[]},401)); await flushPromises(); expect(expired).not.toHaveBeenCalled()
 })
})
