package br.com.sistemalogistica.pessoa2.stores;

import java.util.List;

public record StorePageResponse(List<StoreResponse> items, int page, int size, long totalItems) {
}
