package logistics.notifications.infrastructure.persistence;

import contracts.v1.NotificationEventType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import logistics.notifications.domain.NotificationOutboxEntry;
import logistics.notifications.domain.NotificationOutboxRepository;
import logistics.notifications.domain.OutboxStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcNotificationOutboxRepository implements NotificationOutboxRepository {
  private final JdbcTemplate jdbcTemplate;

  private final RowMapper<NotificationOutboxEntry> rowMapper =
      (rs, rowNum) ->
          new NotificationOutboxEntry(
              getUuid(rs, "id"),
              getUuid(rs, "event_id"),
              NotificationEventType.valueOf(rs.getString("event_type")),
              getUuid(rs, "order_id"),
              rs.getString("recipient_email"),
              rs.getString("subject"),
              rs.getString("body"),
              rs.getString("payload_hash"),
              OutboxStatus.valueOf(rs.getString("status")),
              rs.getInt("attempts"),
              rs.getTimestamp("next_retry_at").toInstant(),
              rs.getString("last_error"),
              rs.getTimestamp("occurred_at").toInstant(),
              rs.getTimestamp("created_at").toInstant(),
              rs.getTimestamp("updated_at").toInstant());

  public JdbcNotificationOutboxRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  private static UUID getUuid(ResultSet rs, String col) throws SQLException {
    Object val = rs.getObject(col);
    if (val == null) {
      return null;
    }
    return val instanceof UUID uuid ? uuid : UUID.fromString(val.toString());
  }

  @Override
  public Optional<NotificationOutboxEntry> findByEvent(
      UUID eventId, NotificationEventType eventType) {
    List<NotificationOutboxEntry> rows =
        jdbcTemplate.query(
            """
            SELECT id, event_id, event_type, order_id, recipient_email, subject, body,
                   payload_hash, status, attempts, next_retry_at, last_error,
                   occurred_at, created_at, updated_at
            FROM notifications.notification_outbox
            WHERE event_id = ? AND event_type = ?
            """,
            rowMapper,
            eventId,
            eventType.name());
    return rows.stream().findFirst();
  }

  @Override
  public void save(NotificationOutboxEntry entry) {
    int updated =
        jdbcTemplate.update(
            """
            UPDATE notifications.notification_outbox
            SET status = ?, attempts = ?, next_retry_at = ?, last_error = ?, updated_at = ?
            WHERE id = ?
            """,
            entry.getStatus().name(),
            entry.getAttempts(),
            Timestamp.from(entry.getNextRetryAt()),
            entry.getLastError(),
            Timestamp.from(entry.getUpdatedAt()),
            entry.getId());

    if (updated == 0) {
      jdbcTemplate.update(
          """
          INSERT INTO notifications.notification_outbox
          (id, event_id, event_type, order_id, recipient_email, subject, body,
           payload_hash, status, attempts, next_retry_at, last_error,
           occurred_at, created_at, updated_at)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
          """,
          entry.getId(),
          entry.getEventId(),
          entry.getEventType().name(),
          entry.getOrderId(),
          entry.getRecipientEmail(),
          entry.getSubject(),
          entry.getBody(),
          entry.getPayloadHash(),
          entry.getStatus().name(),
          entry.getAttempts(),
          Timestamp.from(entry.getNextRetryAt()),
          entry.getLastError(),
          Timestamp.from(entry.getOccurredAt()),
          Timestamp.from(entry.getCreatedAt()),
          Timestamp.from(entry.getUpdatedAt()));
    }
  }

  @Override
  public List<NotificationOutboxEntry> findDuePending(Instant now, int limit) {
    return jdbcTemplate.query(
        """
        SELECT id, event_id, event_type, order_id, recipient_email, subject, body,
               payload_hash, status, attempts, next_retry_at, last_error,
               occurred_at, created_at, updated_at
        FROM notifications.notification_outbox
        WHERE status = 'PENDING' AND next_retry_at <= ?
        ORDER BY next_retry_at ASC, created_at ASC
        LIMIT ?
        """,
        rowMapper,
        Timestamp.from(now),
        Math.max(1, limit));
  }

  @Override
  public List<NotificationOutboxEntry> findByOrderId(UUID orderId) {
    return jdbcTemplate.query(
        """
        SELECT id, event_id, event_type, order_id, recipient_email, subject, body,
               payload_hash, status, attempts, next_retry_at, last_error,
               occurred_at, created_at, updated_at
        FROM notifications.notification_outbox
        WHERE order_id = ?
        ORDER BY created_at ASC
        """,
        rowMapper,
        orderId);
  }
}
