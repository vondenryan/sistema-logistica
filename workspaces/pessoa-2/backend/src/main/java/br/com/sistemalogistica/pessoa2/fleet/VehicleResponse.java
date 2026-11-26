package br.com.sistemalogistica.pessoa2.fleet;

import java.math.BigDecimal;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

public record VehicleResponse(
		UUID id,
		String identifier,
		@JsonSerialize(using = ToStringSerializer.class) BigDecimal capacityKg,
		FleetType fleetType,
		@JsonInclude(JsonInclude.Include.NON_NULL) UUID carrierId,
		boolean active) {
	static VehicleResponse from(Vehicle vehicle) {
		return new VehicleResponse(
				vehicle.getId(),
				vehicle.getIdentifier(),
				vehicle.getCapacityKg(),
				vehicle.getFleetType(),
				vehicle.getCarrierId(),
				vehicle.isActive());
	}
}
