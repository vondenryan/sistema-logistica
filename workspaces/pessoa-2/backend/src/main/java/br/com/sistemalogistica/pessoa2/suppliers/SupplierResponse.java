package br.com.sistemalogistica.pessoa2.suppliers;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

public record SupplierResponse(
		UUID id,
		String name,
		@JsonInclude(JsonInclude.Include.NON_NULL) String contactEmail,
		UUID storeId) {
	static SupplierResponse from(Supplier supplier) {
		return new SupplierResponse(
				supplier.getId(),
				supplier.getName(),
				supplier.getContactEmail(),
				supplier.getStoreId());
	}
}
