import { describe, expect, it, vi, beforeEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import TrackingView from '../src/TrackingView.vue'
import DriverPanelView from '../src/DriverPanelView.vue'
import DocumentsView from '../src/DocumentsView.vue'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' }
  })
}

describe('TrackingView', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('displays public status and milestones without exposing internal fields', async () => {
    const fetchMock = vi.fn().mockResolvedValueOnce(
      jsonResponse({
        status: 'IN_TRANSIT',
        milestones: [
          { status: 'PENDING', at: '2026-11-14T09:00:00.000Z' },
          { status: 'ASSIGNED', at: '2026-11-14T10:00:00.000Z' },
          { status: 'IN_TRANSIT', at: '2026-11-14T12:00:00.000Z' }
        ]
      })
    )
    vi.stubGlobal('fetch', fetchMock)

    const wrapper = mount(TrackingView)
    await wrapper.find('input').setValue('TRK-7M4Q9X2K8P')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/tracking/TRK-7M4Q9X2K8P')
    expect(wrapper.text()).toContain('Em trânsito')
    expect(wrapper.findAll('.milestone-item')).toHaveLength(3)
  })

  it('shows generic not found message on 404', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValueOnce(
        jsonResponse({ status: 404, code: 'NOT_FOUND', detail: 'Resource not found' }, 404)
      )
    )

    const wrapper = mount(TrackingView)
    await wrapper.find('input').setValue('TRK-INVALIDO')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.find('[role="alert"]').text()).toContain('não encontrado')
  })

  it('shows rate limit notice on 429', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValueOnce(
        jsonResponse({ status: 429, code: 'RATE_LIMITED', detail: 'Too many requests' }, 429)
      )
    )

    const wrapper = mount(TrackingView)
    await wrapper.find('input').setValue('TRK-7M4Q9X2K8P')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.find('[role="alert"]').text()).toContain('Muitas consultas')
  })
})

