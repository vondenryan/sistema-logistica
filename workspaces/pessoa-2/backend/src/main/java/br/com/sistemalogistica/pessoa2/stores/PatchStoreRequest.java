package br.com.sistemalogistica.pessoa2.stores;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PatchStoreRequest(
		@Pattern(regexp = "(?s).*\\S.*", message = "Must not be blank.")
		@Size(max = 160) String name,
		Boolean active) {
}
