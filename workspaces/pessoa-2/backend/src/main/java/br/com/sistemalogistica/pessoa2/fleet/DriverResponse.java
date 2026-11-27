package br.com.sistemalogistica.pessoa2.fleet;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

public record DriverResponse(
		UUID id,
		String name,
		@JsonInclude(JsonInclude.Include.NON_NULL) UUID carrierId,
		boolean active) {
	static DriverResponse from(Driver driver) {
		return new DriverResponse(driver.getId(), driver.getName(), driver.getCarrierId(), driver.isActive());
	}
}
