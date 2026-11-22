<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api, formatApiError, type Store } from '../api'
import { devActor, isDevMode, type DevRole } from '../devActor'
import { canManageStores, isValidStoreId } from '../stores/storePolicy'
import { validateStoreName } from '../stores/storeValidation'

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
const stores = ref<Store[]>([])
const loading = ref(false)
const saving = ref(false)
const errorMessage = ref('')
const statusMessage = ref('')
const formOpen = ref(false)
const formName = ref('')
const formError = ref('')
const editingStore = ref<Store | null>(null)

const canManage = computed(() => canManageStores(isDevMode ? devActor.role : null))
const isStoreProfile = computed(() => isDevMode && devActor.role === 'STORE')
const pageCount = computed(() => Math.max(1, Math.ceil(totalItems.value / pageSize.value)))
const canApplyProfile = computed(
  () => selectedRole.value !== 'STORE' || isValidStoreId(selectedStoreId.value),
)

onMounted(() => {
  void loadStores()
})

function applyDevProfile() {
  devActor.role = selectedRole.value
  devActor.storeId = selectedRole.value === 'STORE' ? selectedStoreId.value.trim() : ''
  page.value = 0
  if (selectedRole.value !== 'STORE') {
    pageSize.value = 20
  }
  statusMessage.value = ''
  closeForm()
  void loadStores()
}

async function loadStores() {
  loading.value = true
  errorMessage.value = ''

  try {
    if (isStoreProfile.value) {
      if (!isValidStoreId(devActor.storeId)) {
        stores.value = []
        totalItems.value = 0
        errorMessage.value = 'Informe um UUID válido para consultar a loja do perfil STORE.'
        return
      }
      const store = await api.getStore(devActor.storeId)
      stores.value = [store]
      totalItems.value = 1
      page.value = 0
      pageSize.value = 1
      return
    }

    const result = await api.listStores(page.value, pageSize.value)
    stores.value = result.items
    totalItems.value = result.totalItems
  } catch (error) {
    stores.value = []
    totalItems.value = 0
    errorMessage.value = formatApiError(error)
  } finally {
    loading.value = false
  }
}

function changePage(nextPage: number) {
  if (nextPage < 0 || nextPage >= pageCount.value) {
    return
  }
  page.value = nextPage
  void loadStores()
}

function changePageSize(event: Event) {
  const target = event.target
  if (!(target instanceof HTMLSelectElement)) {
    return
  }
  pageSize.value = Number(target.value)
  page.value = 0
  void loadStores()
}

function openCreateForm() {
  editingStore.value = null
  formName.value = ''
  formError.value = ''
  formOpen.value = true
}

function openEditForm(store: Store) {
  editingStore.value = store
  formName.value = store.name
  formError.value = ''
  formOpen.value = true
}

function closeForm() {
  formOpen.value = false
  formError.value = ''
}

async function saveStore() {
  if (!canManage.value) {
    return
  }
  const validationMessage = validateStoreName(formName.value)
  if (validationMessage !== null) {
    formError.value = validationMessage
    return
  }

  saving.value = true
  formError.value = ''
  statusMessage.value = ''
  try {
    if (editingStore.value) {
      await api.patchStore(editingStore.value.id, { name: formName.value })
      statusMessage.value = 'Loja atualizada.'
    } else {
      await api.createStore(formName.value)
      statusMessage.value = 'Loja criada.'
    }
    closeForm()
    await loadStores()
  } catch (error) {
    formError.value = formatApiError(error)
  } finally {
    saving.value = false
  }
}

async function inactivateStore(store: Store) {
  if (!canManage.value || !store.active) {
    return
  }
  if (!window.confirm(`Inativar a loja "${store.name}"?`)) {
    return
  }

  statusMessage.value = ''
  errorMessage.value = ''
  try {
    await api.patchStore(store.id, { active: false })
    statusMessage.value = 'Loja inativada.'
    await loadStores()
  } catch (error) {
    errorMessage.value = formatApiError(error)
  }
}
</script>

