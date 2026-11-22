import { createRouter, createWebHistory } from 'vue-router'
import StoresView from './views/StoresView.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/stores' },
    { path: '/stores', name: 'stores', component: StoresView },
  ],
})
