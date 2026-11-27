<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api, listAllCarriers, type Carrier, type Driver } from '../api'
import { currentRole } from '../devActor'
import { canManagePartners } from '../partners/partnerPolicy'
import { formatFleetError } from '../fleet/fleetError'
import { validateDriverForm } from '../fleet/fleetValidation'

const page = ref(0)
const pageSize = ref(20)
const totalItems = ref(0)
const drivers = ref<Driver[]>([])
const carriers = ref<Carrier[]>([])
const loading = ref(false)
const loadingCarriers = ref(false)
const saving = ref(false)
const errorMessage = ref('')
const carrierError = ref('')
const statusMessage = ref('')
const formOpen = ref(false)
const formError = ref('')
const formName = ref('')
const formCarrierId = ref('')
const editingDriver = ref<Driver | null>(null)

const role = computed(() => (currentRole.value))
const canManage = computed(() => canManagePartners(role.value))
const pageCount = computed(() => Math.max(1, Math.ceil(totalItems.value / pageSize.value)))

onMounted(() => {
  if (canManage.value) {
    void loadDrivers()
    void loadCarriers()
  }
})

async function loadDrivers() {
  if (!canManage.value) {
    return
  }
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await api.listDrivers(page.value, pageSize.value)
    drivers.value = result.items
    totalItems.value = result.totalItems
  } catch (error) {
    drivers.value = []
    totalItems.value = 0
    errorMessage.value = formatFleetError(error)
  } finally {
    loading.value = false
  }
}

async function loadCarriers() {
  loadingCarriers.value = true
  carrierError.value = ''
  try {
    carriers.value = await listAllCarriers()
  } catch (error) {
    carriers.value = []
    carrierError.value = formatFleetError(error)
  } finally {
    loadingCarriers.value = false
  }
}

function changePage(nextPage: number) {
  if (nextPage < 0 || nextPage >= pageCount.value) {
    return
  }
  page.value = nextPage
  void loadDrivers()
}

function changePageSize(event: Event) {
  const target = event.target
  if (!(target instanceof HTMLSelectElement)) {
    return
  }
  pageSize.value = Number(target.value)
  page.value = 0
  void loadDrivers()
}

function openCreateForm() {
  editingDriver.value = null
  formName.value = ''
  formCarrierId.value = ''
  formError.value = ''
  formOpen.value = true
}

function openEditForm(driver: Driver) {
  editingDriver.value = driver
  formName.value = driver.name
  formCarrierId.value = driver.carrierId ?? ''
  formError.value = ''
  formOpen.value = true
}

function closeForm() {
  formOpen.value = false
  formError.value = ''
}

async function saveDriver() {
  if (!canManage.value) {
    return
  }
  const validationMessage = validateDriverForm(formName.value, formCarrierId.value)
  if (validationMessage !== null) {
    formError.value = validationMessage
    return
  }

  saving.value = true
  formError.value = ''
  statusMessage.value = ''
  const driver = {
    name: formName.value,
    ...(formCarrierId.value.trim() === '' ? {} : { carrierId: formCarrierId.value }),
  }
  try {
    if (editingDriver.value) {
      await api.patchDriver(editingDriver.value.id, driver)
      statusMessage.value = 'Motorista atualizado.'
    } else {
      await api.createDriver({ ...driver, active: true })
      statusMessage.value = 'Motorista cadastrado.'
    }
    closeForm()
    await loadDrivers()
  } catch (error) {
    formError.value = formatFleetError(error)
  } finally {
    saving.value = false
  }
}

async function inactivateDriver(driver: Driver) {
  if (!canManage.value || !driver.active) {
    return
  }
  if (!window.confirm(`Inativar o motorista "${driver.name}"?`)) {
    return
  }

  statusMessage.value = ''
  errorMessage.value = ''
  try {
    await api.patchDriver(driver.id, { active: false })
    statusMessage.value = 'Motorista inativado.'
    await loadDrivers()
  } catch (error) {
    errorMessage.value = formatFleetError(error)
  }
}
</script>

