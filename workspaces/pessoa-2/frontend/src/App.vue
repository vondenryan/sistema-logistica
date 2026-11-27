<template>
  <div class="app-shell">
    <header class="site-header">
      <RouterLink class="brand" to="/stores">Sistema de Logística</RouterLink>
      <nav aria-label="Navegação principal">
        <RouterLink to="/stores">Lojas</RouterLink>
        <RouterLink to="/origins">Origens</RouterLink>
        <RouterLink to="/suppliers">Fornecedores</RouterLink>
      </nav>
    </header>
    <main>
      <RouterView />
    </main>
  </div>
</template>
