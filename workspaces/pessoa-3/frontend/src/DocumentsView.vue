<script setup lang="ts">
import { ref } from 'vue'
import {
  ApiError,
  deliveriesApi,
  type DocumentReceipt,
  type DocumentType
} from './api'

const orderId = ref('')
const documentType = ref<DocumentType>('CTE_SIMULATED')
const loading = ref(false)
const errorMessage = ref<string | null>(null)
const receipt = ref<DocumentReceipt | null>(null)

async function generate() {
  errorMessage.value = null
  receipt.value = null

  if (!orderId.value.trim()) {
    errorMessage.value = 'Informe o ID do pedido (UUID).'
    return
  }

  loading.value = true
  try {
    receipt.value = await deliveriesApi.generateDocument(orderId.value, documentType.value)
  } catch (err) {
    if (err instanceof ApiError) {
      if (err.status === 422) {
        errorMessage.value = 'Frete ainda não cotado para este pedido.'
      } else if (err.status === 404) {
        errorMessage.value = 'Pedido não encontrado.'
      } else {
        errorMessage.value = err.message
      }
    } else {
      errorMessage.value = 'Erro ao gerar documento simulado.'
    }
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="card" aria-label="Documentos Fiscais Simulados">
    <div
      style="
        background: #fef2f2;
        border: 2px solid #dc2626;
        color: #991b1b;
        padding: 0.75rem 1rem;
        border-radius: 0.5rem;
        font-weight: 700;
        text-align: center;
        margin-bottom: 1rem;
      "
    >
      SIMULAÇÃO ACADÊMICA — SEM VALIDADE FISCAL
    </div>

    <h2>Documentos Simulados (NF-e / CT-e)</h2>
    <p>Emissão fictícia para demonstração acadêmica. Sem transmissão à SEFAZ.</p>

    <form class="actions-row" @submit.prevent="generate">
      <input
        v-model="orderId"
        type="text"
        class="input"
        placeholder="UUID do Pedido (ex: 44444444-4444-4444-8444-444444444444)"
        aria-label="ID do Pedido"
      />
      <select v-model="documentType" class="input" aria-label="Tipo de Documento">
        <option value="CTE_SIMULATED">CT-e Simulado</option>
        <option value="NFE_SIMULATED">NF-e Simulada</option>
      </select>
      <button type="submit" class="btn btn-primary" :disabled="loading">
        {{ loading ? 'Gerando...' : 'Gerar Documento' }}
      </button>
    </form>

    <div v-if="errorMessage" class="alert-error" role="alert" style="margin-top: 1rem">
      {{ errorMessage }}
    </div>

    <div v-if="receipt" class="card" style="margin-top: 1rem; background: #f8fafc">
      <div><strong>Documento ID:</strong> {{ receipt.documentId }}</div>
      <div><strong>Pedido:</strong> {{ receipt.orderId }}</div>
      <div><strong>Tipo:</strong> {{ receipt.documentType }}</div>
      <div><strong>Status:</strong> {{ receipt.status }}</div>
      <div class="actions-row">
        <a
          :href="deliveriesApi.documentPdfUrl(receipt.documentId)"
          target="_blank"
          rel="noopener noreferrer"
          class="btn btn-success"
          style="text-decoration: none"
        >
          Visualizar / Baixar PDF
        </a>
      </div>
    </div>
  </section>
</template>
