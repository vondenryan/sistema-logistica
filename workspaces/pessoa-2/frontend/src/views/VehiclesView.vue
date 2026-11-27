<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api, listAllCarriers, type Carrier, type FleetType, type Vehicle } from '../api'
import { currentRole } from '../devActor'
import { canManagePartners } from '../partners/partnerPolicy'
import { formatFleetError } from '../fleet/fleetError'
import {
  toVehicleDetails,
  validateVehicleForm,
  type VehicleForm,
} from '../fleet/fleetValidation'

const page = ref(0)
const pageSize = ref(20)
const totalItems = ref(0)
const vehicles = ref<Vehicle[]>([])
const carriers = ref<Carrier[]>([])
const loading = ref(false)
const loadingCarriers = ref(false)
const saving = ref(false)
const errorMessage = ref('')
const carrierError = ref('')
const statusMessage = ref('')
const formOpen = ref(false)
const formError = ref('')
const formIdentifier = ref('')
const formCapacityKg = ref('')
const formFleetType = ref<FleetType>('OWN')
const formCarrierId = ref('')
const editingVehicle = ref<Vehicle | null>(null)

const role = computed(() => (currentRole.value))
const canManage = computed(() => canManagePartners(role.value))
const pageCount = computed(() => Math.max(1, Math.ceil(totalItems.value / pageSize.value)))

onMounted(() => {
  if (canManage.value) {
    void loadVehicles()
    void loadCarriers()
  }
})

async function loadVehicles() {
  if (!canManage.value) {
    return
  }
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await api.listVehicles(page.value, pageSize.value)
    vehicles.value = result.items
    totalItems.value = result.totalItems
  } catch (error) {
    vehicles.value = []
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
  void loadVehicles()
}

function changePageSize(event: Event) {
  const target = event.target
  if (!(target instanceof HTMLSelectElement)) {
    return
  }
  pageSize.value = Number(target.value)
  page.value = 0
  void loadVehicles()
}

function openCreateForm() {
  editingVehicle.value = null
  formIdentifier.value = ''
  formCapacityKg.value = ''
  formFleetType.value = 'OWN'
  formCarrierId.value = ''
  formError.value = ''
  formOpen.value = true
}

function openEditForm(vehicle: Vehicle) {
  editingVehicle.value = vehicle
  formIdentifier.value = vehicle.identifier
  formCapacityKg.value = vehicle.capacityKg
  formFleetType.value = vehicle.fleetType
  formCarrierId.value = vehicle.carrierId ?? ''
  formError.value = ''
  formOpen.value = true
}

function handleFleetTypeChange(event: Event) {
  const target = event.target
  if (!(target instanceof HTMLSelectElement)) {
    return
  }
  if (target.value === 'OWN') {
    formFleetType.value = 'OWN'
    formCarrierId.value = ''
  } else if (target.value === 'THIRD_PARTY') {
    formFleetType.value = 'THIRD_PARTY'
  }
}

function closeForm() {
  formOpen.value = false
  formError.value = ''
}

function currentForm(): VehicleForm {
  return {
    identifier: formIdentifier.value,
    capacityKg: formCapacityKg.value,
    fleetType: formFleetType.value,
    carrierId: formCarrierId.value,
  }
}

async function saveVehicle() {
  if (!canManage.value) {
    return
  }
  const form = currentForm()
  const validationMessage = validateVehicleForm(form)
  if (validationMessage !== null) {
    formError.value = validationMessage
    return
  }

  saving.value = true
  formError.value = ''
  statusMessage.value = ''
  const details = toVehicleDetails(form)
  try {
    if (editingVehicle.value) {
      await api.patchVehicle(editingVehicle.value.id, details)
      statusMessage.value = 'Veículo atualizado.'
    } else {
      await api.createVehicle({ ...details, active: true })
      statusMessage.value = 'Veículo cadastrado.'
    }
    closeForm()
    await loadVehicles()
  } catch (error) {
    formError.value = formatFleetError(error, 'Já existe um veículo com esse identifier.')
  } finally {
    saving.value = false
  }
}

async function inactivateVehicle(vehicle: Vehicle) {
  if (!canManage.value || !vehicle.active) {
    return
  }
  if (!window.confirm(`Inativar o veículo "${vehicle.identifier}"?`)) {
    return
  }

  statusMessage.value = ''
  errorMessage.value = ''
  try {
    await api.patchVehicle(vehicle.id, { active: false })
    statusMessage.value = 'Veículo inativado.'
    await loadVehicles()
  } catch (error) {
    errorMessage.value = formatFleetError(error)
  }
}
</script>

