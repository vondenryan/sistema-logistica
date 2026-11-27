package br.com.sistemalogistica.pessoa2.fleet;

import java.math.BigDecimal;
import java.util.UUID;

import br.com.sistemalogistica.pessoa2.contracts.v1.FleetType;

record CarrierCreateRequest(String name, boolean active) {
}

record CarrierPatchRequest(String name, Boolean active) {
}

record DriverCreateRequest(String name, UUID carrierId, boolean active) {
}

record DriverPatchRequest(String name, UUID carrierId, boolean carrierIdProvided, Boolean active) {
}

record VehicleCreateRequest(
		String identifier,
		BigDecimal capacityKg,
		FleetType fleetType,
		UUID carrierId,
		boolean active) {
}

record VehiclePatchRequest(
		String identifier,
		BigDecimal capacityKg,
		FleetType fleetType,
		UUID carrierId,
		boolean carrierIdProvided,
		Boolean active) {
}