describe('DriverPanelView', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('loads driver deliveries and transitions ASSIGNED -> IN_TRANSIT -> DELIVERED', async () => {
    const deliveryId = '77777777-7777-4777-8777-777777777777'
    const orderId = '44444444-4444-4444-8444-444444444444'
    const routeId = '33333333-3333-4333-8333-333333333333'

    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        jsonResponse({
          items: [
            {
              deliveryId,
              orderId,
              routeId,
              status: 'ASSIGNED',
              updatedAt: '2026-11-14T12:00:00.000Z',
              receiverName: null
            }
          ],
          page: 0,
          size: 20,
          totalItems: 1
        })
      )
      .mockResolvedValueOnce(
        jsonResponse({
          deliveryId,
          orderId,
          routeId,
          status: 'IN_TRANSIT',
          updatedAt: '2026-11-14T12:05:00.000Z',
          receiverName: null
        })
      )
      .mockResolvedValueOnce(
        jsonResponse({
          deliveryId,
          orderId,
          routeId,
          status: 'DELIVERED',
          updatedAt: '2026-11-14T12:30:00.000Z',
          receiverName: 'Maria Silva'
        })
      )

    vi.stubGlobal('fetch', fetchMock)

    const wrapper = mount(DriverPanelView)
    await flushPromises()

    expect(wrapper.text()).toContain('Minhas Entregas (1)')
    expect(wrapper.text()).toContain('ASSIGNED')

    await wrapper.find('button.btn-primary').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('IN_TRANSIT')

    // Trying to complete without receiverName triggers local validation
    await wrapper.find('button.btn-success').trigger('click')
    expect(wrapper.find('[role="alert"]').text()).toContain('nome do recebedor')

    await wrapper.find('input[aria-label="Nome do recebedor"]').setValue('Maria Silva')
    await wrapper.find('button.btn-success').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('DELIVERED')
    expect(wrapper.text()).toContain('Maria Silva')
  })
  it('navigates to the 21st delivery, updates it, refreshes the current page and returns', async () => {
    const firstItems = Array.from({ length: 20 }, (_, i) => ({
      deliveryId: `delivery-${i}`, orderId: `order-${i}`, routeId: 'route',
      status: 'ASSIGNED', updatedAt: '2026-11-14T12:00:00Z', receiverName: null
    }))
    const last = { ...firstItems[0], deliveryId: 'delivery-21', orderId: 'order-21' }
    const moved = { ...last, status: 'IN_TRANSIT' }
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({ items: firstItems, page: 0, size: 20, totalItems: 21 }))
      .mockResolvedValueOnce(jsonResponse({ items: [last], page: 1, size: 20, totalItems: 21 }))
      .mockResolvedValueOnce(jsonResponse(moved))
      .mockResolvedValueOnce(jsonResponse({ items: [moved], page: 1, size: 20, totalItems: 21 }))
      .mockResolvedValueOnce(jsonResponse({ items: firstItems, page: 0, size: 20, totalItems: 21 }))
    vi.stubGlobal('fetch', fetchMock)
    const wrapper = mount(DriverPanelView)
    await flushPromises()
    const nav = () => wrapper.get('nav[aria-label="Paginação das entregas"]')
    const previous = () => nav().findAll('button')[0]
    const next = () => nav().findAll('button')[1]
    expect(previous().attributes('disabled')).toBeDefined()
    expect(wrapper.findAll('article')).toHaveLength(20)
    await next().trigger('click')
    await flushPromises()
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/v1/deliveries/my?page=1&size=20')
    expect(nav().text()).toContain('Página 2 de 2')
    expect(next().attributes('disabled')).toBeDefined()
    expect(wrapper.findAll('article')).toHaveLength(1)
    expect(wrapper.get('article').attributes('data-delivery-id')).toBe(last.deliveryId)
    await wrapper.get('article button.btn-primary').trigger('click')
    await flushPromises()
    expect(fetchMock).toHaveBeenNthCalledWith(3, '/api/v1/deliveries/delivery-21/status', expect.objectContaining({ method: 'PATCH', body: JSON.stringify({ status: 'IN_TRANSIT' }) }))
    expect(wrapper.get('article').text()).toContain('IN_TRANSIT')
    await wrapper.get('.card button').trigger('click')
    await flushPromises()
    expect(fetchMock).toHaveBeenNthCalledWith(4, '/api/v1/deliveries/my?page=1&size=20')
    await previous().trigger('click')
    await flushPromises()
    expect(fetchMock).toHaveBeenNthCalledWith(5, '/api/v1/deliveries/my?page=0&size=20')
    expect(wrapper.findAll('article')).toHaveLength(20)
    expect(nav().text()).toContain('Página 1 de 2')
  })

  it('preserves the current page after navigation fails and allows retry', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({ items: [], page: 0, size: 20, totalItems: 21 }))
      .mockResolvedValueOnce(jsonResponse({ code: 'DEPENDENCY_UNAVAILABLE' }, 503))
      .mockResolvedValueOnce(jsonResponse({ items: [], page: 1, size: 20, totalItems: 21 }))
    vi.stubGlobal('fetch', fetchMock)
    const wrapper = mount(DriverPanelView)
    await flushPromises()
    const next = () => wrapper.get('nav').findAll('button')[1]
    await next().trigger('click')
    await flushPromises()
    expect(wrapper.get('nav').text()).toContain('Página 1 de 2')
    expect(wrapper.find('[role="alert"]').exists()).toBe(true)
    await next().trigger('click')
    await flushPromises()
    expect(fetchMock).toHaveBeenNthCalledWith(3, '/api/v1/deliveries/my?page=1&size=20')
    expect(wrapper.get('nav').text()).toContain('Página 2 de 2')
  })

})

describe('DocumentsView', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('displays academic watermark and generates simulated document PDF link', async () => {
    const orderId = '44444444-4444-4444-8444-444444444444'
    const documentId = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'

    const fetchMock = vi.fn().mockResolvedValueOnce(
      jsonResponse(
        {
          documentId,
          orderId,
          documentType: 'CTE_SIMULATED',
          status: 'GENERATED'
        },
        201
      )
    )
    vi.stubGlobal('fetch', fetchMock)

    const wrapper = mount(DocumentsView)
    expect(wrapper.text()).toContain('SIMULAÇÃO ACADÊMICA — SEM VALIDADE FISCAL')

    await wrapper.find('input[aria-label="ID do Pedido"]').setValue(orderId)
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain(documentId)
    const link = wrapper.find('a')
    expect(link.attributes('href')).toBe(`/api/v1/documents/${documentId}/pdf`)
  })

  it('shows precondition error when freight is not quoted (422)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValueOnce(
        jsonResponse({ status: 422, code: 'PRECONDITION_FAILED', detail: 'Freight missing' }, 422)
      )
    )

    const wrapper = mount(DocumentsView)
    await wrapper
      .find('input[aria-label="ID do Pedido"]')
      .setValue('44444444-4444-4444-8444-444444444444')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.find('[role="alert"]').text()).toContain('Frete ainda não cotado')
  })
})
