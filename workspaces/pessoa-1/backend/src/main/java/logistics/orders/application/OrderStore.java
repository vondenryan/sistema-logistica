package logistics.orders.application;

import contracts.v1.*;
import java.time.*;
import java.util.*;
import logistics.orders.domain.OrderData.*;

public interface OrderStore {
  record Job(
      UUID eventId, String email, Instant at, boolean deliveryDone, boolean notificationDone) {}

  void insert(UUID id, Input input, OriginSnapshot origin, String tracking);

  void update(UUID id, Input input, OriginSnapshot origin);

  View get(UUID id, boolean lock);

  View getByTracking(String code);

  Page list(UUID storeId, int page, int size);

  void confirm(UUID id, OriginSnapshot origin);

  void cancel(UUID id);

  Job job(UUID id);

  void deliveryDone(UUID id);

  void notificationDone(UUID id);

  List<UUID> pending();
}
