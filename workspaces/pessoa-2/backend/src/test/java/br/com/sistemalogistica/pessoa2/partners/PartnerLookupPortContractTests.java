package br.com.sistemalogistica.pessoa2.partners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.UUID;

import jakarta.persistence.Entity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import br.com.sistemalogistica.pessoa2.contracts.v1.AddressSnapshot;
import br.com.sistemalogistica.pessoa2.contracts.v1.OriginSnapshot;
import br.com.sistemalogistica.pessoa2.contracts.v1.PartnerLookupPort;
import br.com.sistemalogistica.pessoa2.contracts.v1.StoreSnapshot;
import br.com.sistemalogistica.pessoa2.origins.CreateOriginRequest;
import br.com.sistemalogistica.pessoa2.origins.OriginService;
import br.com.sistemalogistica.pessoa2.origins.OriginType;
import br.com.sistemalogistica.pessoa2.shared.contract.ContractException;
import br.com.sistemalogistica.pessoa2.stores.CreateStoreRequest;
import br.com.sistemalogistica.pessoa2.stores.StoreService;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class PartnerLookupPortContractTests {

	private static final br.com.sistemalogistica.pessoa2.origins.AddressSnapshot ORIGIN_ADDRESS =
			new br.com.sistemalogistica.pessoa2.origins.AddressSnapshot(
					"Rua Exemplo, 100", null, "Cidade Fictícia", "SC", "00000-000", "BR", -26.48, -49.07);
	private static final AddressSnapshot CONTRACT_ADDRESS =
			new AddressSnapshot("Rua Exemplo, 100", null, "Cidade Fictícia", "SC", "00000-000", "BR", -26.48, -49.07);

	@Autowired
	private PartnerLookupPort partnerLookupPort;

	@Autowired
	private StoreService storeService;

	@Autowired
	private OriginService originService;

	@Test
	void returnsContractSnapshotsForExistingOriginAndStore() {
		var store = storeService.create(new CreateStoreRequest("Partner contract " + UUID.randomUUID(), true));
		var origin = originService.create(
				new CreateOriginRequest(OriginType.DISTRIBUTION_CENTER, store.id(), ORIGIN_ADDRESS, true));

		OriginSnapshot originSnapshot = partnerLookupPort.getOrigin(origin.id());
		StoreSnapshot storeSnapshot = partnerLookupPort.getStore(store.id());

		assertEquals(new OriginSnapshot(
				origin.id(),
				br.com.sistemalogistica.pessoa2.contracts.v1.OriginType.DISTRIBUTION_CENTER,
				store.id(),
				CONTRACT_ADDRESS,
				true), originSnapshot);
		assertEquals(new StoreSnapshot(store.id(), true, store.name()), storeSnapshot);
		assertDoesNotExposeJpaEntities(originSnapshot);
		assertDoesNotExposeJpaEntities(storeSnapshot);
	}

	@Test
	void returnsInactiveOriginAndStoreSnapshots() {
		var store = storeService.create(new CreateStoreRequest("Inactive partner " + UUID.randomUUID(), false));
		var origin = originService.create(
				new CreateOriginRequest(OriginType.STORE, store.id(), ORIGIN_ADDRESS, false));

		assertFalse(partnerLookupPort.getOrigin(origin.id()).active());
		assertFalse(partnerLookupPort.getStore(store.id()).active());
	}

	@Test
	void returnsNotFoundForMissingOriginAndStore() {
		ContractException originException =
				assertThrows(ContractException.class, () -> partnerLookupPort.getOrigin(UUID.randomUUID()));
		ContractException storeException =
				assertThrows(ContractException.class, () -> partnerLookupPort.getStore(UUID.randomUUID()));

		assertEquals("NOT_FOUND", originException.getCode());
		assertEquals("NOT_FOUND", storeException.getCode());
	}

	@Test
	void matchesTheV1SnapshotComponentNamesAndTypes() {
		assertArrayEquals(
				new String[] {"id", "type", "storeId", "address", "active"},
				componentNames(OriginSnapshot.class));
		assertArrayEquals(
				new Class<?>[] {
					UUID.class,
					br.com.sistemalogistica.pessoa2.contracts.v1.OriginType.class,
					UUID.class,
					AddressSnapshot.class,
					boolean.class
				},
				componentTypes(OriginSnapshot.class));
		assertArrayEquals(
				new String[] {"line1", "line2", "city", "state", "postalCode", "country", "latitude", "longitude"},
				componentNames(AddressSnapshot.class));
		assertArrayEquals(
				new Class<?>[] {
					String.class, String.class, String.class, String.class, String.class, String.class,
					Double.class, Double.class
				},
				componentTypes(AddressSnapshot.class));
		assertArrayEquals(new String[] {"id", "active", "name"}, componentNames(StoreSnapshot.class));
		assertArrayEquals(
				new Class<?>[] {UUID.class, boolean.class, String.class},
				componentTypes(StoreSnapshot.class));
	}

	private void assertDoesNotExposeJpaEntities(Object snapshot) {
		Class<?> snapshotType = snapshot.getClass();
		assertFalse(snapshotType.isAnnotationPresent(Entity.class));
		for (RecordComponent component : snapshotType.getRecordComponents()) {
			Class<?> componentType = component.getType();
			assertFalse(componentType.isAnnotationPresent(Entity.class));
			if (componentType.isRecord()) {
				assertRecordHasNoJpaEntityComponents(componentType);
			}
		}
	}

	private String[] componentNames(Class<?> recordType) {
		return Arrays.stream(recordType.getRecordComponents())
				.map(RecordComponent::getName)
				.toArray(String[]::new);
	}

	private Class<?>[] componentTypes(Class<?> recordType) {
		return Arrays.stream(recordType.getRecordComponents())
				.map(RecordComponent::getType)
				.toArray(Class<?>[]::new);
	}

	private void assertRecordHasNoJpaEntityComponents(Class<?> recordType) {
		assertFalse(recordType.isAnnotationPresent(Entity.class));
		for (RecordComponent component : recordType.getRecordComponents()) {
			assertFalse(component.getType().isAnnotationPresent(Entity.class));
		}
	}
}