<template>
  <section class="stores-page" aria-labelledby="stores-title">
    <div class="page-heading">
      <div>
        <p class="eyebrow">Cadastros</p>
        <h1 id="stores-title">Lojas</h1>
      </div>
      <button v-if="canManage" type="button" class="primary-button" @click="openCreateForm">
        Cadastrar loja
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
          <option v-for="role in roles" :key="role.value" :value="role.value">
            {{ role.label }}
          </option>
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
          <span id="store-id-help" class="field-help">Informe a loja autorizada para o perfil.</span>
        </template>
        <button type="submit" class="secondary-button" :disabled="!canApplyProfile">
          Aplicar perfil
        </button>
      </form>
    </section>

    <p v-if="statusMessage" class="notice success" role="status" aria-live="polite">
      {{ statusMessage }}
    </p>
    <p v-if="errorMessage" class="notice error" role="alert">
      {{ errorMessage }}
    </p>

    <section v-if="formOpen && canManage" class="store-form-panel" aria-labelledby="form-title">
      <h2 id="form-title">{{ editingStore ? 'Editar loja' : 'Cadastrar loja' }}</h2>
      <form @submit.prevent="saveStore">
        <label for="store-name">Nome da loja</label>
        <input
          id="store-name"
          v-model="formName"
          type="text"
          maxlength="160"
          required
          aria-describedby="store-name-help"
          :aria-invalid="formError ? 'true' : 'false'"
        />
        <span id="store-name-help" class="field-help">De 1 a 160 caracteres.</span>
        <p v-if="formError" class="notice error" role="alert">{{ formError }}</p>
        <div class="form-actions">
          <button type="submit" class="primary-button" :disabled="saving">
            {{ saving ? 'Salvando…' : 'Salvar' }}
          </button>
          <button type="button" class="secondary-button" :disabled="saving" @click="closeForm">
            Cancelar
          </button>
        </div>
      </form>
    </section>

    <section class="list-panel" aria-labelledby="list-title">
      <div class="list-heading">
        <div>
          <h2 id="list-title">{{ isStoreProfile ? 'Minha loja' : 'Lojas cadastradas' }}</h2>
          <p v-if="!isStoreProfile" class="field-help">{{ totalItems }} loja(s)</p>
        </div>
        <div class="list-controls">
          <label v-if="!isStoreProfile" for="page-size">Itens por página</label>
          <select
            v-if="!isStoreProfile"
            id="page-size"
            :value="pageSize"
            :disabled="loading"
            @change="changePageSize"
          >
            <option :value="10">10</option>
            <option :value="20">20</option>
            <option :value="50">50</option>
            <option :value="100">100</option>
          </select>
          <button type="button" class="secondary-button" :disabled="loading" @click="loadStores">
            Atualizar
          </button>
        </div>
      </div>

      <p v-if="loading" class="empty-state" role="status">Carregando lojas…</p>
      <div v-else-if="stores.length > 0" class="table-scroll">
        <table>
          <caption class="sr-only">Lojas e situação de atividade</caption>
          <thead>
            <tr>
              <th scope="col">Nome</th>
              <th scope="col">Situação</th>
              <th v-if="canManage" scope="col">Ações</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="store in stores" :key="store.id">
              <td>{{ store.name }}</td>
              <td>
                <span :class="['status-badge', store.active ? 'is-active' : 'is-inactive']">
                  {{ store.active ? 'Ativa' : 'Inativa' }}
                </span>
              </td>
              <td v-if="canManage">
                <div class="row-actions">
                  <button type="button" class="text-button" @click="openEditForm(store)">
                    Editar
                  </button>
                  <button
                    v-if="store.active"
                    type="button"
                    class="text-button danger-text"
                    @click="inactivateStore(store)"
                  >
                    Inativar
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <p v-else-if="!errorMessage" class="empty-state">Nenhuma loja encontrada.</p>

      <div v-if="!isStoreProfile" class="pagination" aria-label="Paginação das lojas">
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
  </section>
</template>
