package br.com.sistemalogistica.pessoa2.fleet;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import br.com.sistemalogistica.pessoa2.security.ActorContext;
import br.com.sistemalogistica.pessoa2.security.CurrentActorPort;
import br.com.sistemalogistica.pessoa2.security.Role;
import br.com.sistemalogistica.pessoa2.shared.contract.ContractException;

final class FleetSupport {

	private FleetSupport() {
	}

	static void requireAdministrativeActor(CurrentActorPort currentActorPort) {
		ActorContext actor = currentActorPort.requireActor();
		if (actor.role() != Role.ADMIN && actor.role() != Role.OPERATOR) {
			throw new ContractException("FORBIDDEN", "The actor is not allowed to perform this operation.");
		}
	}

	static PageRequest pageRequest(int page, int size) {
		if (page < 0) {
			throw ContractException.validation("page", "Must be zero or greater.");
		}
		if (size < 1 || size > 100) {
			throw ContractException.validation("size", "Must be between 1 and 100.");
		}
		return PageRequest.of(page, size);
	}

	static <T> FleetPageResponse<T> page(Page<T> page, int requestedPage, int size) {
		return new FleetPageResponse<>(page.getContent(), requestedPage, size, page.getTotalElements());
	}

	static String normalizeName(String name) {
		if (name == null || name.isBlank()) {
			throw ContractException.validation("name", "Must not be blank.");
		}
		String normalized = name.trim();
		if (normalized.length() > 160) {
			throw ContractException.validation("name", "Must be at most 160 characters.");
		}
		return normalized;
	}

	static String normalizeIdentifier(String identifier) {
		if (identifier == null || identifier.isBlank()) {
			throw ContractException.validation("identifier", "Must not be blank.");
		}
		String normalized = identifier.trim();
		if (normalized.length() > 40) {
			throw ContractException.validation("identifier", "Must be at most 40 characters.");
		}
		return normalized;
	}

	static void requireCarrier(CarrierRepository repository, UUID carrierId) {
		if (!repository.existsById(carrierId)) {
			throw new ContractException("NOT_FOUND", "Carrier was not found.");
		}
	}

	static boolean isConstraintViolation(Throwable exception, String constraintName) {
		for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
			if (cause instanceof ConstraintViolationException violation
					&& constraintName.equals(violation.getConstraintName())) {
				return true;
			}
		}
		return false;
	}

	static ContractException conflict(String field, String message) {
		return new ContractException(
				"CONFLICT",
				message,
				List.of(Map.of("field", field, "reason", "MUST_BE_UNIQUE")));
	}
}
