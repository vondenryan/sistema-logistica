import { describe, expect, it } from 'vitest'
import {
  toVehicleDetails,
  validateCarrierName,
  validateDriverForm,
  validateVehicleForm,
  type VehicleForm,
} from './fleetValidation'

const carrierId = '77777777-7777-4777-8777-777777777777'
const vehicleForm: VehicleForm = {
  identifier: 'VEI-001',
  capacityKg: '1000.000',
  fleetType: 'OWN',
  carrierId: '',
}

describe('validação dos cadastros de frota', () => {
  it('valida nomes de transportadoras e motoristas', () => {
    expect(validateCarrierName(` ${'A'.repeat(160)} `)).toBeNull()
    expect(validateCarrierName('  ')).toBe('Informe o nome da transportadora.')
    expect(validateCarrierName('A'.repeat(161))).toBe(
      'O nome da transportadora deve ter no máximo 160 caracteres.',
    )

    expect(validateDriverForm('Motorista', '')).toBeNull()
    expect(validateDriverForm('Motorista', carrierId)).toBeNull()
    expect(validateDriverForm('Motorista', 'invalid')).toBe(
      'Selecione uma transportadora válida.',
    )
  })

  it('valida identificador, capacidade decimal, frota e transportadora do veículo', () => {
    expect(validateVehicleForm(vehicleForm)).toBeNull()
    expect(validateVehicleForm({
      ...vehicleForm,
      fleetType: 'THIRD_PARTY',
      carrierId,
    })).toBeNull()
    expect(validateVehicleForm({ ...vehicleForm, identifier: ' ' })).toBe(
      'Informe o identificador do veículo.',
    )
    expect(validateVehicleForm({ ...vehicleForm, identifier: 'V'.repeat(41) })).toBe(
      'O identificador do veículo deve ter no máximo 40 caracteres.',
    )
    for (const capacityKg of ['0.000', '-1.000', '1', '1.0000', '1000000000.000']) {
      expect(validateVehicleForm({ ...vehicleForm, capacityKg })).not.toBeNull()
    }
    expect(validateVehicleForm({ ...vehicleForm, fleetType: 'THIRD_PARTY' })).toBe(
      'Selecione uma transportadora para a frota terceirizada.',
    )
  })

  it('omite carrierId para OWN e inclui o vínculo em THIRD_PARTY', () => {
    expect(toVehicleDetails(vehicleForm)).toEqual({
      identifier: 'VEI-001',
      capacityKg: '1000.000',
      fleetType: 'OWN',
    })
    expect(toVehicleDetails({
      ...vehicleForm,
      fleetType: 'THIRD_PARTY',
      carrierId,
    })).toEqual({
      identifier: 'VEI-001',
      capacityKg: '1000.000',
      fleetType: 'THIRD_PARTY',
      carrierId,
    })
  })
})
