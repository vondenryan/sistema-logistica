package br.com.sistemalogistica.pessoa2.origins;

import java.util.UUID;

import jakarta.validation.Valid;

public record PatchOriginRequest(
		OriginType type,
		UUID storeId,
		@Valid AddressSnapshot address,
		Boolean active) {
}
