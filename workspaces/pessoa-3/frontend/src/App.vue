<script setup lang="ts">
import { ref } from 'vue'
import DriverPanelView from './DriverPanelView.vue'
import TrackingView from './TrackingView.vue'
import DocumentsView from './DocumentsView.vue'
import { setDeliverySession, signIn } from './api'

const integrated = import.meta.env.MODE === 'integration' || import.meta.env.PROD
const authenticated = ref(false)
const email = ref('')
const password = ref('')
const loggingIn = ref(false)
const loginError = ref('')

async function login() {
  loggingIn.value = true
  loginError.value = ''
  try {
    const session = await signIn(email.value.trim(), password.value)
    setDeliverySession(session.accessToken)
    authenticated.value = true
  } catch (error) {
    loginError.value = error instanceof Error ? error.message : 'Não foi possível entrar.'
  } finally {
    password.value = ''
    loggingIn.value = false
  }
}

function logout() {
  setDeliverySession('')
  authenticated.value = false
}

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
      <button v-if="authenticated" class="tab-btn" type="button" @click="logout">Sair</button>
      </nav>
    </header>

    <main>
      <TrackingView v-if="activeTab === 'tracking'" />
      <form v-else-if="integrated && !authenticated" class="card" @submit.prevent="login">
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
