<script setup lang="ts">
import { computed, ref } from 'vue'
import { currentRole, isDevMode, session, clearSession } from './devActor'
import { api } from './api'
import { canManagePartners } from './partners/partnerPolicy'

const canManageFleet = computed(() =>
  canManagePartners(currentRole.value),
)

const email = ref('')
const password = ref('')
const signingIn = ref(false)
const loginError = ref('')

async function signIn() {
  signingIn.value = true
  loginError.value = ''
  try {
    const result = await api.signIn(email.value.trim(), password.value)
    session.accessToken = result.accessToken
    session.actor = result.actor
  } catch (error) {
    loginError.value = error instanceof Error ? error.message : 'Não foi possível entrar.'
  } finally {
    password.value = ''
    signingIn.value = false
  }
}
</script>

<template>
  <div class="app-shell">
    <header class="site-header">
      <RouterLink class="brand" to="/stores">Sistema de Logística</RouterLink>
      <nav v-if="isDevMode || session.actor" aria-label="Navegação principal">
        <RouterLink to="/stores">Lojas</RouterLink>
        <RouterLink to="/origins">Origens</RouterLink>
        <RouterLink to="/suppliers">Fornecedores</RouterLink>
        <template v-if="canManageFleet">
          <RouterLink to="/carriers">Transportadoras</RouterLink>
          <RouterLink to="/drivers">Motoristas</RouterLink>
          <RouterLink to="/vehicles">Veículos</RouterLink>
          <RouterLink to="/fleet/assignments">Atribuição de frota</RouterLink>
        </template>

        <button v-if="session.actor" type="button" @click="clearSession">Sair</button>
      </nav>
    </header>
    <main>
      <RouterView v-if="isDevMode || session.actor" />
      <section v-else class="stores-page">
        <h1>Entrar</h1>
        <form class="store-form-panel" @submit.prevent="signIn">
          <label for="login-email">E-mail</label>
          <input id="login-email" v-model="email" type="email" autocomplete="username" required />
          <label for="login-password">Senha</label>
          <input id="login-password" v-model="password" type="password" autocomplete="current-password" required />
          <p v-if="loginError" role="alert">{{ loginError }}</p>
          <button type="submit" :disabled="signingIn">Entrar</button>
        </form>
      </section>
    </main>
  </div>
</template>
