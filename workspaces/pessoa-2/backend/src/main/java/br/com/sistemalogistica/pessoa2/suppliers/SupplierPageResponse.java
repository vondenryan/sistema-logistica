package br.com.sistemalogistica.pessoa2.suppliers;

import java.util.List;

public record SupplierPageResponse(List<SupplierResponse> items, int page, int size, long totalItems) {
}
