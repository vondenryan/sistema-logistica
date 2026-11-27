package br.com.sistemalogistica.pessoa2.stores;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.sistemalogistica.pessoa2.security.ActorContext;
import br.com.sistemalogistica.pessoa2.security.CurrentActorPort;
import br.com.sistemalogistica.pessoa2.security.Role;
import br.com.sistemalogistica.pessoa2.shared.contract.ContractException;

@Service
public class StoreService {

	private static final String STORE_NAME_INDEX = "ux_stores_name_ci";

	private final StoreRepository repository;
	private final CurrentActorPort currentActorPort;

	public StoreService(StoreRepository repository, CurrentActorPort currentActorPort) {
		this.repository = repository;
		this.currentActorPort = currentActorPort;
	}

	@Transactional(readOnly = true)
	public StorePageResponse list(int page, int size) {
		requireAdministrativeActor();
		if (page < 0) {
			throw ContractException.validation("page", "Must be zero or greater.");
		}
		if (size < 1 || size > 100) {
			throw ContractException.validation("size", "Must be between 1 and 100.");
		}

		Page<Store> stores = repository.findAllByOrderByCreatedAtAscIdAsc(PageRequest.of(page, size));
		List<StoreResponse> items = stores.getContent().stream().map(StoreResponse::from).toList();
		return new StorePageResponse(items, page, size, stores.getTotalElements());
	}

	@Transactional(readOnly = true)
	public StoreResponse get(UUID storeId) {
		ActorContext actor = currentActorPort.requireActor();
		if (actor.role() == Role.STORE && !storeId.equals(actor.storeId())) {
			throw notFound();
		}
		if (actor.role() != Role.STORE && !isAdministrative(actor.role())) {
			throw forbidden();
		}

		return StoreResponse.from(repository.findById(storeId).orElseThrow(StoreService::notFound));
	}

	@Transactional
	public StoreResponse create(CreateStoreRequest request) {
		requireAdministrativeActor();
		String name = normalizeName(request.name());
		if (repository.existsByNameIgnoreCase(name)) {
			throw conflict();
		}

		Store store = new Store(name, request.active());
		return StoreResponse.from(save(store));
	}

	@Transactional
	public StoreResponse patch(UUID storeId, PatchStoreRequest request) {
		requireAdministrativeActor();
		if (request.name() == null && request.active() == null) {
			throw ContractException.validation("body", "At least one field must be provided.");
		}

		Store store = repository.findById(storeId).orElseThrow(StoreService::notFound);
		String name = request.name() == null ? store.getName() : normalizeName(request.name());
		boolean active = request.active() == null ? store.isActive() : request.active();

		if (!name.equalsIgnoreCase(store.getName()) && repository.existsByNameIgnoreCaseAndIdNot(name, storeId)) {
			throw conflict();
		}

		store.update(name, active);
		return StoreResponse.from(save(store));
	}

	private Store save(Store store) {
		try {
			return repository.saveAndFlush(store);
		} catch (DataIntegrityViolationException exception) {
			if (isStoreNameViolation(exception)) {
				throw conflict();
			}
			throw exception;
		}
	}

	private boolean isStoreNameViolation(Throwable exception) {
		for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
			if (cause instanceof ConstraintViolationException violation
					&& STORE_NAME_INDEX.equals(violation.getConstraintName())) {
				return true;
			}
		}
		return false;
	}

	private String normalizeName(String name) {
		if (name == null || name.isBlank()) {
			throw ContractException.validation("name", "Must not be blank.");
		}
		String normalized = name.trim();
		if (normalized.length() > 160) {
			throw ContractException.validation("name", "Must be at most 160 characters.");
		}
		return normalized;
	}

	private void requireAdministrativeActor() {
		ActorContext actor = currentActorPort.requireActor();
		if (!isAdministrative(actor.role())) {
			throw forbidden();
		}
	}

	private boolean isAdministrative(Role role) {
		return role == Role.ADMIN || role == Role.OPERATOR;
	}

	private static ContractException forbidden() {
		return new ContractException("FORBIDDEN", "The actor is not allowed to perform this operation.");
	}

	private static ContractException notFound() {
		return new ContractException("NOT_FOUND", "Store was not found.");
	}

	private static ContractException conflict() {
		return new ContractException(
				"CONFLICT",
				"A store with this name already exists.",
				List.of(Map.of("field", "name", "reason", "MUST_BE_UNIQUE")));
	}
}
