package contracts.v1;

import java.math.*;
import java.time.*;
import java.util.*;

public record RouteSnapshot(
    UUID routeId,
    UUID storeId,
    UUID originId,
    RouteStatus status,
    List<UUID> orderIds,
    UUID driverId,
    UUID vehicleId,
    Instant startedAt) {}
