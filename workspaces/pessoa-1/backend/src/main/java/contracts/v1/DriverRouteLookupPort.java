package contracts.v1;

import java.util.List;
import java.util.UUID;

public interface DriverRouteLookupPort {
  List<UUID> findRouteIdsByDriver(UUID driverId);
}
