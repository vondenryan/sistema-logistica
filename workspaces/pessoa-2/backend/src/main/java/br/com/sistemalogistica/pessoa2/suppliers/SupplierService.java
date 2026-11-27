package br.com.sistemalogistica.pessoa2.suppliers;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

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
public class SupplierService {

	private static final Pattern EMAIL_PATTERN =
			Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

	private final SupplierRepository repository;
	private final StoreRepository storeRepository;
	private final CurrentActorPort currentActorPort;

	public SupplierService(
			SupplierRepository repository,
			StoreRepository storeRepository,
			CurrentActorPort currentActorPort) {
		this.repository = repository;
		this.storeRepository = storeRepository;
		this.currentActorPort = currentActorPort;
	}

	@Transactional(readOnly = true)
	public SupplierPageResponse list(int page, int size) {
		requireAdministrativeActor();
		validatePagination(page, size);

		Page<Supplier> suppliers = repository.findAllByOrderByCreatedAtAscIdAsc(PageRequest.of(page, size));
		List<SupplierResponse> items = suppliers.getContent().stream().map(SupplierResponse::from).toList();
		return new SupplierPageResponse(items, page, size, suppliers.getTotalElements());
	}

	@Transactional(readOnly = true)
	public SupplierResponse get(UUID supplierId) {
		ActorContext actor = currentActorPort.requireActor();
		if (actor.role() != Role.STORE && !isAdministrative(actor.role())) {
			throw forbidden();
		}
		Supplier supplier = repository.findById(supplierId).orElseThrow(SupplierService::supplierNotFound);

		if (actor.role() == Role.STORE) {
			if (!Objects.equals(actor.storeId(), supplier.getStoreId())) {
				throw supplierNotFound();
			}
			return SupplierResponse.from(supplier);
		}
		return SupplierResponse.from(supplier);
	}

	@Transactional
	public SupplierResponse create(CreateSupplierRequest request) {
		requireAdministrativeActor();
		String name = normalizeName(request.name());
		String contactEmail = normalizeEmail(request.contactEmail());
		requireExistingStore(request.storeId());

		Supplier supplier = new Supplier(name, contactEmail, request.storeId(), true);
		return SupplierResponse.from(repository.saveAndFlush(supplier));
	}

	@Transactional
	public SupplierResponse patch(UUID supplierId, PatchSupplierRequest request) {
		requireAdministrativeActor();
		if (request.name() == null && request.contactEmail() == null
				&& request.storeId() == null && request.active() == null) {
			throw ContractException.validation("body", "At least one field must be provided.");
		}

		Supplier supplier = repository.findById(supplierId).orElseThrow(SupplierService::supplierNotFound);
		if (request.storeId() != null) {
			requireExistingStore(request.storeId());
		}

		String name = request.name() == null ? supplier.getName() : normalizeName(request.name());
		String contactEmail = request.contactEmail() == null
				? supplier.getContactEmail()
				: normalizeEmail(request.contactEmail());
		UUID storeId = request.storeId() == null ? supplier.getStoreId() : request.storeId();
		boolean active = request.active() == null ? supplier.isActive() : request.active();

		supplier.update(name, contactEmail, storeId, active);
		return SupplierResponse.from(repository.saveAndFlush(supplier));
	}

	private void requireExistingStore(UUID storeId) {
		if (!storeRepository.existsById(storeId)) {
			throw new ContractException("NOT_FOUND", "Store was not found.");
		}
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

	private String normalizeEmail(String email) {
		if (email == null) {
			return null;
		}
		String normalized = email.trim();
		if (normalized.isEmpty()) {
			throw ContractException.validation("contactEmail", "Must be a valid email.");
		}
		if (normalized.length() > 254) {
			throw ContractException.validation("contactEmail", "Must be at most 254 characters.");
		}
		if (!EMAIL_PATTERN.matcher(normalized).matches()) {
			throw ContractException.validation("contactEmail", "Must be a valid email.");
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

	private static ContractException supplierNotFound() {
		return new ContractException("NOT_FOUND", "Supplier was not found.");
	}
}
