<script setup lang="ts">
import { ref } from 'vue'
import DriverPanelView from './DriverPanelView.vue'
import TrackingView from './TrackingView.vue'

const activeTab = ref<'driver' | 'tracking'>('driver')
</script>

<template>
  <div class="app-shell">
    <header class="app-header">
      <h1 class="app-title">Logística · Entregas &amp; Rastreamento</h1>
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
      </nav>
    </header>

    <main>
      <DriverPanelView v-if="activeTab === 'driver'" />
      <TrackingView v-else />
    </main>
  </div>
</template>
