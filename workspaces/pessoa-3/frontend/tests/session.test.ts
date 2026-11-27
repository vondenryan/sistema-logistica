import { flushPromises, mount } from '@vue/test-utils'
import App from '../src/App.vue'
import { afterEach, expect, it, vi } from 'vitest'
import { clearDeliverySession, deliveriesApi, deliveryAuthenticated, setDeliverySession } from '../src/api'

afterEach(() => {
  clearDeliverySession()
  vi.useRealTimers()
  vi.unstubAllGlobals()
  vi.unstubAllEnvs()
})

it('expires the session at the server timestamp and keeps tracking public', async () => {
  vi.useFakeTimers()
  setDeliverySession({ accessToken: 'test-token', expiresAt: new Date(Date.now() + 1000).toISOString() })
  expect(deliveryAuthenticated.value).toBe(true)
  vi.advanceTimersByTime(1000)
  expect(deliveryAuthenticated.value).toBe(false)

  const fetch = vi.fn().mockResolvedValue(new Response('{"status":"PENDING","milestones":[]}'))
  vi.stubGlobal('fetch', fetch)
  await expect(deliveriesApi.trackByCode('TRK-TEST')).resolves.toMatchObject({ status: 'PENDING' })
  expect(fetch).toHaveBeenCalledWith('/api/v1/tracking/TRK-TEST')
})

it('keeps a new session when a request from the previous session returns 401', async () => {
  let finish!: (response: Response) => void
  vi.stubGlobal('fetch', vi.fn().mockImplementation(() => new Promise<Response>(resolve => {
    finish = resolve
  })))
  const expiresAt = new Date(Date.now() + 60_000).toISOString()
  setDeliverySession({ accessToken: 'old-token', expiresAt })
  const pending = deliveriesApi.listMyDeliveries()
  setDeliverySession({ accessToken: 'new-token', expiresAt })
  finish(new Response('{"code":"UNAUTHENTICATED"}', { status: 401 }))
  await expect(pending).rejects.toMatchObject({ status: 401 })
  expect(deliveryAuthenticated.value).toBe(true)
})


it('loads the driver panel in an autonomous production build', async () => {
  vi.stubEnv('MODE', 'production')
  vi.stubEnv('PROD', true)
  const fetch = vi.fn().mockResolvedValue(new Response('{"items":[],"page":0,"size":20,"totalItems":0}'))
  vi.stubGlobal('fetch', fetch)
  const view = mount(App)
  await flushPromises()
  expect(view.find('#email').exists()).toBe(false)
  expect(fetch).toHaveBeenCalledWith('/api/v1/deliveries/my?page=0&size=20')
  view.unmount()
})

it('requires login for protected tabs and keeps public tracking available in integrated mode', async () => {
  vi.stubEnv('MODE', 'integration')
  const fetch = vi.fn()
  vi.stubGlobal('fetch', fetch)
  const view = mount(App)
  expect(view.find('#email').exists()).toBe(true)
  expect(fetch).not.toHaveBeenCalled()
  const tracking = view.findAll('nav button').find(button => button.text() === 'Rastreamento Público')
  await tracking?.trigger('click')
  expect(view.find('#email').exists()).toBe(false)
  expect(view.get('main').text()).toContain('Rastreamento')
  view.unmount()
})
