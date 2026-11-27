package br.com.sistemalogistica.pessoa2.fleet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import br.com.sistemalogistica.pessoa2.contracts.v1.AssignmentRequest;
import br.com.sistemalogistica.pessoa2.contracts.v1.AssignmentSnapshot;
import br.com.sistemalogistica.pessoa2.contracts.v1.FakeRouteSnapshotAdapter;
import br.com.sistemalogistica.pessoa2.contracts.v1.FleetType;
import br.com.sistemalogistica.pessoa2.contracts.v1.ReleaseReason;
import br.com.sistemalogistica.pessoa2.contracts.v1.ReleaseReceipt;
import br.com.sistemalogistica.pessoa2.contracts.v1.ReleaseRequest;
import br.com.sistemalogistica.pessoa2.security.Role;
import br.com.sistemalogistica.pessoa2.shared.contract.ContractException;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class FleetAssignmentIntegrationTests {

	private static final UUID ORIGIN_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
	private static final String STORE_ID = "22222222-2222-4222-8222-222222222222";
	private static final UUID OWN_DRIVER_ID = UUID.fromString("55555555-5555-4555-8555-555555555555");
	private static final UUID THIRD_PARTY_DRIVER_ID = UUID.fromString("55555555-5555-4555-8555-555555555557");
	private static final UUID OWN_VEHICLE_ID = UUID.fromString("66666666-6666-4666-8666-666666666666");
	private static final UUID THIRD_PARTY_VEHICLE_ID = UUID.fromString("66666666-6666-4666-8666-666666666668");
	private static final List<UUID> TEST_ROUTE_IDS = List.of(
			FakeRouteSnapshotAdapter.ROUTE_2_ID,
			FakeRouteSnapshotAdapter.ROUTE_3_ID,
			FakeRouteSnapshotAdapter.ROUTE_4_ID,
			FakeRouteSnapshotAdapter.ROUTE_5_ID,
			FakeRouteSnapshotAdapter.COMPLETED_ROUTE_ID);

	@Autowired
	private FleetAssignmentService assignmentService;

	@Autowired
	private FleetAssignmentRepository assignmentRepository;

	@Autowired
	private FleetReleaseEventRepository releaseEventRepository;

	@Autowired
	private CarrierRepository carrierRepository;

	@Autowired
	private DriverRepository driverRepository;

	@Autowired
	private VehicleRepository vehicleRepository;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	private UUID concurrencyCarrierId;
	private UUID concurrencyDriverId;
	private UUID concurrencyVehicleId;

	@BeforeEach
	void clearReservedTestFixtures() {
		releaseEventRepository.deleteAllByRouteIdIn(TEST_ROUTE_IDS);
		assignmentRepository.deleteAllByRouteIdIn(TEST_ROUTE_IDS);
	}

	@AfterEach
	void cleanTestFixtures() {
		releaseEventRepository.deleteAllByRouteIdIn(TEST_ROUTE_IDS);
		assignmentRepository.deleteAllByRouteIdIn(TEST_ROUTE_IDS);
		if (concurrencyVehicleId != null) {
			vehicleRepository.deleteById(concurrencyVehicleId);
			concurrencyVehicleId = null;
		}
		if (concurrencyDriverId != null) {
			driverRepository.deleteById(concurrencyDriverId);
			concurrencyDriverId = null;
		}
		if (concurrencyCarrierId != null) {
			carrierRepository.deleteById(concurrencyCarrierId);
			concurrencyCarrierId = null;
		}
	}

	@Test
	void integrationDatabaseIsPostgresql() throws Exception {
		try (Connection connection = dataSource.getConnection()) {
			assertEquals("PostgreSQL", connection.getMetaData().getDatabaseProductName());
		}
	}

	@Test
	void ownFleetIsPreferredAndStableIdsBreakTies() {
		AssignmentSnapshot result = assignmentService.assign(request(FakeRouteSnapshotAdapter.ROUTE_4_ID, "400.000"));

		assertEquals(OWN_DRIVER_ID, result.driverId());
		assertEquals(OWN_VEHICLE_ID, result.vehicleId());
		assertEquals(FleetType.OWN, result.fleetType());
		assertNull(result.carrierId());
	}

	@Test
	void fallsBackToMatchingThirdPartyCarrierWhenOwnCapacityIsInsufficient() {
		AssignmentSnapshot result = assignmentService.assign(request(FakeRouteSnapshotAdapter.ROUTE_4_ID, "1500.000"));

		assertEquals(THIRD_PARTY_DRIVER_ID, result.driverId());
		assertEquals(THIRD_PARTY_VEHICLE_ID, result.vehicleId());
		assertEquals(FleetType.THIRD_PARTY, result.fleetType());
		assertNotNull(result.carrierId());
	}

	@Test
	void returnsNoEligibleResourceWhenCapacityCannotBeMet() {
		ContractException exception = assertThrows(
				ContractException.class,
				() -> assignmentService.assign(request(FakeRouteSnapshotAdapter.ROUTE_4_ID, "2500.000")));

		assertEquals("NO_ELIGIBLE_RESOURCE", exception.getCode());
	}

	@Test
	void validatesCapacityAndRouteOrigin() {
		ContractException invalidCapacity = assertThrows(
				ContractException.class,
				() -> assignmentService.assign(request(FakeRouteSnapshotAdapter.ROUTE_4_ID, "0")));
		assertEquals("VALIDATION_ERROR", invalidCapacity.getCode());

		ContractException mismatchedOrigin = assertThrows(
				ContractException.class,
				() -> assignmentService.assign(new AssignmentRequest(
						FakeRouteSnapshotAdapter.ROUTE_4_ID,
						UUID.randomUUID(),
						new BigDecimal("100.000"))));
		assertEquals("PRECONDITION_FAILED", mismatchedOrigin.getCode());
	}

	@Test
	void rejectsInvalidRequiredCapacityValuesAtPort() {
		for (String capacity : List.of("0", "-40.000", "0.0001")) {
			ContractException exception = assertThrows(
					ContractException.class,
					() -> assignmentService.assign(request(FakeRouteSnapshotAdapter.ROUTE_4_ID, capacity)));

			assertEquals("VALIDATION_ERROR", exception.getCode());
			assertTrue(exception.getDetails().stream()
					.anyMatch(detail -> "requiredCapacityKg".equals(detail.get("field"))));
		}
	}

	@Test
	void onlyPlannedRoutesCanBeAssigned() {
		ContractException exception = assertThrows(
				ContractException.class,
				() -> assignmentService.assign(request(FakeRouteSnapshotAdapter.ASSIGNED_ROUTE_ID, "100.000")));

		assertEquals("CONFLICT", exception.getCode());
	}

	@Test
	void assignmentIsIdempotentAndDifferentPayloadConflicts() {
		AssignmentRequest request = request(FakeRouteSnapshotAdapter.ROUTE_4_ID, "500.000");
		AssignmentSnapshot first = assignmentService.assign(request);
		AssignmentSnapshot repeated = assignmentService.assign(request);
		assertEquals(first.assignmentId(), repeated.assignmentId());

		ContractException conflict = assertThrows(
				ContractException.class,
				() -> assignmentService.assign(request(FakeRouteSnapshotAdapter.ROUTE_4_ID, "501.000")));
		assertEquals("IDEMPOTENCY_CONFLICT", conflict.getCode());
	}

	@Test
	void releaseIsIdempotentRejectsCancellationAndKeepsRouteAssignment() {
		AssignmentSnapshot plannedAssignment = assignmentService.assign(
				request(FakeRouteSnapshotAdapter.ROUTE_4_ID, "500.000"));
		UUID plannedEventId = UUID.randomUUID();
		ContractException notCompleted = assertThrows(
				ContractException.class,
				() -> assignmentService.release(new ReleaseRequest(
						plannedAssignment.routeId(),
						plannedEventId,
						ReleaseReason.ROUTE_COMPLETED)));
		assertEquals("PRECONDITION_FAILED", notCompleted.getCode());
		FleetAssignment plannedEntity = assignmentRepository.findByRouteId(plannedAssignment.routeId()).orElseThrow();
		plannedEntity.release(Instant.now());
		assignmentRepository.saveAndFlush(plannedEntity);

		AssignmentRequest assignmentRequest = request(FakeRouteSnapshotAdapter.COMPLETED_ROUTE_ID, "500.000");
		FleetAssignment assigned = assignmentRepository.saveAndFlush(new FleetAssignment(
				UUID.randomUUID(),
				assignmentRequest,
				OWN_DRIVER_ID,
				OWN_VEHICLE_ID,
				null,
				FleetType.OWN));
		UUID eventId = UUID.randomUUID();

		ContractException cancellation = assertThrows(
				ContractException.class,
				() -> assignmentService.release(new ReleaseRequest(
						assigned.getRouteId(),
						eventId,
						ReleaseReason.ROUTE_CANCELLED)));
		assertEquals("PRECONDITION_FAILED", cancellation.getCode());

		ReleaseRequest completed = new ReleaseRequest(assigned.getRouteId(), eventId, ReleaseReason.ROUTE_COMPLETED);
		ReleaseReceipt first = assignmentService.release(completed);
		ReleaseReceipt repeated = assignmentService.release(completed);
		assertTrue(first.released());
		assertEquals(false, repeated.released());
		assertNotNull(assignmentRepository.findByRouteId(assigned.getRouteId()).orElseThrow().getReleasedAt());

		ContractException unknownRoute = assertThrows(
				ContractException.class,
				() -> assignmentService.release(new ReleaseRequest(
						UUID.randomUUID(),
						UUID.randomUUID(),
						ReleaseReason.ROUTE_COMPLETED)));
		assertEquals("NOT_FOUND", unknownRoute.getCode());
	}

	@Test
	void devAssignmentEndpointIsProtectedAndLookupMatchesContract() throws Exception {
		UUID routeId = FakeRouteSnapshotAdapter.ROUTE_5_ID;
		String body = "{\"routeId\":\"" + routeId + "\",\"originId\":\"" + ORIGIN_ID
				+ "\",\"requiredCapacityKg\":\"1500.000\"}";

		mockMvc.perform(post("/api/v1/fleet/assignments")
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.routeId").value(routeId.toString()))
				.andExpect(jsonPath("$.fleetType").value("THIRD_PARTY"));

		mockMvc.perform(get("/api/v1/fleet/assignments/{routeId}", routeId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.routeId").value(routeId.toString()));

		mockMvc.perform(get("/api/v1/fleet/assignments/{routeId}", routeId)
						.header("X-Dev-Role", Role.STORE.name())
						.header("X-Dev-Store-Id", STORE_ID))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"));

		mockMvc.perform(get("/api/v1/fleet/assignments/{routeId}", UUID.randomUUID())
						.header("X-Dev-Role", "INVALID"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

		mockMvc.perform(get("/api/v1/fleet/assignments/{routeId}", UUID.randomUUID()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));

		mockMvc.perform(post("/api/v1/fleet/assignments")
						.header("X-Dev-Role", Role.STORE.name())
						.header("X-Dev-Store-Id", STORE_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isForbidden());
	}

	@Test
	void devAssignmentEndpointReturns409WhenNoPairCanCarryTheLoad() throws Exception {
		String body = "{\"routeId\":\"" + FakeRouteSnapshotAdapter.ROUTE_5_ID + "\",\"originId\":\"" + ORIGIN_ID
				+ "\",\"requiredCapacityKg\":\"2500.000\"}";

		mockMvc.perform(post("/api/v1/fleet/assignments")
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("NO_ELIGIBLE_RESOURCE"));
	}

	@Test
	void devAssignmentEndpointRejectsInvalidRequiredCapacityValues() throws Exception {
		for (String capacity : List.of("0", "-40.000", "0.0001")) {
			String body = "{\"routeId\":\"" + FakeRouteSnapshotAdapter.ROUTE_5_ID
					+ "\",\"originId\":\"" + ORIGIN_ID + "\",\"requiredCapacityKg\":\"" + capacity + "\"}";

			mockMvc.perform(post("/api/v1/fleet/assignments")
							.contentType(MediaType.APPLICATION_JSON)
							.content(body))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
					.andExpect(jsonPath("$.details[*].field", hasItem("requiredCapacityKg")));
		}
	}

	@Test
	void simultaneousDifferentRoutesCannotReserveTheSameOnlyPair() throws Exception {
		createSingleThirdPartyPair();
		AssignmentRequest firstRequest = request(FakeRouteSnapshotAdapter.ROUTE_2_ID, "2500.000");
		AssignmentRequest secondRequest = request(FakeRouteSnapshotAdapter.ROUTE_3_ID, "2500.000");

		List<Attempt> attempts = runTogether(
				() -> attempt(firstRequest),
				() -> attempt(secondRequest));

		assertEquals(1, attempts.stream().filter(attempt -> attempt.assignment() != null).count());
		assertEquals(1, attempts.stream().filter(attempt -> "NO_ELIGIBLE_RESOURCE".equals(attempt.errorCode())).count());
		assertEquals(1, assignmentRepository.findAllByReleasedAtIsNull().stream()
				.filter(assignment -> assignment.getVehicleId().equals(concurrencyVehicleId))
				.count());
	}

	@Test
	void simultaneousIdenticalRequestsForOneRouteReturnSameAssignmentId() throws Exception {
		createSingleThirdPartyPair();
		AssignmentRequest request = request(FakeRouteSnapshotAdapter.ROUTE_2_ID, "2500.000");

		List<AssignmentSnapshot> results = runTogether(
				() -> assignmentService.assign(request),
				() -> assignmentService.assign(request));

		assertEquals(results.get(0).assignmentId(), results.get(1).assignmentId());
		assertNotNull(results.get(0).assignmentId());
	}

	private void createSingleThirdPartyPair() {
		Carrier carrier = carrierRepository.saveAndFlush(
				new Carrier("Concurrency Carrier " + UUID.randomUUID(), true));
		Driver driver = driverRepository.saveAndFlush(
				new Driver("Concurrency Driver", carrier.getId(), true));
		Vehicle vehicle = vehicleRepository.saveAndFlush(new Vehicle(
				"CC-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
				new BigDecimal("3000.000"),
				FleetType.THIRD_PARTY,
				carrier.getId(),
				true));
		concurrencyCarrierId = carrier.getId();
		concurrencyDriverId = driver.getId();
		concurrencyVehicleId = vehicle.getId();
	}

	private static AssignmentRequest request(UUID routeId, String requiredCapacityKg) {
		return new AssignmentRequest(routeId, ORIGIN_ID, new BigDecimal(requiredCapacityKg));
	}

	private Attempt attempt(AssignmentRequest request) {
		try {
			return new Attempt(assignmentService.assign(request), null);
		} catch (ContractException exception) {
			return new Attempt(null, exception.getCode());
		}
	}

	private static <T> List<T> runTogether(Callable<T> first, Callable<T> second) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			Future<T> firstResult = executor.submit(awaitStart(ready, start, first));
			Future<T> secondResult = executor.submit(awaitStart(ready, start, second));
			assertTrue(ready.await(10, TimeUnit.SECONDS));
			start.countDown();
			return List.of(firstResult.get(20, TimeUnit.SECONDS), secondResult.get(20, TimeUnit.SECONDS));
		} finally {
			executor.shutdownNow();
			assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
		}
	}

	private static <T> Callable<T> awaitStart(
			CountDownLatch ready,
			CountDownLatch start,
			Callable<T> operation) {
		return () -> {
			ready.countDown();
			if (!start.await(10, TimeUnit.SECONDS)) {
				throw new IllegalStateException("Concurrency test start gate timed out.");
			}
			return operation.call();
		};
	}

	private record Attempt(AssignmentSnapshot assignment, String errorCode) {
	}
}
