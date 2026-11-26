package br.com.sistemalogistica.pessoa2.contracts.v1;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RouteSnapshot(
		UUID routeId,
		UUID storeId,
		UUID originId,
		RouteStatus status,
		List<UUID> orderIds,
		UUID driverId,
		UUID vehicleId,
		Instant startedAt) {

	public RouteSnapshot {
		orderIds = List.copyOf(orderIds);
	}
}
