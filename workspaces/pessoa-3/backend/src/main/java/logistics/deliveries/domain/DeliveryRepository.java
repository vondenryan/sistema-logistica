package logistics.deliveries.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeliveryRepository {
  void save(Delivery delivery);

  Optional<Delivery> findById(UUID id);

  Optional<Delivery> findByOrderId(UUID orderId);

  List<Delivery> findByRouteId(UUID routeId);

  List<Delivery> findByRouteIds(List<UUID> routeIds, int offset, int limit);

  void recordEvent(DeliveryEvent event);

  List<DeliveryEvent> findEventsByDeliveryId(UUID deliveryId);
}
