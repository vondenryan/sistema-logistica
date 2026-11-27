package br.com.sistemalogistica.pessoa2.contracts.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.sistemalogistica.pessoa2.fleet.FakeRouteSnapshotAdapter;
import contracts.v1.ContractException;
import contracts.v1.RouteSnapshot;
import contracts.v1.RouteStatus;

class FakeRouteSnapshotAdapterTests {

	private final FakeRouteSnapshotAdapter adapter = new FakeRouteSnapshotAdapter();

	@Test
	void exposesKnownRouteOriginAndExpectedStatuses() {
		RouteSnapshot planned = adapter.getById(FakeRouteSnapshotAdapter.ROUTE_1_ID);
		assertEquals(FakeRouteSnapshotAdapter.ROUTE_1_ID, planned.routeId());
		assertEquals(UUID.fromString("11111111-1111-4111-8111-111111111111"), planned.originId());
		assertEquals(RouteStatus.PLANNED, planned.status());

		assertEquals(
				RouteStatus.ASSIGNED,
				adapter.getById(FakeRouteSnapshotAdapter.ASSIGNED_ROUTE_ID).status());
		assertEquals(
				RouteStatus.COMPLETED,
				adapter.getById(FakeRouteSnapshotAdapter.COMPLETED_ROUTE_ID).status());
	}

	@Test
	void returnsNotFoundForAnUnknownRoute() {
		ContractException exception =
				assertThrows(ContractException.class, () -> adapter.getById(UUID.randomUUID()));

		assertEquals("NOT_FOUND", exception.code());
	}
}
