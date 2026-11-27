<script setup lang="ts">
import { computed, ref } from 'vue'
import { ApiError, api, type Carrier, type Driver, type FleetAssignment, type Vehicle } from '../api'
import { isDevMode, devActor } from '../devActor'
import { canManagePartners } from '../partners/partnerPolicy'
import { validateFleetAssignment, isValidAssignmentRouteId } from '../fleet/assignmentValidation'
import { formatFleetError } from '../fleet/fleetError'

const demoRouteIds = [
  '33333333-3333-4333-8333-333333333333',
  '33333333-3333-4333-8333-333333333334',
  '33333333-3333-4333-8333-333333333335',
  '33333333-3333-4333-8333-333333333336',
  '33333333-3333-4333-8333-333333333337',
]
const demoOriginId = '11111111-1111-4111-8111-111111111111'

interface AssignmentDetails {
  snapshot: FleetAssignment
  driver: Driver | null
  vehicle: Vehicle | null
  carrier: Carrier | null
}

const role = computed(() => (isDevMode ? devActor.role : null))
const canManage = computed(() => canManagePartners(role.value))
const lookupRouteId = ref('')
const lookupLoading = ref(false)
const lookupError = ref('')
const assignment = ref<AssignmentDetails | null>(null)
const demoRouteId = ref(demoRouteIds[0])
const demoCapacityKg = ref('40.000')
const saving = ref(false)
const formError = ref('')
const statusMessage = ref('')

async function lookupAssignment() {
  if (!canManage.value) {
    return
  }
  if (!isValidAssignmentRouteId(lookupRouteId.value)) {
    assignment.value = null
    lookupError.value = 'Informe um UUID válido para consultar a atribuição.'
    return
  }

  lookupLoading.value = true
  lookupError.value = ''
  assignment.value = null
  statusMessage.value = ''
  try {
    const snapshot = await api.getFleetAssignment(lookupRouteId.value.trim())
    assignment.value = emptyAssignmentDetails(snapshot)
    await loadAssignmentDetails(snapshot)
  } catch (error) {
    lookupError.value = formatAssignmentError(error)
  } finally {
    lookupLoading.value = false
  }
}

async function createDemoAssignment() {
  if (!isDevMode || !canManage.value) {
    return
  }
  const validationMessage = validateFleetAssignment(
    demoRouteId.value,
    demoOriginId,
    demoCapacityKg.value,
  )
  if (validationMessage !== null) {
    formError.value = validationMessage
    return
  }

  saving.value = true
  formError.value = ''
  lookupError.value = ''
  statusMessage.value = ''
  assignment.value = null
  let created: FleetAssignment
  try {
    created = await api.createFleetAssignment({
      routeId: demoRouteId.value,
      originId: demoOriginId,
      requiredCapacityKg: demoCapacityKg.value,
    })
  } catch (error) {
    formError.value = formatAssignmentError(error)
    saving.value = false
    return
  }
  saving.value = false

  lookupRouteId.value = created.routeId
  statusMessage.value = 'Atribuição criada.'
  assignment.value = emptyAssignmentDetails(created)
  try {
    await loadAssignmentDetails(created)
  } catch (error) {
    lookupError.value = `A atribuição foi criada, mas não foi possível carregar os detalhes: ${formatAssignmentError(error)}`
  }
}

function emptyAssignmentDetails(snapshot: FleetAssignment): AssignmentDetails {
  return { snapshot, driver: null, vehicle: null, carrier: null }
}

async function loadAssignmentDetails(snapshot: FleetAssignment) {
  const [driver, vehicle] = await Promise.all([
    api.getDriver(snapshot.driverId),
    api.getVehicle(snapshot.vehicleId),
  ])
  const carrierId = snapshot.carrierId ?? vehicle.carrierId
  const carrier = carrierId ? await api.getCarrier(carrierId) : null
  assignment.value = { snapshot, driver, vehicle, carrier }
}

