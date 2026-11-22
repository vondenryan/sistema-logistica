package br.com.sistemalogistica.pessoa2.suppliers;

import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateSupplierRequest(
		@NotBlank @Size(max = 160) String name,
		@Email @Size(max = 254) String contactEmail,
		@NotNull UUID storeId) {
}
