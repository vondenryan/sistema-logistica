import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import App from '../src/App.vue'
import { Api, ApiError, type OrderInput } from '../src/api'
import { createSession } from '../src/session'

const storeId = '22222222-2222-4222-8222-222222222222', originId = '11111111-1111-4111-8111-111111111111'
const actor = { actorId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', role: 'STORE', storeId, driverId: null }
const input: OrderInput = { storeId, originId, destination: { line1:'Rua A', line2:null, city:'Cidade', state:'SC', country:'BR', latitude:-26, longitude:-49 }, recipientName:'Cliente', items:[{description:'Caixa',quantity:2,unitWeightKg:'20.000'}] }
const problem = { type:'https://example.invalid/problems/conflict', title:'Conflict', status:409, detail:'Estado inválido', code:'CONFLICT', details:[] }
function fixture() {
 let order = { ...input, orderId:'44444444-4444-4444-8444-444444444444', status:'DRAFT', totalWeightKg:'40.000', freightAmount:null, currency:'BRL', trackingCode:'TRK-EXEMPLO', createdAt:'2026-11-14T12:00:00.000Z' }
 const calls: {url:string; method:string; body: any; authorization: string | null}[] = []
 const fake = vi.fn(async (url: string | URL | Request, init?: RequestInit) => {
  const path = String(url), method = init?.method || 'GET', body = init?.body ? JSON.parse(String(init.body)) : undefined
  calls.push({url:path,method,body,authorization:new Headers(init?.headers).get('Authorization')})
  let result: unknown = order, status = 200
  if (path.endsWith('/auth/login')) { if (body.password === 'wrong') { result = {...problem,status:401,code:'UNAUTHENTICATED',detail:'Credenciais inválidas'}; status=401 } else result = {accessToken:'fixture-token',tokenType:'Bearer',expiresAt:'2099-01-01T00:00:00.000Z',actor} }
  else if (path.endsWith('/auth/me')) result = actor
  else if (path.includes('/orders?')) result = {items:[order],page:0,size:20,totalItems:1}
  else if (path.endsWith('/orders') && method === 'POST') { order = {...order,...body,status:'DRAFT'}; result=order; status=201 }
  else if (method === 'PATCH') { order = {...order,...body}; result=order }
  else if (path.endsWith('/confirm')) { order = {...order,status:'CONFIRMED'}; result=order }
  else if (path.endsWith('/cancel')) { if (order.status !== 'DRAFT') {result=problem;status=409} else {order={...order,status:'CANCELLED'};result=order} }
  return new Response(JSON.stringify(result), {status,headers:{'Content-Type': status >= 400 ? 'application/problem+json':'application/json'}})
 })
 vi.stubGlobal('fetch', fake); return { calls }
}
beforeEach(() => { sessionStorage.clear(); vi.unstubAllGlobals() })
describe('contrato HTTP e sessão', () => {
 it('autentica, restaura o ator pelo servidor e limpa a sessão', async () => { const {calls}=fixture(); const s=createSession(); await s.login('store@example.invalid','local'); expect(s.state.actor?.storeId).toBe(storeId); const restored=createSession(); await restored.restore(); expect(restored.state.actor?.role).toBe('STORE'); expect(calls.at(-1)?.authorization).toBe('Bearer fixture-token'); restored.logout(); expect(sessionStorage.length).toBe(0) })
 it('recusa sessão expirada sem consultar o servidor e apresenta ProblemDetail', async () => { fixture(); sessionStorage.setItem('logistica.session',JSON.stringify({token:'old',expiresAt:'2000-01-01T00:00:00Z'})); const s=createSession(); await s.restore(); expect(s.state.token).toBeNull(); await expect(s.login('x@example.invalid','wrong')).rejects.toMatchObject({problem:{code:'UNAUTHENTICATED',detail:'Credenciais inválidas'}}) })
 it('cria, edita, confirma e cancela com corpos v1 e pesos em string', async () => { const {calls}=fixture(); const api=new Api(()=>'fixture-token'); const created=await api.create(input); await api.update(created.orderId,{...input,recipientName:'Outro'}); expect((await api.confirm(created.orderId)).status).toBe('CONFIRMED'); await expect(api.cancel(created.orderId,'Solicitação')).rejects.toBeInstanceOf(ApiError); const draft = await api.create(input); expect(draft.status).toBe('DRAFT'); expect((await api.cancel(draft.orderId,'Solicitação')).status).toBe('CANCELLED'); expect(calls[0].body.items[0].unitWeightKg).toBe('20.000'); expect(calls[1].method).toBe('PATCH'); expect(calls[2].body).toEqual({}); expect(calls[3].body).toEqual({reason:'Solicitação'}); expect(calls[5].body).toEqual({reason:'Solicitação'}) })
})
describe('interface', () => {
 it('usa loja do ator na criação e permite editar o rascunho', async () => { const {calls}=fixture(); const wrapper=mount(App); await flushPromises(); await wrapper.get('input[type=email]').setValue('store@example.invalid'); await wrapper.get('input[type=password]').setValue('local'); await wrapper.get('form').trigger('submit'); await flushPromises(); expect(wrapper.text()).toContain('Cliente'); await wrapper.get('tbody button').trigger('click'); await flushPromises(); await wrapper.findAll('button').find(b=>b.text().includes('Editar rascunho'))!.trigger('click'); expect((wrapper.findAll('input')[0].element as HTMLInputElement).readOnly).toBe(true); await wrapper.get('form').trigger('submit'); await flushPromises(); expect(calls.find(c=>c.method==='PATCH')?.body.storeId).toBe(storeId); expect(wrapper.text()).toContain('Rascunho atualizado') })
 it('oculta cancelamento após confirmação e limpa e-mail ao criar outro pedido', async () => { const {calls}=fixture(); const wrapper=mount(App); await flushPromises(); await wrapper.get('input[type=email]').setValue('store@example.invalid'); await wrapper.get('input[type=password]').setValue('local'); await wrapper.get('form').trigger('submit'); await flushPromises(); await wrapper.get('tbody button').trigger('click'); await flushPromises(); await wrapper.findAll('button').find(b=>b.text()==='Confirmar pedido')!.trigger('click'); await flushPromises(); expect(wrapper.text()).toContain('Os dados confirmados são imutáveis'); expect(wrapper.text()).not.toContain('Motivo do cancelamento'); await wrapper.findAll('button').find(b=>b.text().includes('Voltar aos pedidos'))!.trigger('click'); await flushPromises(); await wrapper.findAll('button').find(b=>b.text().includes('Novo pedido'))!.trigger('click'); expect((wrapper.find('input[type=email]').element as HTMLInputElement).value).toBe(''); expect(calls.some(c=>c.url.endsWith('/confirm'))).toBe(true) })
 it('mostra erro de login sem persistir token', async () => { fixture(); const wrapper=mount(App); await flushPromises(); await wrapper.get('input[type=email]').setValue('store@example.invalid'); await wrapper.get('input[type=password]').setValue('wrong'); await wrapper.get('form').trigger('submit'); await flushPromises(); expect(wrapper.get('[role=alert]').text()).toContain('Credenciais inválidas'); expect(sessionStorage.length).toBe(0) })
})
