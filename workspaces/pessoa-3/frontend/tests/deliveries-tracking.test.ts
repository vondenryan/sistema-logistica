import { describe, expect, it, vi, beforeEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import TrackingView from '../src/TrackingView.vue'
import DriverPanelView from '../src/DriverPanelView.vue'

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
})
