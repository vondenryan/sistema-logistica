<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api, formatApiError, listAllStores, type Store, type Supplier } from '../api'
import { currentRole, devActor, isDevMode, type DevRole } from '../devActor'
import {
  canListPartners,
  canManagePartners,
  canReadPartnerById,
  isValidPartnerId,
} from '../partners/partnerPolicy'
import { validateSupplier } from '../partners/supplierValidation'

const roles: { value: DevRole; label: string }[] = [
  { value: 'ADMIN', label: 'Administrador' },
  { value: 'OPERATOR', label: 'Operador' },
  { value: 'STORE', label: 'Loja' },
]

const selectedRole = ref<DevRole>(devActor.role ?? 'ADMIN')
const selectedStoreId = ref(devActor.storeId)
const page = ref(0)
const pageSize = ref(20)
const totalItems = ref(0)
const suppliers = ref<Supplier[]>([])
const stores = ref<Store[]>([])
const loading = ref(false)
const loadingStores = ref(false)
const saving = ref(false)
const errorMessage = ref('')
const storeError = ref('')
const statusMessage = ref('')
const formOpen = ref(false)
const formError = ref('')
const editingSupplier = ref<Supplier | null>(null)
const formName = ref('')
const formEmail = ref('')
const formStoreId = ref('')
const lookupId = ref('')
const lookupLoading = ref(false)
const lookupError = ref('')
const lookupSupplier = ref<Supplier | null>(null)

const role = computed(() => (currentRole.value))
const canManage = computed(() => canManagePartners(role.value))
const canList = computed(() => canListPartners(role.value))
const isStoreProfile = computed(() => currentRole.value === 'STORE')
const canReadById = computed(() => canReadPartnerById(role.value))
const pageCount = computed(() => Math.max(1, Math.ceil(totalItems.value / pageSize.value)))
const canApplyProfile = computed(
  () => selectedRole.value !== 'STORE' || isValidPartnerId(selectedStoreId.value),
)

onMounted(() => {
  if (canList.value) {
    void loadSuppliers()
    void loadStores()
  }
})

function applyDevProfile() {
  devActor.role = selectedRole.value
  devActor.storeId = selectedRole.value === 'STORE' ? selectedStoreId.value.trim() : ''
  page.value = 0
  pageSize.value = 20
  statusMessage.value = ''
  lookupError.value = ''
  lookupSupplier.value = null
  closeForm()
  if (canList.value) {
    void loadSuppliers()
    void loadStores()
  } else {
    suppliers.value = []
    totalItems.value = 0
    stores.value = []
  }
}

async function loadSuppliers() {
  if (!canList.value) {
    return
  }
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await api.listSuppliers(page.value, pageSize.value)
    suppliers.value = result.items
    totalItems.value = result.totalItems
  } catch (error) {
    suppliers.value = []
    totalItems.value = 0
    errorMessage.value = formatApiError(error)
  } finally {
    loading.value = false
  }
}

async function loadStores() {
  loadingStores.value = true
  storeError.value = ''
  try {
    stores.value = await listAllStores()
  } catch (error) {
    stores.value = []
    storeError.value = formatApiError(error)
  } finally {
    loadingStores.value = false
  }
}

function changePage(nextPage: number) {
  if (nextPage < 0 || nextPage >= pageCount.value) {
    return
  }
  page.value = nextPage
  void loadSuppliers()
}

function changePageSize(event: Event) {
  const target = event.target
  if (!(target instanceof HTMLSelectElement)) {
    return
  }
  pageSize.value = Number(target.value)
  page.value = 0
  void loadSuppliers()
}

function openCreateForm() {
  editingSupplier.value = null
  formName.value = ''
  formEmail.value = ''
  formStoreId.value = ''
  formError.value = ''
  formOpen.value = true
}

function openEditForm(supplier: Supplier) {
  editingSupplier.value = supplier
  formName.value = supplier.name
  formEmail.value = supplier.contactEmail ?? ''
  formStoreId.value = supplier.storeId
  formError.value = ''
  formOpen.value = true
}

function closeForm() {
  formOpen.value = false
  formError.value = ''
}

