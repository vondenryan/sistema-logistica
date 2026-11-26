<script setup lang="ts">
import { computed } from 'vue'
import { devActor, isDevMode } from './devActor'
import { canManagePartners } from './partners/partnerPolicy'

const canManageFleet = computed(() =>
  canManagePartners(isDevMode ? devActor.role : null),
)
</script>

<template>
  <div class="app-shell">
    <header class="site-header">
      <RouterLink class="brand" to="/stores">Sistema de Logística</RouterLink>
      <nav aria-label="Navegação principal">
        <RouterLink to="/stores">Lojas</RouterLink>
        <RouterLink to="/origins">Origens</RouterLink>
        <RouterLink to="/suppliers">Fornecedores</RouterLink>
        <template v-if="canManageFleet">
          <RouterLink to="/carriers">Transportadoras</RouterLink>
          <RouterLink to="/drivers">Motoristas</RouterLink>
          <RouterLink to="/vehicles">Veículos</RouterLink>
          <RouterLink to="/fleet/assignments">Atribuição de frota</RouterLink>
        </template>
      </nav>
    </header>
    <main>
      <RouterView />
    </main>
  </div>
</template>
