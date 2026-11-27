<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import DriverPanelView from './DriverPanelView.vue'
import TrackingView from './TrackingView.vue'
import DocumentsView from './DocumentsView.vue'
import { clearDeliverySession, deliveryAuthenticated, setDeliverySession, signIn } from './api'

const integrated = import.meta.env.MODE === 'integration'
const email = ref('')
const password = ref('')
const loggingIn = ref(false)
const loginError = ref('')

async function login() {
  loggingIn.value = true
  loginError.value = ''
  try {
    const session = await signIn(email.value.trim(), password.value)
    setDeliverySession(session)
  } catch (error) {
    loginError.value = error instanceof Error ? error.message : 'Não foi possível entrar.'
  } finally {
    password.value = ''
    loggingIn.value = false
  }
}

type Tab = 'driver' | 'tracking' | 'documents'
function tabFromLocation(): Tab {
  if (!integrated) return 'driver'
  if (window.location.hash === '#rastreamento') return 'tracking'
  if (window.location.hash === '#documentos') return 'documents'
  return 'driver'
}
const activeTab = ref<Tab>(tabFromLocation())
const hashes = { driver: '#motorista', tracking: '#rastreamento', documents: '#documentos' }
function selectTab(tab: Tab) {
  activeTab.value = tab
  if (integrated) window.location.hash = hashes[tab]
}
function syncTab() { activeTab.value = tabFromLocation() }
onMounted(() => { if (integrated) window.addEventListener('hashchange', syncTab) })
onUnmounted(() => window.removeEventListener('hashchange', syncTab))
</script>

<template>
  <div class="app-shell">
    <header class="app-header">
      <h1 class="app-title">Logística · Entregas, Rastreamento &amp; Documentos</h1>
      <nav class="nav-tabs" aria-label="Navegação">
        <a v-if="integrated" class="tab-btn" href="/">Módulos</a>
        <button
          :class="['tab-btn', { active: activeTab === 'driver' }]"
          @click="selectTab('driver')"
        >
          Painel do Motorista
        </button>
        <button
          :class="['tab-btn', { active: activeTab === 'tracking' }]"
          @click="selectTab('tracking')"
        >
          Rastreamento Público
        </button>
        <button
          :class="['tab-btn', { active: activeTab === 'documents' }]"
          @click="selectTab('documents')"
        >
          Documentos Simulados
        </button>
        <button v-if="deliveryAuthenticated" class="tab-btn" type="button" @click="clearDeliverySession">Sair</button>
      </nav>
    </header>

    <main>
      <TrackingView v-if="activeTab === 'tracking'" />
      <form v-else-if="integrated && !deliveryAuthenticated" class="card" @submit.prevent="login">
        <h2>Entrar</h2>
        <label for="email">E-mail</label>
        <input id="email" v-model="email" class="input" type="email" autocomplete="username" required />
        <label for="password">Senha</label>
        <input id="password" v-model="password" class="input" type="password" autocomplete="current-password" required />
        <p v-if="loginError" role="alert">{{ loginError }}</p>
        <button class="btn btn-primary" type="submit" :disabled="loggingIn">Entrar</button>
      </form>
      <DriverPanelView v-else-if="activeTab === 'driver'" />
      <DocumentsView v-else />
    </main>
  </div>
</template>
