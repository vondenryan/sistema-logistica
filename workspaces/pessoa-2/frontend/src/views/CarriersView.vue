<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api, type Carrier } from '../api'
import { currentRole } from '../devActor'
import { canManagePartners } from '../partners/partnerPolicy'
import { formatFleetError } from '../fleet/fleetError'
import { validateCarrierName } from '../fleet/fleetValidation'

const page = ref(0)
const pageSize = ref(20)
const totalItems = ref(0)
const carriers = ref<Carrier[]>([])
const loading = ref(false)
const saving = ref(false)
const errorMessage = ref('')
const statusMessage = ref('')
const formOpen = ref(false)
const formError = ref('')
const formName = ref('')
const editingCarrier = ref<Carrier | null>(null)

const role = computed(() => (currentRole.value))
const canManage = computed(() => canManagePartners(role.value))
const pageCount = computed(() => Math.max(1, Math.ceil(totalItems.value / pageSize.value)))

onMounted(() => {
  if (canManage.value) {
    void loadCarriers()
  }
})

async function loadCarriers() {
  if (!canManage.value) {
    return
  }
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await api.listCarriers(page.value, pageSize.value)
    carriers.value = result.items
    totalItems.value = result.totalItems
  } catch (error) {
    carriers.value = []
    totalItems.value = 0
    errorMessage.value = formatFleetError(error)
  } finally {
    loading.value = false
  }
}

function changePage(nextPage: number) {
  if (nextPage < 0 || nextPage >= pageCount.value) {
    return
  }
  page.value = nextPage
  void loadCarriers()
}

function changePageSize(event: Event) {
  const target = event.target
  if (!(target instanceof HTMLSelectElement)) {
    return
  }
  pageSize.value = Number(target.value)
  page.value = 0
  void loadCarriers()
}

function openCreateForm() {
  editingCarrier.value = null
  formName.value = ''
  formError.value = ''
  formOpen.value = true
}

function openEditForm(carrier: Carrier) {
  editingCarrier.value = carrier
  formName.value = carrier.name
  formError.value = ''
  formOpen.value = true
}

function closeForm() {
  formOpen.value = false
  formError.value = ''
}

async function saveCarrier() {
  if (!canManage.value) {
    return
  }
  const validationMessage = validateCarrierName(formName.value)
  if (validationMessage !== null) {
    formError.value = validationMessage
    return
  }

  saving.value = true
  formError.value = ''
  statusMessage.value = ''
  try {
    if (editingCarrier.value) {
      await api.patchCarrier(editingCarrier.value.id, { name: formName.value })
      statusMessage.value = 'Transportadora atualizada.'
    } else {
      await api.createCarrier({ name: formName.value, active: true })
      statusMessage.value = 'Transportadora criada.'
    }
    closeForm()
    await loadCarriers()
  } catch (error) {
    formError.value = formatFleetError(
      error,
      'Já existe uma transportadora com esse nome.',
    )
  } finally {
    saving.value = false
  }
}

async function inactivateCarrier(carrier: Carrier) {
  if (!canManage.value || !carrier.active) {
    return
  }
  if (!window.confirm(`Inativar a transportadora "${carrier.name}"?`)) {
    return
  }

  statusMessage.value = ''
  errorMessage.value = ''
  try {
    await api.patchCarrier(carrier.id, { active: false })
    statusMessage.value = 'Transportadora inativada.'
    await loadCarriers()
  } catch (error) {
    errorMessage.value = formatFleetError(error)
  }
}
</script>

<template>
  <section class="stores-page" aria-labelledby="carriers-title">
    <div class="page-heading">
      <div>
        <p class="eyebrow">Cadastros</p>
        <h1 id="carriers-title">Transportadoras</h1>
      </div>
      <button v-if="canManage" type="button" class="primary-button" @click="openCreateForm">
        Cadastrar transportadora
      </button>
    </div>

    <p v-if="statusMessage" class="notice success" role="status" aria-live="polite">
      {{ statusMessage }}
    </p>
    <p v-if="errorMessage" class="notice error" role="alert">{{ errorMessage }}</p>

    <section v-if="formOpen && canManage" class="store-form-panel" aria-labelledby="form-title">
      <h2 id="form-title">{{ editingCarrier ? 'Editar transportadora' : 'Cadastrar transportadora' }}</h2>
      <form @submit.prevent="saveCarrier">
        <label for="carrier-name">Nome da transportadora</label>
        <input
          id="carrier-name"
          v-model="formName"
          type="text"
          maxlength="160"
          required
          :aria-invalid="formError ? 'true' : 'false'"
        />
        <span class="field-help">De 1 a 160 caracteres.</span>
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
          <h2 id="list-title">Transportadoras cadastradas</h2>
          <p class="field-help">{{ totalItems }} transportadora(s)</p>
        </div>
        <div class="list-controls">
          <label for="page-size">Itens por página</label>
          <select id="page-size" :value="pageSize" :disabled="loading" @change="changePageSize">
            <option :value="10">10</option>
            <option :value="20">20</option>
            <option :value="50">50</option>
            <option :value="100">100</option>
          </select>
          <button type="button" class="secondary-button" :disabled="loading" @click="loadCarriers">
            Atualizar
          </button>
        </div>
      </div>

      <p v-if="loading" class="empty-state" role="status">Carregando transportadoras…</p>
      <div v-else-if="carriers.length > 0" class="table-scroll">
        <table>
          <caption class="sr-only">Transportadoras e situação de atividade</caption>
          <thead>
            <tr>
              <th scope="col">Nome</th>
              <th scope="col">Situação</th>
              <th scope="col">Ações</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="carrier in carriers" :key="carrier.id">
              <td>{{ carrier.name }}</td>
              <td>{{ carrier.active ? 'Ativa' : 'Inativa' }}</td>
              <td>
                <div class="row-actions">
                  <button type="button" class="text-button" @click="openEditForm(carrier)">
                    Editar
                  </button>
                  <button
                    v-if="carrier.active"
                    type="button"
                    class="text-button danger-text"
                    @click="inactivateCarrier(carrier)"
                  >
                    Inativar
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <p v-else-if="!errorMessage" class="empty-state">Nenhuma transportadora encontrada.</p>

      <div class="pagination" aria-label="Paginação das transportadoras">
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
      Seu perfil não tem acesso ao cadastro de transportadoras.
    </p>
  </section>
</template>
