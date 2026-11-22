package br.com.sistemalogistica.pessoa2.stores;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateStoreRequest(
		@NotBlank @Size(max = 160) String name,
		@NotNull Boolean active) {
}
