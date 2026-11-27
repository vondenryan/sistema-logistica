package br.com.sistemalogistica.pessoa2.origins;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record CreateOriginRequest(
		@NotNull OriginType type,
		@NotNull UUID storeId,
		@NotNull @Valid AddressSnapshot address,
		@NotNull Boolean active) {
}
