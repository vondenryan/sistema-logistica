package logistics.fakes;

import contracts.v1.ContractException;
import contracts.v1.DriverRouteLookupPort;
import contracts.v1.RouteSnapshot;
import contracts.v1.RouteSnapshotPort;
import contracts.v1.RouteStatus;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class FakeRouteSnapshotPort implements RouteSnapshotPort, DriverRouteLookupPort {
  public static final UUID CANONICAL_ROUTE_ID =
      UUID.fromString("33333333-3333-4333-8333-333333333333");
  public static final UUID CANONICAL_STORE_ID =
      UUID.fromString("22222222-2222-4222-8222-222222222222");
  public static final UUID CANONICAL_ORIGIN_ID =
      UUID.fromString("11111111-1111-4111-8111-111111111111");
  public static final UUID CANONICAL_DRIVER_ID =
      UUID.fromString("55555555-5555-4555-8555-555555555555");
  public static final UUID CANONICAL_VEHICLE_ID =
      UUID.fromString("66666666-6666-4666-8666-666666666666");

  private final Map<UUID, RouteSnapshot> routesById = new ConcurrentHashMap<>();

  public FakeRouteSnapshotPort() {
    resetToCanonical();
  }

  public final void resetToCanonical() {
    routesById.clear();

    RouteSnapshot canonicalRoute =
        new RouteSnapshot(
            CANONICAL_ROUTE_ID,
            CANONICAL_STORE_ID,
            CANONICAL_ORIGIN_ID,
            RouteStatus.ASSIGNED,
            List.of(UUID.fromString("44444444-4444-4444-8444-444444444444")),
            CANONICAL_DRIVER_ID,
            CANONICAL_VEHICLE_ID,
            null);

    register(canonicalRoute);
  }

  public void register(RouteSnapshot route) {
    routesById.put(route.routeId(), route);
  }

  @Override
  public RouteSnapshot getById(UUID routeId) {
    RouteSnapshot snapshot = routesById.get(routeId);
    if (snapshot == null) {
      throw new ContractException("NOT_FOUND");
    }
    return snapshot;
  }

  @Override
  public List<UUID> findRouteIdsByDriver(UUID driverId) {
    if (driverId == null) {
      return List.of();
    }
    return routesById.values().stream()
        .filter(r -> Objects.equals(r.driverId(), driverId))
        .map(RouteSnapshot::routeId)
        .toList();
  }
}
