package contracts.v1;

import java.util.*;

public interface DeliveryCommandPort {
  DeliverySnapshot ensureCreated(UUID orderId, UUID eventId);

  DeliverySnapshot assignRoute(UUID orderId, UUID routeId, UUID eventId);
}
