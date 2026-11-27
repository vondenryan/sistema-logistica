package br.com.sistemalogistica.pessoa2.fleet;

import java.util.List;

public record FleetPageResponse<T>(List<T> items, int page, int size, long totalItems) {
	public FleetPageResponse {
		items = List.copyOf(items);
	}
}