async function saveSupplier() {
  if (!canManage.value) {
    return
  }
  const validationMessage = validateSupplier(formName.value, formEmail.value, formStoreId.value)
  if (validationMessage !== null) {
    formError.value = validationMessage
    return
  }

  saving.value = true
  formError.value = ''
  statusMessage.value = ''
  try {
    const payload = {
      name: formName.value,
      ...(formEmail.value.trim() === '' ? {} : { contactEmail: formEmail.value }),
      storeId: formStoreId.value,
    }
    if (editingSupplier.value) {
      await api.patchSupplier(editingSupplier.value.id, payload)
      statusMessage.value = 'Fornecedor atualizado.'
    } else {
      await api.createSupplier(payload)
      statusMessage.value = 'Fornecedor cadastrado.'
    }
    closeForm()
    await loadSuppliers()
  } catch (error) {
    formError.value = formatApiError(error)
  } finally {
    saving.value = false
  }
}

async function lookupSupplierById() {
  if (!canReadById.value) {
    return
  }
  if (!isValidPartnerId(lookupId.value)) {
    lookupSupplier.value = null
    lookupError.value = 'Informe um UUID válido para consultar o fornecedor.'
    return
  }

  lookupLoading.value = true
  lookupError.value = ''
  lookupSupplier.value = null
  try {
    lookupSupplier.value = await api.getSupplier(lookupId.value.trim())
  } catch (error) {
    lookupError.value = formatApiError(error)
  } finally {
    lookupLoading.value = false
  }
}
</script>

