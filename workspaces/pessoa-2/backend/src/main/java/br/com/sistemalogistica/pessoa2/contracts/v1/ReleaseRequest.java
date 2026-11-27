package br.com.sistemalogistica.pessoa2.contracts.v1;

import java.util.UUID;

public record ReleaseRequest(UUID routeId, UUID eventId, ReleaseReason reason) {
}
