package logistics.deliveries.domain;

import contracts.v1.DeliveryStatus;
import contracts.v1.Role;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record DeliveryEvent(
    UUID id,
    UUID deliveryId,
    DeliveryStatus fromStatus,
    DeliveryStatus toStatus,
    UUID actorId,
    Role actorRole,
    String reason,
    String receiverName,
    Instant occurredAt) {

  public DeliveryEvent {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(deliveryId, "deliveryId must not be null");
    Objects.requireNonNull(toStatus, "toStatus must not be null");
    Objects.requireNonNull(actorId, "actorId must not be null");
    Objects.requireNonNull(actorRole, "actorRole must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
  }

  public static DeliveryEvent record(
      UUID deliveryId,
      DeliveryStatus fromStatus,
      DeliveryStatus toStatus,
      UUID actorId,
      Role actorRole,
      String reason,
      String receiverName) {
    return new DeliveryEvent(
        UUID.randomUUID(),
        deliveryId,
        fromStatus,
        toStatus,
        actorId,
        actorRole,
        reason,
        receiverName,
        Instant.now());
  }

  public static DeliveryEvent recordWithId(
      UUID eventId,
      UUID deliveryId,
      DeliveryStatus fromStatus,
      DeliveryStatus toStatus,
      UUID actorId,
      Role actorRole,
      String reason,
      String receiverName) {
    return new DeliveryEvent(
        eventId != null ? eventId : UUID.randomUUID(),
        deliveryId,
        fromStatus,
        toStatus,
        actorId,
        actorRole,
        reason,
        receiverName,
        Instant.now());
  }
}
