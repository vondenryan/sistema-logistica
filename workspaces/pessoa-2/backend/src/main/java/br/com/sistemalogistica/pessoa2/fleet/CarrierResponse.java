package br.com.sistemalogistica.pessoa2.fleet;

import java.util.UUID;

public record CarrierResponse(UUID id, String name, boolean active) {
	static CarrierResponse from(Carrier carrier) {
		return new CarrierResponse(carrier.getId(), carrier.getName(), carrier.isActive());
	}
}
