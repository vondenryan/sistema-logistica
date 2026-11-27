package logistics.deliveries.infrastructure.persistence;

import contracts.v1.DeliveryStatus;
import contracts.v1.Role;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import logistics.deliveries.domain.Delivery;
import logistics.deliveries.domain.DeliveryEvent;
import logistics.deliveries.domain.DeliveryRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcDeliveryRepository implements DeliveryRepository {
  private final JdbcTemplate jdbcTemplate;
  private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

  private final RowMapper<Delivery> deliveryRowMapper =
      (rs, rowNum) ->
          new Delivery(
              getUuid(rs, "id"),
              getUuid(rs, "order_id"),
              getUuid(rs, "route_id"),
              DeliveryStatus.valueOf(rs.getString("status")),
              rs.getString("receiver_name"),
              rs.getTimestamp("created_at").toInstant(),
              rs.getTimestamp("updated_at").toInstant());

  private final RowMapper<DeliveryEvent> eventRowMapper =
      (rs, rowNum) -> {
        String from = rs.getString("from_status");
        return new DeliveryEvent(
            getUuid(rs, "id"),
            getUuid(rs, "delivery_id"),
            from != null ? DeliveryStatus.valueOf(from) : null,
            DeliveryStatus.valueOf(rs.getString("to_status")),
            getUuid(rs, "actor_id"),
            Role.valueOf(rs.getString("actor_role")),
            rs.getString("reason"),
            rs.getString("receiver_name"),
            rs.getTimestamp("occurred_at").toInstant());
      };

  public JdbcDeliveryRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
    this.namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
  }

  private static UUID getUuid(ResultSet rs, String col) throws SQLException {
    Object val = rs.getObject(col);
    if (val == null) {
      return null;
    }
    if (val instanceof UUID uuid) {
      return uuid;
    }
    return UUID.fromString(val.toString());
  }

  @Override
  public void save(Delivery delivery) {
    int updated =
        jdbcTemplate.update(
            """
            UPDATE deliveries.deliveries
            SET route_id = ?, status = ?, receiver_name = ?, updated_at = ?
            WHERE id = ?
            """,
            delivery.getRouteId(),
            delivery.getStatus().name(),
            delivery.getReceiverName(),
            Timestamp.from(delivery.getUpdatedAt()),
            delivery.getId());

    if (updated == 0) {
      jdbcTemplate.update(
          """
          INSERT INTO deliveries.deliveries
          (id, order_id, route_id, status, receiver_name, created_at, updated_at)
          VALUES (?, ?, ?, ?, ?, ?, ?)
          """,
          delivery.getId(),
          delivery.getOrderId(),
          delivery.getRouteId(),
          delivery.getStatus().name(),
          delivery.getReceiverName(),
          Timestamp.from(delivery.getCreatedAt()),
          Timestamp.from(delivery.getUpdatedAt()));
    }
  }

  @Override
  public Optional<Delivery> findById(UUID id) {
    List<Delivery> results =
        jdbcTemplate.query(
            "SELECT id, order_id, route_id, status, receiver_name, created_at, updated_at FROM deliveries.deliveries WHERE id = ?",
            deliveryRowMapper,
            id);
    return results.stream().findFirst();
  }

  @Override
  public Optional<Delivery> lockById(UUID id) {
    return jdbcTemplate.query(
        """
        SELECT id, order_id, route_id, status, receiver_name, created_at, updated_at
        FROM deliveries.deliveries WHERE id = ? FOR UPDATE
        """,
        deliveryRowMapper, id).stream().findFirst();
  }

  @Override
  public Optional<Delivery> findByOrderId(UUID orderId) {
    List<Delivery> results =
        jdbcTemplate.query(
            "SELECT id, order_id, route_id, status, receiver_name, created_at, updated_at FROM deliveries.deliveries WHERE order_id = ?",
            deliveryRowMapper,
            orderId);
    return results.stream().findFirst();
  }

  @Override
  public List<Delivery> findByRouteId(UUID routeId) {
    return jdbcTemplate.query(
        "SELECT id, order_id, route_id, status, receiver_name, created_at, updated_at FROM deliveries.deliveries WHERE route_id = ? ORDER BY created_at ASC",
        deliveryRowMapper,
        routeId);
  }

  @Override
  public List<Delivery> findByRouteIds(List<UUID> routeIds, int offset, int limit) {
    if (routeIds == null || routeIds.isEmpty()) {
      return Collections.emptyList();
    }
    MapSqlParameterSource params = new MapSqlParameterSource();
    params.addValue("routeIds", routeIds);
    params.addValue("offset", Math.max(0, offset));
    params.addValue("limit", Math.max(1, limit));

    return namedParameterJdbcTemplate.query(
        """
        SELECT id, order_id, route_id, status, receiver_name, created_at, updated_at
        FROM deliveries.deliveries
        WHERE route_id IN (:routeIds)
        ORDER BY created_at DESC, id DESC
        LIMIT :limit OFFSET :offset
        """,
        params,
        deliveryRowMapper);
  }

  @Override
  public long countByRouteIds(List<UUID> routeIds) {
    if (routeIds == null || routeIds.isEmpty()) {
      return 0L;
    }
    MapSqlParameterSource params = new MapSqlParameterSource();
    params.addValue("routeIds", routeIds);
    Long count =
        namedParameterJdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM deliveries.deliveries WHERE route_id IN (:routeIds)",
            params,
            Long.class);
    return count != null ? count : 0L;
  }

  @Override
  public void recordEvent(DeliveryEvent event) {
    jdbcTemplate.update(
        """
        INSERT INTO deliveries.delivery_events
        (id, delivery_id, from_status, to_status, actor_id, actor_role, reason, receiver_name, occurred_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        event.id(),
        event.deliveryId(),
        event.fromStatus() != null ? event.fromStatus().name() : null,
        event.toStatus().name(),
        event.actorId(),
        event.actorRole().name(),
        event.reason(),
        event.receiverName(),
        Timestamp.from(event.occurredAt()));
  }

  @Override
  public List<DeliveryEvent> findEventsByDeliveryId(UUID deliveryId) {
    return jdbcTemplate.query(
        """
        SELECT id, delivery_id, from_status, to_status, actor_id, actor_role, reason, receiver_name, occurred_at
        FROM deliveries.delivery_events
        WHERE delivery_id = ?
        ORDER BY occurred_at ASC, id ASC
        """,
        eventRowMapper,
        deliveryId);
  }
}