<template>
  <section class="stores-page" aria-labelledby="suppliers-title">
    <div class="page-heading">
      <div>
        <p class="eyebrow">Cadastros</p>
        <h1 id="suppliers-title">Fornecedores</h1>
      </div>
      <button v-if="canManage" type="button" class="primary-button" @click="openCreateForm">
        Cadastrar fornecedor
      </button>
    </div>

    <section v-if="isDevMode" class="dev-profile" aria-labelledby="dev-profile-title">
      <div>
        <h2 id="dev-profile-title">Perfil de desenvolvimento</h2>
        <p>Este seletor e os headers de ator funcionam somente no modo dev local.</p>
      </div>
      <form class="profile-form" @submit.prevent="applyDevProfile">
        <label for="dev-role">Perfil</label>
        <select id="dev-role" v-model="selectedRole">
          <option v-for="item in roles" :key="item.value" :value="item.value">{{ item.label }}</option>
        </select>
        <template v-if="selectedRole === 'STORE'">
          <label for="dev-store-id">UUID da loja</label>
          <input
            id="dev-store-id"
            v-model="selectedStoreId"
            type="text"
            autocomplete="off"
            aria-describedby="store-id-help"
            placeholder="00000000-0000-0000-0000-000000000000"
          />
          <span id="store-id-help" class="field-help">Loja autorizada para o perfil.</span>
        </template>
        <button type="submit" class="secondary-button" :disabled="!canApplyProfile">
          Aplicar perfil
        </button>
      </form>
    </section>

    <p v-if="statusMessage" class="notice success" role="status" aria-live="polite">
      {{ statusMessage }}
    </p>
    <p v-if="errorMessage" class="notice error" role="alert">{{ errorMessage }}</p>
    <p v-if="storeError && canManage" class="notice error" role="alert">
      Não foi possível carregar as lojas para seleção: {{ storeError }}
    </p>

    <section v-if="formOpen && canManage" class="store-form-panel" aria-labelledby="form-title">
      <h2 id="form-title">{{ editingSupplier ? 'Editar fornecedor' : 'Cadastrar fornecedor' }}</h2>
      <form @submit.prevent="saveSupplier">
        <label for="supplier-name">Nome do fornecedor</label>
        <input id="supplier-name" v-model="formName" maxlength="160" required />
        <span class="field-help">De 1 a 160 caracteres.</span>

        <label for="supplier-email">Email de contato (opcional)</label>
        <input id="supplier-email" v-model="formEmail" type="email" maxlength="254" />
        <span class="field-help">
          Até 254 caracteres. Ao editar, deixar vazio mantém o email atual; o contrato não permite removê-lo.
        </span>

        <label for="supplier-store">Loja vinculada</label>
        <select id="supplier-store" v-model="formStoreId" required :disabled="loadingStores">
          <option value="" disabled>Selecione uma loja</option>
          <option v-for="store in stores" :key="store.id" :value="store.id">
            {{ store.name }}{{ store.active ? '' : ' (inativa)' }}
          </option>
        </select>

        <p v-if="formError" class="notice error" role="alert">{{ formError }}</p>
        <div class="form-actions">
          <button type="submit" class="primary-button" :disabled="saving || loadingStores || stores.length === 0">
            {{ saving ? 'Salvando…' : 'Salvar' }}
          </button>
          <button type="button" class="secondary-button" :disabled="saving" @click="closeForm">
            Cancelar
          </button>
        </div>
      </form>
    </section>

    <section v-if="isStoreProfile && canReadById" class="list-panel" aria-labelledby="lookup-title">
      <h2 id="lookup-title">Consultar fornecedor da minha loja</h2>
      <form class="lookup-form" @submit.prevent="lookupSupplierById">
        <label for="supplier-lookup-id">UUID do fornecedor</label>
        <input id="supplier-lookup-id" v-model="lookupId" type="text" autocomplete="off" />
        <button type="submit" class="secondary-button" :disabled="lookupLoading">
          {{ lookupLoading ? 'Consultando…' : 'Consultar' }}
        </button>
      </form>
      <p v-if="lookupError" class="notice error" role="alert">{{ lookupError }}</p>
      <article v-if="lookupSupplier" class="lookup-result">
        <h3>{{ lookupSupplier.name }}</h3>
        <p v-if="lookupSupplier.contactEmail">{{ lookupSupplier.contactEmail }}</p>
        <p>Loja: {{ lookupSupplier.storeId }}</p>
      </article>
    </section>

    <section v-else-if="canList" class="list-panel" aria-labelledby="list-title">
      <div class="list-heading">
        <div>
          <h2 id="list-title">Fornecedores cadastrados</h2>
          <p class="field-help">{{ totalItems }} fornecedor(es)</p>
        </div>
        <div class="list-controls">
          <label for="page-size">Itens por página</label>
          <select id="page-size" :value="pageSize" :disabled="loading" @change="changePageSize">
            <option :value="10">10</option>
            <option :value="20">20</option>
            <option :value="50">50</option>
            <option :value="100">100</option>
          </select>
          <button type="button" class="secondary-button" :disabled="loading" @click="loadSuppliers">
            Atualizar
          </button>
        </div>
      </div>

      <p v-if="loading" class="empty-state" role="status">Carregando fornecedores…</p>
      <div v-else-if="suppliers.length > 0" class="table-scroll">
        <table>
          <caption class="sr-only">Fornecedores e lojas vinculadas</caption>
          <thead>
            <tr>
              <th scope="col">Nome</th>
              <th scope="col">Email de contato</th>
              <th scope="col">Loja</th>
              <th v-if="canManage" scope="col">Ações</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="supplier in suppliers" :key="supplier.id">
              <td>{{ supplier.name }}</td>
              <td>{{ supplier.contactEmail ?? 'Não informado' }}</td>
              <td>{{ stores.find((store) => store.id === supplier.storeId)?.name ?? supplier.storeId }}</td>
              <td v-if="canManage">
                <button type="button" class="text-button" @click="openEditForm(supplier)">Editar</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <p v-else-if="!errorMessage" class="empty-state">Nenhum fornecedor encontrado.</p>

      <div class="pagination" aria-label="Paginação dos fornecedores">
        <button
          type="button"
          class="secondary-button"
          :disabled="loading || page === 0 || totalItems === 0"
          @click="changePage(page - 1)"
        >
          Anterior
        </button>
        <span>Página {{ page + 1 }} de {{ pageCount }}</span>
        <button
          type="button"
          class="secondary-button"
          :disabled="loading || page + 1 >= pageCount"
          @click="changePage(page + 1)"
        >
          Próxima
        </button>
      </div>
    </section>
    <p v-else class="notice error" role="alert">Seu perfil não tem acesso ao cadastro de fornecedores.</p>
  </section>
</template>
