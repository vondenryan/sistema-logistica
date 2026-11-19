package logistics.deliveries.domain;

import contracts.v1.ContractException;
import contracts.v1.DeliverySnapshot;
import contracts.v1.DeliveryStatus;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class Delivery {
  private final UUID id;
  private final UUID orderId;
  private UUID routeId;
  private DeliveryStatus status;
  private String receiverName;
  private final Instant createdAt;
  private Instant updatedAt;

  public Delivery(
      UUID id,
      UUID orderId,
      UUID routeId,
      DeliveryStatus status,
      String receiverName,
      Instant createdAt,
      Instant updatedAt) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.orderId = Objects.requireNonNull(orderId, "orderId must not be null");
    this.routeId = routeId;
    this.status = Objects.requireNonNull(status, "status must not be null");
    this.receiverName = receiverName;
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
  }

  public static Delivery create(UUID orderId) {
    Instant now = Instant.now();
    return new Delivery(
        UUID.randomUUID(),
        orderId,
        null,
        DeliveryStatus.PENDING,
        null,
        now,
        now);
  }

  public static Delivery createWithId(UUID deliveryId, UUID orderId) {
    Instant now = Instant.now();
    return new Delivery(
        deliveryId,
        orderId,
        null,
        DeliveryStatus.PENDING,
        null,
        now,
        now);
  }

  public void assignRoute(UUID newRouteId) {
    Objects.requireNonNull(newRouteId, "routeId must not be null");
    if (this.status == DeliveryStatus.ASSIGNED && Objects.equals(this.routeId, newRouteId)) {
      return; // Idempotent
    }

    if (this.status != DeliveryStatus.PENDING && this.status != DeliveryStatus.FAILED_ATTEMPT) {
      throw new ContractException("CONFLICT");
    }

    this.routeId = newRouteId;
    this.status = DeliveryStatus.ASSIGNED;
    this.updatedAt = Instant.now();
  }

  public void startTransit() {
    if (this.status == DeliveryStatus.IN_TRANSIT) {
      return; // Idempotent
    }

    if (this.status != DeliveryStatus.ASSIGNED) {
      throw new ContractException("CONFLICT");
    }

    this.status = DeliveryStatus.IN_TRANSIT;
    this.updatedAt = Instant.now();
  }

  public void failAttempt(String reason) {
    if (reason == null || reason.trim().isEmpty() || reason.length() > 500) {
      throw new ContractException("VALIDATION_ERROR");
    }

    if (this.status != DeliveryStatus.IN_TRANSIT) {
      throw new ContractException("CONFLICT");
    }

    this.status = DeliveryStatus.FAILED_ATTEMPT;
    this.updatedAt = Instant.now();
  }

  public void complete(String receiver) {
    if (receiver == null || receiver.trim().isEmpty() || receiver.length() > 150) {
      throw new ContractException("VALIDATION_ERROR");
    }

    String trimmedReceiver = receiver.trim();

    if (this.status == DeliveryStatus.DELIVERED) {
      if (Objects.equals(this.receiverName, trimmedReceiver)) {
        return; // Idempotent
      }
      throw new ContractException("CONFLICT");
    }

    if (this.status != DeliveryStatus.IN_TRANSIT) {
      throw new ContractException("CONFLICT");
    }

    this.receiverName = trimmedReceiver;
    this.status = DeliveryStatus.DELIVERED;
    this.updatedAt = Instant.now();
  }

  public void cancel(String reason) {
    if (this.status == DeliveryStatus.CANCELLED) {
      return; // Idempotent
    }

    if (this.status == DeliveryStatus.DELIVERED || this.status == DeliveryStatus.IN_TRANSIT) {
      throw new ContractException("CONFLICT");
    }

    this.status = DeliveryStatus.CANCELLED;
    this.updatedAt = Instant.now();
  }

  public DeliverySnapshot toSnapshot() {
    return new DeliverySnapshot(id, orderId, routeId, status, updatedAt, receiverName);
  }

  public UUID getId() {
    return id;
  }

  public UUID getOrderId() {
    return orderId;
  }

  public UUID getRouteId() {
    return routeId;
  }

  public DeliveryStatus getStatus() {
    return status;
  }

  public String getReceiverName() {
    return receiverName;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
