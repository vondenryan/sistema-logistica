<template>
  <div class="app-shell">
    <header class="site-header">
      <RouterLink class="brand" to="/stores">Sistema de Logística</RouterLink>
      <nav aria-label="Navegação principal">
        <RouterLink to="/stores">Lojas</RouterLink>
      </nav>
    </header>
    <main>
      <RouterView />
    </main>
  </div>
</template>
