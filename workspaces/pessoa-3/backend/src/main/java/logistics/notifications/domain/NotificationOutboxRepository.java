package logistics.notifications.domain;

import contracts.v1.NotificationEventType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationOutboxRepository {
  Optional<NotificationOutboxEntry> findByEvent(UUID eventId, NotificationEventType eventType);

  void save(NotificationOutboxEntry entry);

  List<NotificationOutboxEntry> findDuePending(Instant now, int limit);

  List<NotificationOutboxEntry> findByOrderId(UUID orderId);
}
