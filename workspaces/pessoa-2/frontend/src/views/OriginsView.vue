<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { api, formatApiError, listAllStores, type Origin, type Store } from '../api'
import { devActor, isDevMode, type DevRole } from '../devActor'
import {
  canListPartners,
  canManagePartners,
  canReadPartnerById,
  isValidPartnerId,
} from '../partners/partnerPolicy'
import {
  isOriginType,
  validateOrigin,
  type OriginAddressForm,
} from '../partners/originValidation'

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
const origins = ref<Origin[]>([])
const stores = ref<Store[]>([])
const loading = ref(false)
const loadingStores = ref(false)
const saving = ref(false)
const errorMessage = ref('')
const storeError = ref('')
const statusMessage = ref('')
const formOpen = ref(false)
const formError = ref('')
const editingOrigin = ref<Origin | null>(null)
const formType = ref('STORE')
const formStoreId = ref('')
const lookupId = ref('')
const lookupLoading = ref(false)
const lookupError = ref('')
const lookupOrigin = ref<Origin | null>(null)
const address = reactive<OriginAddressForm>(emptyAddress())

const role = computed(() => (isDevMode ? devActor.role : null))
const canManage = computed(() => canManagePartners(role.value))
const canList = computed(() => canListPartners(role.value))
const isStoreProfile = computed(() => isDevMode && devActor.role === 'STORE')
const canReadById = computed(() => canReadPartnerById(role.value))
const pageCount = computed(() => Math.max(1, Math.ceil(totalItems.value / pageSize.value)))
const canApplyProfile = computed(
  () => selectedRole.value !== 'STORE' || isValidPartnerId(selectedStoreId.value),
)

