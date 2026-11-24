<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  ApiError,
  deliveriesApi,
  type DeliveryEvent,
  type DeliverySnapshot
} from './api'

const deliveries = ref<DeliverySnapshot[]>([])
const totalItems = ref(0)
const currentPage = ref(0)
const pageSize = 20
const totalPages = computed(() => Math.max(1, Math.ceil(totalItems.value / pageSize)))
const loading = ref(false)
const errorMessage = ref<string | null>(null)
const eventsByDelivery = reactive<Record<string, DeliveryEvent[]>>({})
const expandedId = ref<string | null>(null)
const reasonInputs = reactive<Record<string, string>>({})
const receiverInputs = reactive<Record<string, string>>({})

async function loadDeliveries(targetPage = currentPage.value) {
  if (loading.value || targetPage < 0) return
  loading.value = true
  errorMessage.value = null
  try {
    const page = await deliveriesApi.listMyDeliveries(targetPage, pageSize)
    deliveries.value = page.items
    totalItems.value = page.totalItems
    currentPage.value = page.page
    expandedId.value = null
  } catch (err) {
    errorMessage.value =
      err instanceof ApiError ? err.message : 'Erro ao carregar entregas do motorista.'
  } finally {
    loading.value = false
  }
}

async function toggleHistory(deliveryId: string) {
  if (expandedId.value === deliveryId) {
    expandedId.value = null
    return
  }
  expandedId.value = deliveryId
  try {
    eventsByDelivery[deliveryId] = await deliveriesApi.getEvents(deliveryId)
  } catch (err) {
    errorMessage.value =
      err instanceof ApiError ? err.message : 'Erro ao carregar histórico.'
  }
}

async function transition(
  deliveryId: string,
  status: 'IN_TRANSIT' | 'FAILED_ATTEMPT' | 'ASSIGNED'
) {
  errorMessage.value = null
  const reason = reasonInputs[deliveryId]

  if (status === 'FAILED_ATTEMPT' && (!reason || !reason.trim())) {
    errorMessage.value = 'Informe o motivo da tentativa sem sucesso.'
    return
  }

  try {
    const updated = await deliveriesApi.updateStatus(
      deliveryId,
      status,
      status === 'FAILED_ATTEMPT' ? reason : undefined
    )
    replaceDelivery(updated)
    if (status === 'FAILED_ATTEMPT') {
      reasonInputs[deliveryId] = ''
    }
    if (expandedId.value === deliveryId) {
      eventsByDelivery[deliveryId] = await deliveriesApi.getEvents(deliveryId)
    }
  } catch (err) {
    errorMessage.value = err instanceof ApiError ? err.message : 'Falha ao atualizar status.'
  }
}

async function complete(deliveryId: string) {
  errorMessage.value = null
  const receiverName = receiverInputs[deliveryId]
  if (!receiverName || !receiverName.trim()) {
    errorMessage.value = 'Informe o nome do recebedor para concluir a entrega.'
    return
  }

  try {
    const updated = await deliveriesApi.completeDelivery(deliveryId, receiverName)
    replaceDelivery(updated)
    receiverInputs[deliveryId] = ''
    if (expandedId.value === deliveryId) {
      eventsByDelivery[deliveryId] = await deliveriesApi.getEvents(deliveryId)
    }
  } catch (err) {
    errorMessage.value = err instanceof ApiError ? err.message : 'Falha ao concluir entrega.'
  }
}

function replaceDelivery(updated: DeliverySnapshot) {
  const idx = deliveries.value.findIndex((d) => d.deliveryId === updated.deliveryId)
  if (idx >= 0) {
    deliveries.value[idx] = updated
  }
}

onMounted(() => loadDeliveries())
</script>

