package br.com.sistemalogistica.pessoa2.origins;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.sistemalogistica.pessoa2.security.ActorContext;
import br.com.sistemalogistica.pessoa2.security.CurrentActorPort;
import br.com.sistemalogistica.pessoa2.security.Role;
import br.com.sistemalogistica.pessoa2.shared.contract.ContractException;
import br.com.sistemalogistica.pessoa2.stores.StoreRepository;

@Service
public class OriginService {

	private final OriginRepository repository;
	private final StoreRepository storeRepository;
	private final CurrentActorPort currentActorPort;

	public OriginService(
			OriginRepository repository,
			StoreRepository storeRepository,
			CurrentActorPort currentActorPort) {
		this.repository = repository;
		this.storeRepository = storeRepository;
		this.currentActorPort = currentActorPort;
	}

	@Transactional(readOnly = true)
	public OriginPageResponse list(int page, int size) {
		requireAdministrativeActor();
		validatePagination(page, size);

		Page<Origin> origins = repository.findAllByOrderByCreatedAtAscIdAsc(PageRequest.of(page, size));
		List<OriginSnapshot> items = origins.getContent().stream().map(OriginSnapshot::from).toList();
		return new OriginPageResponse(items, page, size, origins.getTotalElements());
	}

	@Transactional(readOnly = true)
	public OriginSnapshot get(UUID originId) {
		ActorContext actor = currentActorPort.requireActor();
		if (actor.role() == Role.STORE) {
			Origin origin = repository.findById(originId).orElseThrow(OriginService::originNotFound);
			if (!Objects.equals(actor.storeId(), origin.getStoreId())) {
				throw originNotFound();
			}
			return OriginSnapshot.from(origin);
		}
		if (!isAdministrative(actor.role())) {
			throw forbidden();
		}

		return OriginSnapshot.from(repository.findById(originId).orElseThrow(OriginService::originNotFound));
	}

	@Transactional
	public OriginSnapshot create(CreateOriginRequest request) {
		requireAdministrativeActor();
		requireExistingStore(request.storeId());

		Origin origin = new Origin(request.type(), request.storeId(), request.address(), request.active());
		return OriginSnapshot.from(repository.saveAndFlush(origin));
	}

	@Transactional
	public OriginSnapshot patch(UUID originId, PatchOriginRequest request) {
		requireAdministrativeActor();
		if (request.type() == null && request.storeId() == null && request.address() == null
				&& request.active() == null) {
			throw ContractException.validation("body", "At least one field must be provided.");
		}

		Origin origin = repository.findById(originId).orElseThrow(OriginService::originNotFound);
		if (request.storeId() != null) {
			requireExistingStore(request.storeId());
		}

		OriginType type = request.type() == null ? origin.getType() : request.type();
		UUID storeId = request.storeId() == null ? origin.getStoreId() : request.storeId();
		AddressSnapshot address = request.address() == null ? origin.getAddress() : request.address();
		boolean active = request.active() == null ? origin.isActive() : request.active();
		origin.update(type, storeId, address, active);
		return OriginSnapshot.from(repository.saveAndFlush(origin));
	}

	private void requireExistingStore(UUID storeId) {
		if (!storeRepository.existsById(storeId)) {
			throw new ContractException("NOT_FOUND", "Store was not found.");
		}
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

	private void validatePagination(int page, int size) {
		if (page < 0) {
			throw ContractException.validation("page", "Must be zero or greater.");
		}
		if (size < 1 || size > 100) {
			throw ContractException.validation("size", "Must be between 1 and 100.");
		}
	}

	private static ContractException forbidden() {
		return new ContractException("FORBIDDEN", "The actor is not allowed to perform this operation.");
	}

	private static ContractException originNotFound() {
		return new ContractException("NOT_FOUND", "Origin was not found.");
	}
}
