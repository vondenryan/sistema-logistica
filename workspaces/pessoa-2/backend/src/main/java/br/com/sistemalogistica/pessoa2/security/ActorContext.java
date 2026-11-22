package br.com.sistemalogistica.pessoa2.security;

import java.util.UUID;

public record ActorContext(UUID actorId, Role role, UUID storeId, UUID driverId) {
}
