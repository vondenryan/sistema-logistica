package br.com.sistemalogistica.pessoa2.origins;

import java.util.UUID;

public record OriginSnapshot(
		UUID id,
		OriginType type,
		UUID storeId,
		AddressSnapshot address,
		boolean active) {
	static OriginSnapshot from(Origin origin) {
		return new OriginSnapshot(
				origin.getId(),
				origin.getType(),
				origin.getStoreId(),
				origin.getAddress(),
				origin.isActive());
	}
}