function formatAssignmentError(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.code === 'NO_ELIGIBLE_RESOURCE') {
      return 'Não há motorista e veículo elegíveis para a origem e capacidade solicitadas.'
    }
    if (error.code === 'IDEMPOTENCY_CONFLICT') {
      return 'Esta rota já foi atribuída com dados diferentes. Consulte a atribuição existente ou selecione outra rota.'
    }
    if (error.code === 'NOT_FOUND') {
      return 'Não foi encontrada uma atribuição, rota ou origem correspondente. Verifique o UUID informado.'
    }
  }
  return formatFleetError(error)
}
</script>

<template>
  <section class="stores-page" aria-labelledby="fleet-assignment-title">
    <div class="page-heading">
      <div>
        <p class="eyebrow">Frota</p>
        <h1 id="fleet-assignment-title">Atribuição de frota</h1>
      </div>
    </div>

    <template v-if="canManage">
      <p v-if="statusMessage" class="notice success" role="status" aria-live="polite">
        {{ statusMessage }}
      </p>
      <p v-if="lookupError" class="notice error" role="alert">{{ lookupError }}</p>

      <section class="list-panel" aria-labelledby="lookup-title">
        <h2 id="lookup-title">Consultar atribuição por routeId</h2>
        <p class="field-help">Aceita qualquer UUID; uma rota sem atribuição retorna NOT_FOUND.</p>
        <form class="lookup-form" @submit.prevent="lookupAssignment">
          <label for="assignment-route-id">UUID da rota</label>
          <input
            id="assignment-route-id"
            v-model="lookupRouteId"
            type="text"
            autocomplete="off"
            required
            :aria-invalid="lookupError ? 'true' : 'false'"
          />
          <button type="submit" class="secondary-button" :disabled="lookupLoading">
            {{ lookupLoading ? 'Consultando…' : 'Consultar' }}
          </button>
        </form>
      </section>

      <section v-if="isDevMode" class="store-form-panel" aria-labelledby="demo-assignment-title">
        <h2 id="demo-assignment-title">Demonstração de atribuição (dev)</h2>
        <p class="field-help">
          O POST de demonstração existe somente com o perfil dev do backend.
        </p>
        <form @submit.prevent="createDemoAssignment">
          <label for="demo-route-id">Rota fixa do fake</label>
          <select id="demo-route-id" v-model="demoRouteId">
            <option v-for="routeId in demoRouteIds" :key="routeId" :value="routeId">
              {{ routeId }}
            </option>
          </select>

          <label for="demo-origin-id">Origem fixa</label>
          <input id="demo-origin-id" :value="demoOriginId" type="text" readonly />

          <label for="demo-capacity">Capacidade solicitada (kg)</label>
          <input
            id="demo-capacity"
            v-model="demoCapacityKg"
            type="text"
            inputmode="decimal"
            required
          />
          <span class="field-help">Valor positivo com até três casas decimais.</span>

          <p v-if="formError" class="notice error" role="alert">{{ formError }}</p>
          <div class="form-actions">
            <button type="submit" class="primary-button" :disabled="saving">
              {{ saving ? 'Atribuindo…' : 'Atribuir rota' }}
            </button>
          </div>
        </form>
      </section>

      <article v-if="assignment" class="lookup-result" aria-labelledby="assignment-result-title">
        <h2 id="assignment-result-title">Detalhes da atribuição</h2>
        <p>Rota: {{ assignment.snapshot.routeId }}</p>
        <p>Motorista: {{ assignment.driver?.name ?? assignment.snapshot.driverId }}</p>
        <p>Veículo: {{ assignment.vehicle?.identifier ?? assignment.snapshot.vehicleId }}</p>
        <p>
          Transportadora:
          {{ assignment.carrier?.name ?? (assignment.snapshot.carrierId ? assignment.snapshot.carrierId : 'Não aplicável (frota própria)') }}
        </p>
        <p>Tipo de frota: {{ assignment.snapshot.fleetType === 'OWN' ? 'Própria (OWN)' : 'Terceirizada (THIRD_PARTY)' }}</p>
      </article>
    </template>
    <p v-else class="notice error" role="alert">
      Seu perfil não tem acesso à atribuição de frota.
    </p>
  </section>
</template>
