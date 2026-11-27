package br.com.sistemalogistica.pessoa2.contracts.v1;

import java.math.BigDecimal;
import java.util.UUID;

public record AssignmentRequest(UUID routeId, UUID originId, BigDecimal requiredCapacityKg) {
}
