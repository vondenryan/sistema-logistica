package br.com.sistemalogistica.pessoa2.fleet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;

import br.com.sistemalogistica.pessoa2.security.ActorContext;
import br.com.sistemalogistica.pessoa2.security.CurrentActorPort;
import br.com.sistemalogistica.pessoa2.security.Role;
import br.com.sistemalogistica.pessoa2.shared.contract.ContractException;

class FleetServiceAuthorizationTests {

	@Test
	void nonAdministrativeRolesReceiveForbiddenBeforeRepositoryAccess() {
		CarrierRepository carriers = mock(CarrierRepository.class);
		DriverRepository drivers = mock(DriverRepository.class);
		VehicleRepository vehicles = mock(VehicleRepository.class);
		CurrentActorPort storeActor = actor(Role.STORE);

		assertForbidden(() -> new CarrierService(carriers, storeActor).list(0, 20));
		assertForbidden(() -> new DriverService(drivers, carriers, storeActor).list(0, 20));
		assertForbidden(() -> new VehicleService(vehicles, carriers, storeActor).list(0, 20));
		assertForbidden(() -> new VehicleService(vehicles, carriers, actor(Role.DRIVER)).get(UUID.randomUUID()));

		verifyNoInteractions(carriers, drivers, vehicles);
	}

	@Test
	void missingIdentityRemainsUnauthenticated() {
		CurrentActorPort noIdentity = () -> {
			throw new ContractException("UNAUTHENTICATED", "Authentication is required.");
		};
		ContractException exception = assertThrows(
				ContractException.class,
				() -> new CarrierService(mock(CarrierRepository.class), noIdentity).list(0, 20));

		assertEquals("UNAUTHENTICATED", exception.getCode());
	}

	@Test
	void administratorsAndOperatorsCanListEachResource() {
		CarrierRepository carriers = mock(CarrierRepository.class);
		DriverRepository drivers = mock(DriverRepository.class);
		VehicleRepository vehicles = mock(VehicleRepository.class);
		when(carriers.findAllByOrderByCreatedAtAscIdAsc(org.mockito.ArgumentMatchers.any())).thenReturn(Page.empty());
		when(drivers.findAllByOrderByCreatedAtAscIdAsc(org.mockito.ArgumentMatchers.any())).thenReturn(Page.empty());
		when(vehicles.findAllByOrderByCreatedAtAscIdAsc(org.mockito.ArgumentMatchers.any())).thenReturn(Page.empty());

		new CarrierService(carriers, actor(Role.ADMIN)).list(0, 20);
		new DriverService(drivers, carriers, actor(Role.OPERATOR)).list(0, 20);
		new VehicleService(vehicles, carriers, actor(Role.ADMIN)).list(0, 20);
	}

	private CurrentActorPort actor(Role role) {
		return () -> new ActorContext(UUID.randomUUID(), role, null, null);
	}

	private void assertForbidden(Runnable operation) {
		assertEquals("FORBIDDEN", assertThrows(ContractException.class, operation::run).getCode());
	}
}
