<script setup lang="ts">
import { ref } from 'vue'
import DriverPanelView from './DriverPanelView.vue'
import TrackingView from './TrackingView.vue'
import DocumentsView from './DocumentsView.vue'

const activeTab = ref<'driver' | 'tracking' | 'documents'>('driver')
</script>

<template>
  <div class="app-shell">
    <header class="app-header">
      <h1 class="app-title">Logística · Entregas, Rastreamento &amp; Documentos</h1>
      <nav class="nav-tabs" aria-label="Navegação">
        <button
          :class="['tab-btn', { active: activeTab === 'driver' }]"
          @click="activeTab = 'driver'"
        >
          Painel do Motorista
        </button>
        <button
          :class="['tab-btn', { active: activeTab === 'tracking' }]"
          @click="activeTab = 'tracking'"
        >
          Rastreamento Público
        </button>
        <button
          :class="['tab-btn', { active: activeTab === 'documents' }]"
          @click="activeTab = 'documents'"
        >
          Documentos Simulados
        </button>
      </nav>
    </header>

    <main>
      <DriverPanelView v-if="activeTab === 'driver'" />
      <TrackingView v-else-if="activeTab === 'tracking'" />
      <DocumentsView v-else />
    </main>
  </div>
</template>
