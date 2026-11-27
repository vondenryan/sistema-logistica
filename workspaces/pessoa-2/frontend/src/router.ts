import { createRouter, createWebHistory } from 'vue-router'
import CarriersView from './views/CarriersView.vue'
import DriversView from './views/DriversView.vue'
import FleetAssignmentView from './views/FleetAssignmentView.vue'
import OriginsView from './views/OriginsView.vue'
import StoresView from './views/StoresView.vue'
import SuppliersView from './views/SuppliersView.vue'
import VehiclesView from './views/VehiclesView.vue'
import { currentRole } from './devActor'
import { canManagePartners } from './partners/partnerPolicy'

declare module 'vue-router' {
  interface RouteMeta {
    requiresFleetAccess?: boolean
  }
}

export const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', redirect: '/stores' },
    { path: '/stores', name: 'stores', component: StoresView },
    { path: '/origins', name: 'origins', component: OriginsView },
    { path: '/suppliers', name: 'suppliers', component: SuppliersView },
    {
      path: '/carriers',
      name: 'carriers',
      component: CarriersView,
      meta: { requiresFleetAccess: true },
    },
    {
      path: '/drivers',
      name: 'drivers',
      component: DriversView,
      meta: { requiresFleetAccess: true },
    },
    {
      path: '/vehicles',
      name: 'vehicles',
      component: VehiclesView,
      meta: { requiresFleetAccess: true },
    },
    {
      path: '/fleet/assignments',
      name: 'fleet-assignments',
      component: FleetAssignmentView,
      meta: { requiresFleetAccess: true },
    },
  ],
})

router.beforeEach((to) => {
  if (
    to.meta.requiresFleetAccess &&
    !canManagePartners(currentRole.value)
  ) {
    return { name: 'stores' }
  }
})
