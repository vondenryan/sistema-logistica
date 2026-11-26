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

  // Locks the selected row for the lifetime of the caller's transaction.
  Optional<NotificationOutboxEntry> lockNextDuePending(Instant cutoff);

  List<NotificationOutboxEntry> findByOrderId(UUID orderId);
}
