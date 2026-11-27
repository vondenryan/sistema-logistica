package br.com.sistemalogistica.pessoa2.origins;

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

class OriginServiceAuthorizationTests {

	private static final AddressSnapshot ADDRESS =
			new AddressSnapshot("Rua Exemplo, 100", null, "Cidade Fictícia", "SC", null, "BR", -26.48, -49.07);

	private final OriginRepository repository = mock(OriginRepository.class);
	private final StoreRepository storeRepository = mock(StoreRepository.class);
	private final CurrentActorPort currentActorPort = mock(CurrentActorPort.class);
	private OriginService service;

	@BeforeEach
	void setUp() {
		service = new OriginService(repository, storeRepository, currentActorPort);
	}

	@Test
	void storeActorCannotListCreateOrPatch() {
		UUID storeId = UUID.randomUUID();
		when(currentActorPort.requireActor()).thenReturn(
				new ActorContext(UUID.randomUUID(), Role.STORE, storeId, null));

		assertForbidden(() -> service.list(-1, 20));
		assertForbidden(() -> service.create(new CreateOriginRequest(OriginType.STORE, storeId, ADDRESS, true)));
		assertForbidden(() -> service.patch(UUID.randomUUID(), new PatchOriginRequest(null, null, null, false)));
		verifyNoInteractions(repository, storeRepository);
	}

	@Test
	void storeActorCanReadOnlyAnOriginLinkedToOwnStore() {
		UUID ownStoreId = UUID.randomUUID();
		Origin ownOrigin = origin(ownStoreId);
		Origin otherOrigin = origin(UUID.randomUUID());
		when(currentActorPort.requireActor()).thenReturn(
				new ActorContext(UUID.randomUUID(), Role.STORE, ownStoreId, null));
		when(repository.findById(ownOrigin.getId())).thenReturn(Optional.of(ownOrigin));
		when(repository.findById(otherOrigin.getId())).thenReturn(Optional.of(otherOrigin));

		assertEquals(ownOrigin.getId(), service.get(ownOrigin.getId()).id());
		assertEquals("NOT_FOUND", assertThrows(
				ContractException.class,
				() -> service.get(otherOrigin.getId())).getCode());
	}

	@Test
	void missingStoreDuringCreateReturnsNotFound() {
		UUID storeId = UUID.randomUUID();
		when(currentActorPort.requireActor()).thenReturn(
				new ActorContext(UUID.randomUUID(), Role.ADMIN, null, null));
		when(storeRepository.existsById(storeId)).thenReturn(false);

		assertEquals("NOT_FOUND", assertThrows(
				ContractException.class,
				() -> service.create(new CreateOriginRequest(OriginType.STORE, storeId, ADDRESS, true))).getCode());
		verify(repository, never()).saveAndFlush(any(Origin.class));
	}

	@Test
	void emptyPatchRequiresAtLeastOneField() {
		when(currentActorPort.requireActor()).thenReturn(
				new ActorContext(UUID.randomUUID(), Role.OPERATOR, null, null));

		assertEquals("VALIDATION_ERROR", assertThrows(
				ContractException.class,
				() -> service.patch(UUID.randomUUID(), new PatchOriginRequest(null, null, null, null))).getCode());
		verifyNoInteractions(repository, storeRepository);
	}

	@Test
	void patchWithActiveFalseRetainsOrigin() {
		Origin origin = origin(UUID.randomUUID());
		when(currentActorPort.requireActor()).thenReturn(
				new ActorContext(UUID.randomUUID(), Role.OPERATOR, null, null));
		when(repository.findById(origin.getId())).thenReturn(Optional.of(origin));
		when(repository.saveAndFlush(origin)).thenReturn(origin);

		OriginSnapshot response = service.patch(
				origin.getId(), new PatchOriginRequest(null, null, null, false));

		assertFalse(response.active());
		verify(repository, never()).delete(any(Origin.class));
	}

	@Test
	void driverCannotReadOrigins() {
		when(currentActorPort.requireActor()).thenReturn(
				new ActorContext(UUID.randomUUID(), Role.DRIVER, null, UUID.randomUUID()));

		assertForbidden(() -> service.get(UUID.randomUUID()));
		verifyNoInteractions(repository, storeRepository);
	}

	private Origin origin(UUID storeId) {
		return new Origin(OriginType.DISTRIBUTION_CENTER, storeId, ADDRESS, true);
	}

	private void assertForbidden(Runnable operation) {
		assertEquals("FORBIDDEN", assertThrows(ContractException.class, operation::run).getCode());
	}
}