<template>
  <section aria-label="Painel do Motorista">
    <div class="card">
      <div style="display: flex; justify-content: space-between; align-items: center">
        <h2 style="margin: 0">Minhas Entregas ({{ totalItems }})</h2>
        <button class="btn btn-secondary" :disabled="loading" @click="loadDeliveries()">
          {{ loading ? 'Atualizando...' : 'Atualizar' }}
        </button>
      </div>
    </div>

    <nav aria-label="Paginação das entregas" class="actions-row">
      <button class="btn btn-secondary" :disabled="loading || currentPage === 0" @click="loadDeliveries(currentPage - 1)">
        Anterior
      </button>
      <span aria-live="polite">Página {{ currentPage + 1 }} de {{ totalPages }}</span>
      <button class="btn btn-secondary" :disabled="loading || currentPage + 1 >= totalPages" @click="loadDeliveries(currentPage + 1)">
        Próxima
      </button>
    </nav>

    <div v-if="errorMessage" class="alert-error" role="alert">
      {{ errorMessage }}
    </div>

    <div v-if="!loading && deliveries.length === 0" class="card">
      Nenhuma entrega atribuída no momento.
    </div>

    <article
      v-for="item in deliveries"
      :key="item.deliveryId"
      class="card"
      :data-delivery-id="item.deliveryId"
    >
      <div style="display: flex; justify-content: space-between; align-items: center; gap: 0.5rem">
        <div>
          <strong>Entrega {{ item.deliveryId.slice(0, 8) }}</strong>
          <div style="font-size: 0.85rem; color: #64748b">
            Pedido: {{ item.orderId.slice(0, 8) }} · Atualizado em
            {{ new Date(item.updatedAt).toLocaleString('pt-BR') }}
          </div>
        </div>
        <span :class="['status-badge', `status-${item.status}`]">
          {{ item.status }}
        </span>
      </div>

      <div v-if="item.receiverName" style="margin-top: 0.5rem; font-size: 0.9rem">
        Recebido por: <strong>{{ item.receiverName }}</strong>
      </div>

      <div class="actions-row">
        <button
          v-if="item.status === 'ASSIGNED'"
          class="btn btn-primary"
          @click="transition(item.deliveryId, 'IN_TRANSIT')"
        >
          Iniciar Transporte
        </button>

        <button
          v-if="item.status === 'FAILED_ATTEMPT'"
          class="btn btn-primary"
          @click="transition(item.deliveryId, 'ASSIGNED')"
        >
          Reatribuir para Nova Tentativa
        </button>

        <button class="btn btn-secondary" @click="toggleHistory(item.deliveryId)">
          {{ expandedId === item.deliveryId ? 'Ocultar Histórico' : 'Ver Histórico' }}
        </button>
      </div>

      <div v-if="item.status === 'IN_TRANSIT'" style="margin-top: 1rem">
        <div class="actions-row">
          <input
            v-model="receiverInputs[item.deliveryId]"
            type="text"
            class="input"
            placeholder="Nome completo do recebedor"
            aria-label="Nome do recebedor"
          />
          <button class="btn btn-success" @click="complete(item.deliveryId)">
            Concluir Entrega
          </button>
        </div>

        <div class="actions-row">
          <input
            v-model="reasonInputs[item.deliveryId]"
            type="text"
            class="input"
            placeholder="Motivo da tentativa sem sucesso"
            aria-label="Motivo da falha"
          />
          <button class="btn btn-warning" @click="transition(item.deliveryId, 'FAILED_ATTEMPT')">
            Registrar Falha
          </button>
        </div>
      </div>

      <ul v-if="expandedId === item.deliveryId && eventsByDelivery[item.deliveryId]" class="milestone-list">
        <li
          v-for="ev in eventsByDelivery[item.deliveryId]"
          :key="ev.id"
          class="milestone-item"
        >
          <div>
            <strong>{{ ev.toStatus }}</strong>
            <span v-if="ev.reason"> — {{ ev.reason }}</span>
            <span v-if="ev.receiverName"> — Recebedor: {{ ev.receiverName }}</span>
          </div>
          <small>{{ new Date(ev.occurredAt).toLocaleString('pt-BR') }}</small>
        </li>
      </ul>
    </article>
  </section>
</template>
