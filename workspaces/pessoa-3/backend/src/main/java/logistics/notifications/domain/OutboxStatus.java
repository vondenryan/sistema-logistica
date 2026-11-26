package logistics.notifications.domain;

public enum OutboxStatus {
  PENDING,
  SENT,
  SKIPPED,
  FAILED
}
