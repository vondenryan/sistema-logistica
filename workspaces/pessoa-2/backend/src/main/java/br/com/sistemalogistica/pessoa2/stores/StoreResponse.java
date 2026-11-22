package br.com.sistemalogistica.pessoa2.stores;

import java.util.UUID;

public record StoreResponse(UUID id, String name, boolean active) {
	static StoreResponse from(Store store) {
		return new StoreResponse(store.getId(), store.getName(), store.isActive());
	}
}
