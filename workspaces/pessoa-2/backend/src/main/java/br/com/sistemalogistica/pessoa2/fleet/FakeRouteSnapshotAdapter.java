package br.com.sistemalogistica.pessoa2.fleet;

import contracts.v1.RouteSnapshot;
import contracts.v1.RouteSnapshotPort;
import contracts.v1.RouteStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import contracts.v1.ContractException;

@Component
@Profile({"dev & !integration", "test"})
public class FakeRouteSnapshotAdapter implements RouteSnapshotPort {

	public static final UUID ROUTE_1_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
	public static final UUID ROUTE_2_ID = UUID.fromString("33333333-3333-4333-8333-333333333334");
	public static final UUID ROUTE_3_ID = UUID.fromString("33333333-3333-4333-8333-333333333335");
	public static final UUID ROUTE_4_ID = UUID.fromString("33333333-3333-4333-8333-333333333336");
	public static final UUID ROUTE_5_ID = UUID.fromString("33333333-3333-4333-8333-333333333337");
	public static final UUID ASSIGNED_ROUTE_ID = UUID.fromString("33333333-3333-4333-8333-333333333338");
	public static final UUID COMPLETED_ROUTE_ID = UUID.fromString("33333333-3333-4333-8333-333333333339");

	private static final UUID STORE_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
	private static final UUID ORIGIN_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
	private static final UUID ORDER_ID = UUID.fromString("44444444-4444-4444-8444-444444444444");
	private static final UUID DRIVER_ID = UUID.fromString("55555555-5555-4555-8555-555555555555");
	private static final UUID VEHICLE_ID = UUID.fromString("66666666-6666-4666-8666-666666666666");

	private final Map<UUID, RouteSnapshot> routes = Map.of(
			ROUTE_1_ID, plannedRoute(ROUTE_1_ID),
			ROUTE_2_ID, plannedRoute(ROUTE_2_ID),
			ROUTE_3_ID, plannedRoute(ROUTE_3_ID),
			ROUTE_4_ID, plannedRoute(ROUTE_4_ID),
			ROUTE_5_ID, plannedRoute(ROUTE_5_ID),
			ASSIGNED_ROUTE_ID, new RouteSnapshot(
					ASSIGNED_ROUTE_ID,
					STORE_ID,
					ORIGIN_ID,
					RouteStatus.ASSIGNED,
					List.of(ORDER_ID),
					DRIVER_ID,
					VEHICLE_ID,
					null),
			COMPLETED_ROUTE_ID, new RouteSnapshot(
					COMPLETED_ROUTE_ID,
					STORE_ID,
					ORIGIN_ID,
					RouteStatus.COMPLETED,
					List.of(ORDER_ID),
					DRIVER_ID,
					VEHICLE_ID,
					null));

	@Override
	public RouteSnapshot getById(UUID routeId) {
		RouteSnapshot snapshot = routes.get(routeId);
		if (snapshot == null) {
			throw new ContractException("NOT_FOUND");
		}
		return snapshot;
	}

	private static RouteSnapshot plannedRoute(UUID routeId) {
		return new RouteSnapshot(routeId, STORE_ID, ORIGIN_ID, RouteStatus.PLANNED, List.of(ORDER_ID), null, null, null);
	}
}
