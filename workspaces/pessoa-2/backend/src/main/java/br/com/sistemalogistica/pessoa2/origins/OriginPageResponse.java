package br.com.sistemalogistica.pessoa2.origins;

import java.util.List;

public record OriginPageResponse(List<OriginSnapshot> items, int page, int size, long totalItems) {
}
