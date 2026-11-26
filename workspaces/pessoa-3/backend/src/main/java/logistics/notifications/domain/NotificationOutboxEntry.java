package logistics.notifications.domain;

import contracts.v1.NotificationEventType;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class NotificationOutboxEntry {
  private final UUID id;
  private final UUID eventId;
  private final NotificationEventType eventType;
  private final UUID orderId;
  private final String recipientEmail;
  private final String subject;
  private final String body;
  private final String payloadHash;
  private OutboxStatus status;
  private int attempts;
  private Instant nextRetryAt;
  private String lastError;
  private final Instant occurredAt;
  private final Instant createdAt;
  private Instant updatedAt;

  public NotificationOutboxEntry(
      UUID id,
      UUID eventId,
      NotificationEventType eventType,
      UUID orderId,
      String recipientEmail,
      String subject,
      String body,
      String payloadHash,
      OutboxStatus status,
      int attempts,
      Instant nextRetryAt,
      String lastError,
      Instant occurredAt,
      Instant createdAt,
      Instant updatedAt) {
    this.id = Objects.requireNonNull(id);
    this.eventId = Objects.requireNonNull(eventId);
    this.eventType = Objects.requireNonNull(eventType);
    this.orderId = Objects.requireNonNull(orderId);
    this.recipientEmail = Objects.requireNonNull(recipientEmail);
    this.subject = Objects.requireNonNull(subject);
    this.body = Objects.requireNonNull(body);
    this.payloadHash = Objects.requireNonNull(payloadHash);
    this.status = Objects.requireNonNull(status);
    this.attempts = attempts;
    this.nextRetryAt = Objects.requireNonNull(nextRetryAt);
    this.lastError = lastError;
    this.occurredAt = Objects.requireNonNull(occurredAt);
    this.createdAt = Objects.requireNonNull(createdAt);
    this.updatedAt = Objects.requireNonNull(updatedAt);
  }

  public static NotificationOutboxEntry createPending(
      UUID eventId,
      NotificationEventType eventType,
      UUID orderId,
      String recipientEmail,
      String subject,
      String body,
      String payloadHash,
      Instant occurredAt,
      Instant now) {
    return new NotificationOutboxEntry(
        UUID.randomUUID(),
        eventId,
        eventType,
        orderId,
        recipientEmail,
        subject,
        body,
        payloadHash,
        OutboxStatus.PENDING,
        0,
        now,
        null,
        occurredAt,
        now,
        now);
  }

  public void markSent(Instant now) {
    this.status = OutboxStatus.SENT;
    this.attempts += 1;
    this.lastError = null;
    this.updatedAt = now;
  }

  public void recordFailure(
      String errorMessage, int maxAttempts, long baseRetrySeconds, Instant now) {
    this.attempts += 1;
    this.lastError = truncateError(errorMessage);
    this.updatedAt = now;
    if (this.attempts >= Math.max(1, maxAttempts)) {
      this.status = OutboxStatus.FAILED;
    } else {
      long delaySeconds = Math.max(1L, baseRetrySeconds) * (1L << Math.min(attempts - 1, 10));
      this.nextRetryAt = now.plusSeconds(delaySeconds);
    }
  }

  private static String truncateError(String msg) {
    if (msg == null || msg.isBlank()) {
      return "SMTP delivery error";
    }
    String sanitized = msg.replaceAll("[\\r\\n]+", " ").trim();
    return sanitized.length() > 500 ? sanitized.substring(0, 500) : sanitized;
  }

  public UUID getId() {
    return id;
  }

  public UUID getEventId() {
    return eventId;
  }

  public NotificationEventType getEventType() {
    return eventType;
  }

  public UUID getOrderId() {
    return orderId;
  }

  public String getRecipientEmail() {
    return recipientEmail;
  }

  public String getSubject() {
    return subject;
  }

  public String getBody() {
    return body;
  }

  public String getPayloadHash() {
    return payloadHash;
  }

  public OutboxStatus getStatus() {
    return status;
  }

  public int getAttempts() {
    return attempts;
  }

  public Instant getNextRetryAt() {
    return nextRetryAt;
  }

  public String getLastError() {
    return lastError;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