onMounted(() => {
  if (canList.value) {
    void loadOrigins()
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
  lookupOrigin.value = null
  closeForm()
  if (canList.value) {
    void loadOrigins()
    void loadStores()
  } else {
    origins.value = []
    totalItems.value = 0
    stores.value = []
  }
}

async function loadOrigins() {
  if (!canList.value) {
    return
  }
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await api.listOrigins(page.value, pageSize.value)
    origins.value = result.items
    totalItems.value = result.totalItems
  } catch (error) {
    origins.value = []
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
  void loadOrigins()
}

function changePageSize(event: Event) {
  const target = event.target
  if (!(target instanceof HTMLSelectElement)) {
    return
  }
  pageSize.value = Number(target.value)
  page.value = 0
  void loadOrigins()
}

function openCreateForm() {
  editingOrigin.value = null
  formType.value = 'STORE'
  formStoreId.value = ''
  Object.assign(address, emptyAddress())
  formError.value = ''
  formOpen.value = true
}

function openEditForm(origin: Origin) {
  editingOrigin.value = origin
  formType.value = origin.type
  formStoreId.value = origin.storeId
  Object.assign(address, {
    line1: origin.address.line1,
    line2: origin.address.line2 ?? '',
    city: origin.address.city,
    state: origin.address.state,
    postalCode: origin.address.postalCode ?? '',
    country: origin.address.country,
    latitude: String(origin.address.latitude),
    longitude: String(origin.address.longitude),
  })
  formError.value = ''
  formOpen.value = true
}

function closeForm() {
  formOpen.value = false
  formError.value = ''
}

function originAddressPayload() {
  return {
    line1: address.line1,
    line2: address.line2 === '' ? null : address.line2,
    city: address.city,
    state: address.state,
    ...(address.postalCode === '' ? {} : { postalCode: address.postalCode }),
    country: address.country,
    latitude: Number(address.latitude),
    longitude: Number(address.longitude),
  }
}

async function saveOrigin() {
  if (!canManage.value) {
    return
  }
  const validationMessage = validateOrigin(formType.value, formStoreId.value, address)
  if (validationMessage !== null || !isOriginType(formType.value)) {
    formError.value = validationMessage ?? 'Selecione o tipo da origem.'
    return
  }

  saving.value = true
  formError.value = ''
  statusMessage.value = ''
  try {
    const payload = {
      type: formType.value,
      storeId: formStoreId.value,
      address: originAddressPayload(),
    }
    if (editingOrigin.value) {
      await api.patchOrigin(editingOrigin.value.id, payload)
      statusMessage.value = 'Origem atualizada.'
    } else {
      await api.createOrigin({ ...payload, active: true })
      statusMessage.value = 'Origem criada.'
    }
    closeForm()
    await loadOrigins()
  } catch (error) {
    formError.value = formatApiError(error)
  } finally {
    saving.value = false
  }
}

async function inactivateOrigin(origin: Origin) {
  if (!canManage.value || !origin.active) {
    return
  }
  if (!window.confirm('Inativar esta origem?')) {
    return
  }

  statusMessage.value = ''
  errorMessage.value = ''
  try {
    await api.patchOrigin(origin.id, { active: false })
    statusMessage.value = 'Origem inativada.'
    await loadOrigins()
  } catch (error) {
    errorMessage.value = formatApiError(error)
  }
}

async function lookupOriginById() {
  if (!canReadById.value) {
    return
  }
  if (!isValidPartnerId(lookupId.value)) {
    lookupOrigin.value = null
    lookupError.value = 'Informe um UUID válido para consultar a origem.'
    return
  }

  lookupLoading.value = true
  lookupError.value = ''
  lookupOrigin.value = null
  try {
    lookupOrigin.value = await api.getOrigin(lookupId.value.trim())
  } catch (error) {
    lookupError.value = formatApiError(error)
  } finally {
    lookupLoading.value = false
  }
}

function emptyAddress(): OriginAddressForm {
  return {
    line1: '',
    line2: '',
    city: '',
    state: '',
    postalCode: '',
    country: 'BR',
    latitude: '',
    longitude: '',
  }
}
</script>

<template>
  <section class="stores-page" aria-labelledby="origins-title">
    <div class="page-heading">
      <div>
        <p class="eyebrow">Cadastros</p>
        <h1 id="origins-title">Origens</h1>
      </div>
      <button v-if="canManage" type="button" class="primary-button" @click="openCreateForm">
        Cadastrar origem
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
      <h2 id="form-title">{{ editingOrigin ? 'Editar origem' : 'Cadastrar origem' }}</h2>
      <form @submit.prevent="saveOrigin">
        <label for="origin-type">Tipo da origem</label>
        <select id="origin-type" v-model="formType">
          <option value="STORE">Loja</option>
          <option value="DISTRIBUTION_CENTER">Centro de distribuição</option>
        </select>

        <label for="origin-store">Loja vinculada</label>
        <select id="origin-store" v-model="formStoreId" required :disabled="loadingStores">
          <option value="" disabled>Selecione uma loja</option>
          <option v-for="store in stores" :key="store.id" :value="store.id">
            {{ store.name }}{{ store.active ? '' : ' (inativa)' }}
          </option>
        </select>

        <label for="origin-line1">Endereço — linha 1</label>
        <input id="origin-line1" v-model="address.line1" maxlength="200" required />
        <span class="field-help">Obrigatório, até 200 caracteres.</span>

        <label for="origin-line2">Complemento</label>
        <input id="origin-line2" v-model="address.line2" maxlength="100" />
        <span class="field-help">Opcional, até 100 caracteres.</span>

        <label for="origin-city">Cidade</label>
        <input id="origin-city" v-model="address.city" maxlength="100" required />

        <label for="origin-state">UF</label>
        <input id="origin-state" v-model="address.state" maxlength="2" required />

        <label for="origin-postal-code">CEP</label>
        <input id="origin-postal-code" v-model="address.postalCode" placeholder="00000-000" />
        <span class="field-help">Opcional: 8 dígitos, com ou sem hífen.</span>

        <label for="origin-country">País</label>
        <input id="origin-country" v-model="address.country" maxlength="2" required />
        <span class="field-help">No MVP, informe BR.</span>

        <label for="origin-latitude">Latitude</label>
        <input
          id="origin-latitude"
          v-model="address.latitude"
          type="number"
          min="-90"
          max="90"
          step="any"
          required
        />

        <label for="origin-longitude">Longitude</label>
        <input
          id="origin-longitude"
          v-model="address.longitude"
          type="number"
          min="-180"
          max="180"
          step="any"
          required
        />

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
      <h2 id="lookup-title">Consultar origem da minha loja</h2>
      <form class="lookup-form" @submit.prevent="lookupOriginById">
        <label for="origin-lookup-id">UUID da origem</label>
        <input id="origin-lookup-id" v-model="lookupId" type="text" autocomplete="off" />
        <button type="submit" class="secondary-button" :disabled="lookupLoading">
          {{ lookupLoading ? 'Consultando…' : 'Consultar' }}
        </button>
      </form>
      <p v-if="lookupError" class="notice error" role="alert">{{ lookupError }}</p>
      <article v-if="lookupOrigin" class="lookup-result">
        <h3>{{ lookupOrigin.type === 'STORE' ? 'Origem de loja' : 'Centro de distribuição' }}</h3>
        <p>{{ lookupOrigin.address.line1 }}<span v-if="lookupOrigin.address.line2">, {{ lookupOrigin.address.line2 }}</span></p>
        <p>{{ lookupOrigin.address.city }}, {{ lookupOrigin.address.state }}</p>
        <p>{{ lookupOrigin.active ? 'Ativa' : 'Inativa' }}</p>
      </article>
    </section>

    <section v-else-if="canList" class="list-panel" aria-labelledby="list-title">
      <div class="list-heading">
        <div>
          <h2 id="list-title">Origens cadastradas</h2>
          <p class="field-help">{{ totalItems }} origem(ns)</p>
        </div>
        <div class="list-controls">
          <label for="page-size">Itens por página</label>
          <select id="page-size" :value="pageSize" :disabled="loading" @change="changePageSize">
            <option :value="10">10</option>
            <option :value="20">20</option>
            <option :value="50">50</option>
            <option :value="100">100</option>
          </select>
          <button type="button" class="secondary-button" :disabled="loading" @click="loadOrigins">
            Atualizar
          </button>
        </div>
      </div>

      <p v-if="loading" class="empty-state" role="status">Carregando origens…</p>
      <div v-else-if="origins.length > 0" class="table-scroll">
        <table>
          <caption class="sr-only">Origens, lojas vinculadas e situação</caption>
          <thead>
            <tr>
              <th scope="col">Tipo</th>
              <th scope="col">Endereço</th>
              <th scope="col">Loja</th>
              <th scope="col">Situação</th>
              <th v-if="canManage" scope="col">Ações</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="origin in origins" :key="origin.id">
              <td>{{ origin.type === 'STORE' ? 'Loja' : 'Centro de distribuição' }}</td>
              <td>{{ origin.address.line1 }}, {{ origin.address.city }} - {{ origin.address.state }}</td>
              <td>{{ stores.find((store) => store.id === origin.storeId)?.name ?? origin.storeId }}</td>
              <td>{{ origin.active ? 'Ativa' : 'Inativa' }}</td>
              <td v-if="canManage">
                <div class="row-actions">
                  <button type="button" class="text-button" @click="openEditForm(origin)">Editar</button>
                  <button
                    v-if="origin.active"
                    type="button"
                    class="text-button danger-text"
                    @click="inactivateOrigin(origin)"
                  >
                    Inativar
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <p v-else-if="!errorMessage" class="empty-state">Nenhuma origem encontrada.</p>

      <div class="pagination" aria-label="Paginação das origens">
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
    <p v-else class="notice error" role="alert">Seu perfil não tem acesso ao cadastro de origens.</p>
  </section>
</template>
