<script setup lang="ts">
import { ref } from 'vue'
import { ApiError, deliveriesApi, type DeliveryStatus, type PublicTrackingView } from './api'

const trackingCode = ref('')
const loading = ref(false)
const errorMessage = ref<string | null>(null)
const result = ref<PublicTrackingView | null>(null)

const STATUS_LABELS: Record<DeliveryStatus, string> = {
  PENDING: 'Aguardando rota',
  ASSIGNED: 'Motorista atribuído',
  IN_TRANSIT: 'Em trânsito',
  FAILED_ATTEMPT: 'Tentativa sem sucesso',
  DELIVERED: 'Entregue',
  CANCELLED: 'Cancelada'
}

function statusLabel(status: DeliveryStatus): string {
  return STATUS_LABELS[status] ?? status
}

async function search() {
  const code = trackingCode.value.trim()
  errorMessage.value = null
  result.value = null

  if (!code) {
    errorMessage.value = 'Informe o código de rastreamento.'
    return
  }

  loading.value = true
  try {
    result.value = await deliveriesApi.trackByCode(code)
  } catch (err) {
    if (err instanceof ApiError) {
      if (err.status === 404) {
        errorMessage.value = 'Código de rastreamento não encontrado.'
      } else if (err.status === 429) {
        errorMessage.value = 'Muitas consultas seguidas. Aguarde alguns instantes e tente novamente.'
      } else {
        errorMessage.value = err.message
      }
    } else {
      errorMessage.value = 'Não foi possível consultar o rastreamento.'
    }
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="card" aria-label="Rastreamento Público">
    <h2>Rastreamento Público</h2>
    <p>Consulte o andamento da entrega usando seu código de rastreamento.</p>

    <form class="actions-row" @submit.prevent="search">
      <input
        v-model="trackingCode"
        type="text"
        class="input"
        placeholder="Ex: TRK-7M4Q9X2K8P"
        aria-label="Código de rastreamento"
      />
      <button type="submit" class="btn btn-primary" :disabled="loading">
        {{ loading ? 'Buscando...' : 'Rastrear' }}
      </button>
    </form>

    <div v-if="errorMessage" class="alert-error" role="alert" style="margin-top: 1rem">
      {{ errorMessage }}
    </div>

    <div v-if="result" style="margin-top: 1.25rem">
      <div>
        <strong>Status atual: </strong>
        <span :class="['status-badge', `status-${result.status}`]">
          {{ statusLabel(result.status) }}
        </span>
      </div>

      <ul class="milestone-list">
        <li
          v-for="(milestone, idx) in result.milestones"
          :key="`${milestone.status}-${milestone.at}-${idx}`"
          class="milestone-item"
        >
          <span>{{ statusLabel(milestone.status) }}</span>
          <small>{{ new Date(milestone.at).toLocaleString('pt-BR') }}</small>
        </li>
      </ul>
    </div>
  </section>
</template>
