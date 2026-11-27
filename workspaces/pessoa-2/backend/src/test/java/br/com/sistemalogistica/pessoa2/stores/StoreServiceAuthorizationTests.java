package br.com.sistemalogistica.pessoa2.stores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

class StoreServiceAuthorizationTests {

	private final StoreRepository repository = mock(StoreRepository.class);
	private final CurrentActorPort currentActorPort = mock(CurrentActorPort.class);
	private StoreService service;

	@BeforeEach
	void setUp() {
		service = new StoreService(repository, currentActorPort);
	}

	@Test
	void storeActorCannotListCreateOrPatch() {
		UUID ownStoreId = UUID.randomUUID();
		when(currentActorPort.requireActor()).thenReturn(
				new ActorContext(UUID.randomUUID(), Role.STORE, ownStoreId, null));

		assertEquals("FORBIDDEN", assertThrows(ContractException.class, () -> service.list(0, 20)).getCode());
		assertEquals("FORBIDDEN", assertThrows(
				ContractException.class,
				() -> service.create(new CreateStoreRequest("Nova loja", true))).getCode());
		assertEquals("FORBIDDEN", assertThrows(
				ContractException.class,
				() -> service.patch(ownStoreId, new PatchStoreRequest(null, false))).getCode());
		verifyNoInteractions(repository);
	}

	@Test
	void storeActorCanReadOnlyOwnStoreAndCannotEnumerateAnother() {
		Store ownStore = new Store("Loja própria", true);
		when(currentActorPort.requireActor()).thenReturn(
				new ActorContext(UUID.randomUUID(), Role.STORE, ownStore.getId(), null));
		when(repository.findById(ownStore.getId())).thenReturn(Optional.of(ownStore));

		assertEquals(ownStore.getId(), service.get(ownStore.getId()).id());
		ContractException notFound = assertThrows(ContractException.class, () -> service.get(UUID.randomUUID()));
		assertEquals("NOT_FOUND", notFound.getCode());
		verify(repository).findById(ownStore.getId());
		verify(repository, never()).findAllByOrderByCreatedAtAscIdAsc(any());
	}

	@Test
	void duplicateNameIsConflictForAdministrator() {
		when(currentActorPort.requireActor()).thenReturn(
				new ActorContext(UUID.randomUUID(), Role.ADMIN, null, null));
		when(repository.existsByNameIgnoreCase("Loja Norte")).thenReturn(true);

		ContractException conflict = assertThrows(
				ContractException.class,
				() -> service.create(new CreateStoreRequest(" Loja Norte ", true)));

		assertEquals("CONFLICT", conflict.getCode());
		verify(repository, never()).saveAndFlush(any(Store.class));
	}

	@Test
	void patchRequiresAtLeastOneField() {
		when(currentActorPort.requireActor()).thenReturn(
				new ActorContext(UUID.randomUUID(), Role.ADMIN, null, null));

		assertEquals("VALIDATION_ERROR", assertThrows(
				ContractException.class,
				() -> service.patch(UUID.randomUUID(), new PatchStoreRequest(null, null))).getCode());
		verifyNoInteractions(repository);
	}

	@Test
	void patchWithActiveFalseRetainsStoreAndDoesNotDeleteIt() {
		Store store = new Store("Loja Norte", true);
		when(currentActorPort.requireActor()).thenReturn(
				new ActorContext(UUID.randomUUID(), Role.OPERATOR, null, null));
		when(repository.findById(store.getId())).thenReturn(Optional.of(store));
		when(repository.saveAndFlush(store)).thenReturn(store);

		StoreResponse response = service.patch(store.getId(), new PatchStoreRequest(null, false));

		assertFalse(response.active());
		assertTrue(repository.findById(store.getId()).isPresent());
		verify(repository, never()).delete(any(Store.class));
	}

	@Test
	void driverCannotReadStore() {
		when(currentActorPort.requireActor()).thenReturn(
				new ActorContext(UUID.randomUUID(), Role.DRIVER, null, UUID.randomUUID()));

		assertEquals("FORBIDDEN", assertThrows(
				ContractException.class,
				() -> service.get(UUID.randomUUID())).getCode());
		verifyNoInteractions(repository);
	}
}