<template>
  <section class="stores-page" aria-labelledby="vehicles-title">
    <div class="page-heading">
      <div>
        <p class="eyebrow">Cadastros</p>
        <h1 id="vehicles-title">Veículos</h1>
      </div>
      <button v-if="canManage" type="button" class="primary-button" @click="openCreateForm">
        Cadastrar veículo
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
      <h2 id="form-title">{{ editingVehicle ? 'Editar veículo' : 'Cadastrar veículo' }}</h2>
      <form @submit.prevent="saveVehicle">
        <label for="vehicle-identifier">Identifier</label>
        <input
          id="vehicle-identifier"
          v-model="formIdentifier"
          type="text"
          maxlength="40"
          required
          :aria-invalid="formError ? 'true' : 'false'"
        />
        <span class="field-help">De 1 a 40 caracteres.</span>

        <label for="vehicle-capacity">Capacidade (kg)</label>
        <input
          id="vehicle-capacity"
          v-model="formCapacityKg"
          type="text"
          inputmode="decimal"
          placeholder="1000.000"
          required
          :aria-invalid="formError ? 'true' : 'false'"
        />
        <span class="field-help">String decimal positiva, com até três casas, compatível com decimal(12,3).</span>

        <label for="vehicle-fleet-type">Tipo de frota</label>
        <select
          id="vehicle-fleet-type"
          v-model="formFleetType"
          @change="handleFleetTypeChange"
        >
          <option value="OWN">Própria (OWN)</option>
          <option value="THIRD_PARTY">Terceirizada (THIRD_PARTY)</option>
        </select>

        <template v-if="formFleetType === 'THIRD_PARTY'">
          <label for="vehicle-carrier">Transportadora</label>
          <select
            id="vehicle-carrier"
            v-model="formCarrierId"
            required
            :disabled="loadingCarriers"
          >
            <option value="" disabled>Selecione uma transportadora</option>
            <option v-for="carrier in carriers" :key="carrier.id" :value="carrier.id">
              {{ carrier.name }}{{ carrier.active ? '' : ' (inativa)' }}
            </option>
          </select>
        </template>
        <span v-else class="field-help">
          Frota própria não envia transportadora; mudar para OWN remove o vínculo existente.
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
          <h2 id="list-title">Veículos cadastrados</h2>
          <p class="field-help">{{ totalItems }} veículo(s)</p>
        </div>
        <div class="list-controls">
          <label for="page-size">Itens por página</label>
          <select id="page-size" :value="pageSize" :disabled="loading" @change="changePageSize">
            <option :value="10">10</option>
            <option :value="20">20</option>
            <option :value="50">50</option>
            <option :value="100">100</option>
          </select>
          <button type="button" class="secondary-button" :disabled="loading" @click="loadVehicles">
            Atualizar
          </button>
        </div>
      </div>

      <p v-if="loading" class="empty-state" role="status">Carregando veículos…</p>
      <div v-else-if="vehicles.length > 0" class="table-scroll">
        <table>
          <caption class="sr-only">Veículos, capacidade, frota e situação</caption>
          <thead>
            <tr>
              <th scope="col">Identifier</th>
              <th scope="col">Capacidade (kg)</th>
              <th scope="col">Tipo de frota</th>
              <th scope="col">Transportadora</th>
              <th scope="col">Situação</th>
              <th scope="col">Ações</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="vehicle in vehicles" :key="vehicle.id">
              <td>{{ vehicle.identifier }}</td>
              <td>{{ vehicle.capacityKg }}</td>
              <td>{{ vehicle.fleetType === 'OWN' ? 'Própria' : 'Terceirizada' }}</td>
              <td>
                {{ carriers.find((carrier) => carrier.id === vehicle.carrierId)?.name ?? (vehicle.carrierId ? 'Transportadora não carregada' : '—') }}
              </td>
              <td>{{ vehicle.active ? 'Ativo' : 'Inativo' }}</td>
              <td>
                <div class="row-actions">
                  <button type="button" class="text-button" @click="openEditForm(vehicle)">
                    Editar
                  </button>
                  <button
                    v-if="vehicle.active"
                    type="button"
                    class="text-button danger-text"
                    @click="inactivateVehicle(vehicle)"
                  >
                    Inativar
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <p v-else-if="!errorMessage" class="empty-state">Nenhum veículo encontrado.</p>

      <div class="pagination" aria-label="Paginação dos veículos">
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
      Seu perfil não tem acesso ao cadastro de veículos.
    </p>
  </section>
</template>