<template>
  <section class="stores-page" aria-labelledby="drivers-title">
    <div class="page-heading">
      <div>
        <p class="eyebrow">Cadastros</p>
        <h1 id="drivers-title">Motoristas</h1>
      </div>
      <button v-if="canManage" type="button" class="primary-button" @click="openCreateForm">
        Cadastrar motorista
      </button>
    </div>

    <p v-if="statusMessage" class="notice success" role="status" aria-live="polite">
      {{ statusMessage }}
    </p>
    <p v-if="errorMessage" class="notice error" role="alert">{{ errorMessage }}</p>
    <p v-if="carrierError && canManage" class="notice error" role="alert">
      Não foi possível carregar as transportadoras: {{ carrierError }}
    </p>

    <section v-if="formOpen && canManage" class="store-form-panel" aria-labelledby="form-title">
      <h2 id="form-title">{{ editingDriver ? 'Editar motorista' : 'Cadastrar motorista' }}</h2>
      <form @submit.prevent="saveDriver">
        <label for="driver-name">Nome do motorista</label>
        <input
          id="driver-name"
          v-model="formName"
          type="text"
          maxlength="160"
          required
          :aria-invalid="formError ? 'true' : 'false'"
        />
        <span class="field-help">De 1 a 160 caracteres.</span>

        <label for="driver-carrier">Transportadora (opcional)</label>
        <select id="driver-carrier" v-model="formCarrierId" :disabled="loadingCarriers">
          <option value="" :disabled="Boolean(editingDriver?.carrierId)">
            {{ editingDriver?.carrierId ? 'Vínculo atual não pode ser removido' : 'Sem transportadora' }}
          </option>
          <option v-for="carrier in carriers" :key="carrier.id" :value="carrier.id">
            {{ carrier.name }}{{ carrier.active ? '' : ' (inativa)' }}
          </option>
        </select>
        <span class="field-help">
          O vínculo existente não pode ser removido pelo PATCH; escolha outra transportadora para alterá-lo.
        </span>

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

    <section v-if="canManage" class="list-panel" aria-labelledby="list-title">
      <div class="list-heading">
        <div>
          <h2 id="list-title">Motoristas cadastrados</h2>
          <p class="field-help">{{ totalItems }} motorista(s)</p>
        </div>
        <div class="list-controls">
          <label for="page-size">Itens por página</label>
          <select id="page-size" :value="pageSize" :disabled="loading" @change="changePageSize">
            <option :value="10">10</option>
            <option :value="20">20</option>
            <option :value="50">50</option>
            <option :value="100">100</option>
          </select>
          <button type="button" class="secondary-button" :disabled="loading" @click="loadDrivers">
            Atualizar
          </button>
        </div>
      </div>

      <p v-if="loading" class="empty-state" role="status">Carregando motoristas…</p>
      <div v-else-if="drivers.length > 0" class="table-scroll">
        <table>
          <caption class="sr-only">Motoristas, transportadoras e situação</caption>
          <thead>
            <tr>
              <th scope="col">Nome</th>
              <th scope="col">Transportadora</th>
              <th scope="col">Situação</th>
              <th scope="col">Ações</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="driver in drivers" :key="driver.id">
              <td>{{ driver.name }}</td>
              <td>
                {{ carriers.find((carrier) => carrier.id === driver.carrierId)?.name ?? (driver.carrierId ? 'Transportadora não carregada' : 'Próprio') }}
              </td>
              <td>{{ driver.active ? 'Ativo' : 'Inativo' }}</td>
              <td>
                <div class="row-actions">
                  <button type="button" class="text-button" @click="openEditForm(driver)">
                    Editar
                  </button>
                  <button
                    v-if="driver.active"
                    type="button"
                    class="text-button danger-text"
                    @click="inactivateDriver(driver)"
                  >
                    Inativar
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <p v-else-if="!errorMessage" class="empty-state">Nenhum motorista encontrado.</p>

      <div class="pagination" aria-label="Paginação dos motoristas">
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
    <p v-else class="notice error" role="alert">
      Seu perfil não tem acesso ao cadastro de motoristas.
    </p>
  </section>
</template>
