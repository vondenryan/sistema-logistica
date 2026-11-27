import { createRouter, createWebHistory } from 'vue-router'
import OriginsView from './views/OriginsView.vue'
import StoresView from './views/StoresView.vue'
import SuppliersView from './views/SuppliersView.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/stores' },
    { path: '/stores', name: 'stores', component: StoresView },
    { path: '/origins', name: 'origins', component: OriginsView },
    { path: '/suppliers', name: 'suppliers', component: SuppliersView },
  ],
})
