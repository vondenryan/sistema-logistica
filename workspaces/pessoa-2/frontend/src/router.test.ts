import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { devActor } from './devActor'
import { router } from './router'

const fleetPaths = [
  ['/carriers', 'carriers'],
  ['/drivers', 'drivers'],
  ['/vehicles', 'vehicles'],
  ['/fleet/assignments', 'fleet-assignments'],
] as const

describe('autorização das rotas de frota', () => {
  beforeEach(async () => {
    devActor.role = 'ADMIN'
    await router.push('/stores')
  })

  afterEach(async () => {
    devActor.role = 'ADMIN'
    await router.push('/stores')
  })

  it('permite acesso direto às rotas de frota para OPERATOR', async () => {
    devActor.role = 'OPERATOR'
    for (const [path, name] of fleetPaths) {
      await router.push(path)
      expect(router.currentRoute.value.name).toBe(name)
    }
  })

  it('redireciona STORE para fora de cada rota de frota', async () => {
    devActor.role = 'STORE'
    for (const [path] of fleetPaths) {
      await router.push(path)
      expect(router.currentRoute.value.name).toBe('stores')
    }
  })
})
