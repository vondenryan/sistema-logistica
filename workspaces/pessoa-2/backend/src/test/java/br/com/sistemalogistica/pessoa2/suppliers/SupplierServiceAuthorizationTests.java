package br.com.sistemalogistica.pessoa2.suppliers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.sistemalogistica.pessoa2.security.ActorContext;
import br.com.sistemalogistica.pessoa2.security.CurrentActorPort;
import br.com.sistemalogistica.pessoa2.security.Role;
import br.com.sistemalogistica.pessoa2.shared.contract.ContractException;
import br.com.sistemalogistica.pessoa2.stores.StoreRepository;

class SupplierServiceAuthorizationTests {

	private final SupplierRepository repository = mock(SupplierRepository.class);
	private final StoreRepository storeRepository = mock(StoreRepository.class);
	private final CurrentActorPort currentActorPort = mock(CurrentActorPort.class);
	private SupplierService service;

	@BeforeEach
	void setUp() {
		service = new SupplierService(repository, storeRepository, currentActorPort);
	}

	@Test
	void storeActorCannotListCreateOrPatch() {
		UUID storeId = UUID.randomUUID();
		when(currentActorPort.requireActor()).thenReturn(
				new ActorContext(UUID.randomUUID(), Role.STORE, storeId, null));

		assertForbidden(() -> service.list(0, 20));
		assertForbidden(() -> service.create(
				new CreateSupplierRequest("Fornecedor", "compras@example.com", storeId)));
		assertForbidden(() -> service.patch(
				UUID.randomUUID(), new PatchSupplierRequest(null, null, null, false)));
		verifyNoInteractions(repository, storeRepository);
	}

	@Test
	void storeActorCanReadOnlySupplierFromOwnStore() {
		UUID ownStoreId = UUID.randomUUID();
		Supplier ownSupplier = new Supplier("Próprio", null, ownStoreId, true);
		Supplier otherSupplier = new Supplier("Outro", null, UUID.randomUUID(), true);
		when(currentActorPort.requireActor()).thenReturn(
				new ActorContext(UUID.randomUUID(), Role.STORE, ownStoreId, null));
		when(repository.findById(ownSupplier.getId())).thenReturn(Optional.of(ownSupplier));
		when(repository.findById(otherSupplier.getId())).thenReturn(Optional.of(otherSupplier));

		assertEquals(ownSupplier.getId(), service.get(ownSupplier.getId()).id());
		assertEquals("NOT_FOUND", assertThrows(
				ContractException.class,
				() -> service.get(otherSupplier.getId())).getCode());
	}

	@Test
	void missingStoreReturnsNotFoundWithoutSaving() {
		UUID storeId = UUID.randomUUID();
		when(currentActorPort.requireActor()).thenReturn(
				new ActorContext(UUID.randomUUID(), Role.ADMIN, null, null));
		when(storeRepository.existsById(storeId)).thenReturn(false);

		assertEquals("NOT_FOUND", assertThrows(
				ContractException.class,
				() -> service.create(
						new CreateSupplierRequest("Fornecedor", "compras@example.com", storeId))).getCode());
		verify(repository, never()).saveAndFlush(any(Supplier.class));
	}

	@Test
	void patchRequiresAtLeastOneFieldAndInactivationDoesNotDelete() {
		when(currentActorPort.requireActor()).thenReturn(
				new ActorContext(UUID.randomUUID(), Role.OPERATOR, null, null));
		assertEquals("VALIDATION_ERROR", assertThrows(
				ContractException.class,
				() -> service.patch(UUID.randomUUID(), new PatchSupplierRequest(null, null, null, null))).getCode());

		Supplier supplier = new Supplier("Fornecedor", "compras@example.com", UUID.randomUUID(), true);
		when(repository.findById(supplier.getId())).thenReturn(Optional.of(supplier));
		when(repository.saveAndFlush(supplier)).thenReturn(supplier);

		SupplierResponse response = service.patch(
				supplier.getId(), new PatchSupplierRequest(null, null, null, false));

		assertFalse(supplier.isActive());
		assertEquals(supplier.getId(), response.id());
		verify(repository, never()).delete(any(Supplier.class));
	}

	@Test
	void driverCannotReadSupplier() {
		when(currentActorPort.requireActor()).thenReturn(
				new ActorContext(UUID.randomUUID(), Role.DRIVER, null, UUID.randomUUID()));

		assertForbidden(() -> service.get(UUID.randomUUID()));
		verifyNoInteractions(repository, storeRepository);
	}

	private void assertForbidden(Runnable operation) {
		assertEquals("FORBIDDEN", assertThrows(ContractException.class, operation::run).getCode());
	}
}
