package br.com.sistemalogistica.pessoa2.contracts.v1;

import java.util.UUID;

public record ReleaseReceipt(UUID routeId, boolean released) {
}
