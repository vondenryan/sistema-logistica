package br.com.sistemalogistica.pessoa2.contracts.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import contracts.v1.AddressSnapshot;
import contracts.v1.AssignmentRequest;
import contracts.v1.AssignmentSnapshot;
import contracts.v1.FleetAssignmentPort;
import contracts.v1.FleetType;
import contracts.v1.OriginSnapshot;
import contracts.v1.OriginType;
import contracts.v1.StoreSnapshot;
import contracts.v1.PartnerLookupPort;
import br.com.sistemalogistica.pessoa2.fleet.DevFleetAssignmentRequest;
import br.com.sistemalogistica.pessoa2.fleet.FakeRouteSnapshotAdapter;
import br.com.sistemalogistica.pessoa2.fleet.FleetAssignmentRepository;
import br.com.sistemalogistica.pessoa2.fleet.FleetAssignmentService;
import br.com.sistemalogistica.pessoa2.fleet.FleetAssignmentTransactionService;
import br.com.sistemalogistica.pessoa2.fleet.FleetReleaseEventRepository;
import br.com.sistemalogistica.pessoa2.origins.OriginRepository;
import br.com.sistemalogistica.pessoa2.security.CurrentActorPort;
import br.com.sistemalogistica.pessoa2.stores.StoreRepository;
import br.com.sistemalogistica.pessoa2.partners.PartnerLookupAdapter;

class FleetContractJsonTests {

	private static final UUID ORIGIN_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
	private static final UUID STORE_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
	private static final UUID ROUTE_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void partnerLookupSnapshotsSerializeToV1Fixtures() throws Exception {
		OriginRepository originRepository = mock(OriginRepository.class);
		StoreRepository storeRepository = mock(StoreRepository.class);
		br.com.sistemalogistica.pessoa2.origins.Origin origin =
				mock(br.com.sistemalogistica.pessoa2.origins.Origin.class);
		br.com.sistemalogistica.pessoa2.stores.Store store =
				mock(br.com.sistemalogistica.pessoa2.stores.Store.class);
		when(originRepository.findById(ORIGIN_ID)).thenReturn(Optional.of(origin));
		when(origin.getId()).thenReturn(ORIGIN_ID);
		when(origin.getType()).thenReturn(br.com.sistemalogistica.pessoa2.origins.OriginType.STORE);
		when(origin.getStoreId()).thenReturn(STORE_ID);
		when(origin.getAddress()).thenReturn(new br.com.sistemalogistica.pessoa2.origins.AddressSnapshot(
				"Rua Exemplo, 100", null, "Cidade Fictícia", "SC", "00000-000", "BR", -26.48, -49.07));
		when(origin.isActive()).thenReturn(true);
		when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(store));
		when(store.getId()).thenReturn(STORE_ID);
		when(store.getName()).thenReturn("Loja fictícia");
		when(store.isActive()).thenReturn(true);

		PartnerLookupPort partnerLookup = new PartnerLookupAdapter(originRepository, storeRepository);
		OriginSnapshot originSnapshot = partnerLookup.getOrigin(ORIGIN_ID);
		StoreSnapshot storeSnapshot = partnerLookup.getStore(STORE_ID);
		assertJsonFixture("origin-snapshot.json", originSnapshot);
		assertJsonFixture("store-snapshot.json", storeSnapshot);

		OriginSnapshot deserializedOrigin = objectMapper.readValue(fixture("origin-snapshot.json"), OriginSnapshot.class);
		StoreSnapshot deserializedStore = objectMapper.readValue(fixture("store-snapshot.json"), StoreSnapshot.class);
		assertEquals(ORIGIN_ID, deserializedOrigin.id());
		assertEquals(STORE_ID, deserializedOrigin.storeId());
		assertEquals(OriginType.STORE, deserializedOrigin.type());
		assertEquals(new BigDecimal("-26.48"), deserializedOrigin.address().latitude());
		assertEquals(STORE_ID, deserializedStore.id());
		assertTrue(deserializedStore.active());
		assertJsonFixture("route-snapshot.json", new FakeRouteSnapshotAdapter().getById(
				FakeRouteSnapshotAdapter.ASSIGNED_ROUTE_ID));
	}

	@Test
	void fleetAssignmentPortSerializesItsResponseToV1Fixture() throws Exception {
		FleetAssignmentRepository assignmentRepository = mock(FleetAssignmentRepository.class);
		FleetReleaseEventRepository releaseEventRepository = mock(FleetReleaseEventRepository.class);
		FleetAssignmentTransactionService transactionService =
				mock(FleetAssignmentTransactionService.class);
		AssignmentRequest request = new AssignmentRequest(ROUTE_ID, ORIGIN_ID, new BigDecimal("40.000"));
		AssignmentSnapshot snapshot = new AssignmentSnapshot(
				UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"),
				ROUTE_ID,
				UUID.fromString("55555555-5555-4555-8555-555555555555"),
				UUID.fromString("66666666-6666-4666-8666-666666666666"),
				null,
				FleetType.OWN);
		when(assignmentRepository.findByRouteId(ROUTE_ID)).thenReturn(Optional.empty());
		when(transactionService.reserve(request)).thenReturn(snapshot);

		FleetAssignmentPort port = new FleetAssignmentService(
				assignmentRepository,
				releaseEventRepository,
				transactionService,
				new FakeRouteSnapshotAdapter(),
				mock(CurrentActorPort.class));

		assertJsonFixture("assignment-snapshot.json", port.assign(request));
	}

	@Test
	void assignmentJsonKeepsWeightAsDecimalStringAndSnapshotNullability() throws Exception {
		String assignmentRequestJson = fixture("assignment-request.json");
		DevFleetAssignmentRequest request =
				objectMapper.readValue(assignmentRequestJson, DevFleetAssignmentRequest.class);
		assertJsonFixture("assignment-request.json", request);

		AssignmentRequest portRequest = new AssignmentRequest(
				request.routeId(),
				request.originId(),
				new BigDecimal(request.requiredCapacityKg()));
		assertEquals(ROUTE_ID, portRequest.routeId());
		assertEquals(ORIGIN_ID, portRequest.originId());
		assertEquals(new BigDecimal("40.000"), portRequest.requiredCapacityKg());
		assertEquals(3, portRequest.requiredCapacityKg().scale());

		AssignmentSnapshot snapshot = objectMapper.readValue(
				fixture("assignment-snapshot.json"),
				AssignmentSnapshot.class);
		assertJsonFixture("assignment-snapshot.json", snapshot);
		assertNull(snapshot.carrierId());
		assertEquals(FleetType.OWN, snapshot.fleetType());
	}

	private void assertJsonFixture(String filename, Object value) throws Exception {
		JsonNode expected = objectMapper.readTree(fixture(filename));
		JsonNode actual = objectMapper.readTree(objectMapper.writeValueAsString(value));
		assertEquals(expected, actual, filename);
	}

	private String fixture(String filename) throws Exception {
		try (InputStream input = getClass().getResourceAsStream("/contracts/v1/" + filename)) {
			assertNotNull(input, filename);
			return new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
		}
	}
}
