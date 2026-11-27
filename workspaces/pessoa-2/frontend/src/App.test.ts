import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'
import { devActor } from './devActor'
import App from './App.vue'

const RouterLinkStub = {
  props: ['to'],
  template: '<a :data-to="to"><slot /></a>',
}

describe('menu de frota', () => {
  beforeEach(() => {
    devActor.role = 'ADMIN'
  })

  it('exibe links de frota somente para ADMIN e OPERATOR', () => {
    const adminMenu = mount(App, {
      global: { stubs: { RouterLink: RouterLinkStub, RouterView: true } },
    })
    expect(adminMenu.find('[data-to="/carriers"]').exists()).toBe(true)
    expect(adminMenu.find('[data-to="/drivers"]').exists()).toBe(true)
    expect(adminMenu.find('[data-to="/vehicles"]').exists()).toBe(true)
    expect(adminMenu.find('[data-to="/fleet/assignments"]').exists()).toBe(true)

    devActor.role = 'OPERATOR'
    const operatorMenu = mount(App, {
      global: { stubs: { RouterLink: RouterLinkStub, RouterView: true } },
    })
    expect(operatorMenu.find('[data-to="/carriers"]').exists()).toBe(true)

    devActor.role = 'STORE'
    const storeMenu = mount(App, {
      global: { stubs: { RouterLink: RouterLinkStub, RouterView: true } },
    })
    expect(storeMenu.find('[data-to="/carriers"]').exists()).toBe(false)
    expect(storeMenu.find('[data-to="/drivers"]').exists()).toBe(false)
    expect(storeMenu.find('[data-to="/vehicles"]').exists()).toBe(false)
    expect(storeMenu.find('[data-to="/fleet/assignments"]').exists()).toBe(false)
  })
})
