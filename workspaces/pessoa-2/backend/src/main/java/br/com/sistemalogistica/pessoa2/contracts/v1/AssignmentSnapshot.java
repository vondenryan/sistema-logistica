package br.com.sistemalogistica.pessoa2.contracts.v1;

import java.util.UUID;

public record AssignmentSnapshot(
		UUID assignmentId,
		UUID routeId,
		UUID driverId,
		UUID vehicleId,
		UUID carrierId,
		FleetType fleetType) {
}
