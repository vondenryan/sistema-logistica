package logistics.notifications.domain;

import contracts.v1.NotificationEventType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationOutboxRepository {
  Optional<NotificationOutboxEntry> findByEvent(UUID eventId, NotificationEventType eventType);

  boolean insertIfAbsent(NotificationOutboxEntry entry);

  void save(NotificationOutboxEntry entry);

  // Locks selected rows for the lifetime of the caller's transaction.
  List<NotificationOutboxEntry> lockDuePending(Instant now, int limit);

  List<NotificationOutboxEntry> findByOrderId(UUID orderId);
}
