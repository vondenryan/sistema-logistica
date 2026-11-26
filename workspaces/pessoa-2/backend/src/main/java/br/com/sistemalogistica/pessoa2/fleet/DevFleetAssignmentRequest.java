package br.com.sistemalogistica.pessoa2.fleet;

import java.util.UUID;

public record DevFleetAssignmentRequest(UUID routeId, UUID originId, String requiredCapacityKg) {
}
