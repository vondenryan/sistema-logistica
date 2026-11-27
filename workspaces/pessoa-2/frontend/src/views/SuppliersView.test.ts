import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { devActor } from '../devActor'
import SuppliersView from './SuppliersView.vue'

const apiMocks = vi.hoisted(() => ({
  listSuppliers: vi.fn(),
  getSupplier: vi.fn(),
  createSupplier: vi.fn(),
  patchSupplier: vi.fn(),
  listAllStores: vi.fn(),
}))

vi.mock('../api', () => ({
  api: apiMocks,
  formatApiError: (error: unknown) =>
    error instanceof Error ? error.message : 'Ocorreu um erro inesperado.',
  listAllStores: apiMocks.listAllStores,
}))

const storeId = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'
const supplier = {
  id: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
  name: 'Fornecedor Central',
  contactEmail: 'contato@example.com',
  storeId,
}

describe('autorização da tela de fornecedores', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    devActor.role = 'ADMIN'
    devActor.storeId = ''
    apiMocks.listSuppliers.mockResolvedValue({
      items: [supplier],
      page: 0,
      size: 20,
      totalItems: 1,
    })
    apiMocks.listAllStores.mockResolvedValue([{ id: storeId, name: 'Loja Central', active: true }])
    apiMocks.getSupplier.mockResolvedValue(supplier)
  })

  it('permite criar e editar para ADMIN sem exibir inativação', async () => {
    const wrapper = mount(SuppliersView)
    await flushPromises()

    expect(wrapper.text()).toContain('Cadastrar fornecedor')
    expect(wrapper.text()).toContain('Editar')
    expect(wrapper.text()).not.toContain('Inativar')
    expect(apiMocks.listSuppliers).toHaveBeenCalledWith(0, 20)
  })

  it('para STORE oculta listagem e ações e consulta somente por ID', async () => {
    devActor.role = 'STORE'
    devActor.storeId = storeId
    const wrapper = mount(SuppliersView)
    await flushPromises()

    expect(wrapper.text()).toContain('Consultar fornecedor da minha loja')
    expect(wrapper.text()).not.toContain('Fornecedores cadastrados')
    expect(wrapper.text()).not.toContain('Cadastrar fornecedor')
    expect(wrapper.text()).not.toContain('Editar')
    expect(wrapper.text()).not.toContain('Inativar')
    expect(apiMocks.listSuppliers).not.toHaveBeenCalled()
    expect(apiMocks.listAllStores).not.toHaveBeenCalled()

    await wrapper.get('#supplier-lookup-id').setValue(supplier.id)
    await wrapper.get('.lookup-form').trigger('submit')
    await flushPromises()

    expect(apiMocks.getSupplier).toHaveBeenCalledWith(supplier.id)
    expect(wrapper.text()).toContain('Fornecedor Central')
  })

  it('omite email em branco no PATCH para preservar o valor atual', async () => {
    apiMocks.patchSupplier.mockResolvedValue(supplier)
    const wrapper = mount(SuppliersView)
    await flushPromises()
    await wrapper.get('button.text-button').trigger('click')
    await wrapper.get('#supplier-email').setValue('')
    await wrapper.get('.store-form-panel form').trigger('submit')
    await flushPromises()

    expect(apiMocks.patchSupplier).toHaveBeenCalledWith(supplier.id, {
      name: supplier.name,
      storeId,
    })
  })
})
