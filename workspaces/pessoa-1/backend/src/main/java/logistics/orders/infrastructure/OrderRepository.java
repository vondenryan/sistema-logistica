package logistics.orders.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import contracts.v1.*;
import java.math.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import logistics.orders.application.OrderStore;
import logistics.orders.domain.OrderData.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OrderRepository implements OrderStore {
  private final JdbcTemplate db;
  private final ObjectMapper json;

  public OrderRepository(JdbcTemplate db, ObjectMapper json) {
    this.db = db;
    this.json = json;
  }

  private String encode(Object o) {
    try {
      return json.writeValueAsString(o);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private AddressSnapshot address(String s) {
    try {
      return s == null ? null : json.readValue(s, AddressSnapshot.class);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public void insert(UUID id, Input i, OriginSnapshot o, String tracking) {
    db.update(
        "INSERT INTO"
            + " orders.orders(id,store_id,origin_id,status,origin_json,destination_json,recipient_name,recipient_email,tracking_code,created_at)"
            + " VALUES (?,?,?,?,?,?,?,?,?,?)",
        id,
        i.storeId(),
        i.originId(),
        "DRAFT",
        encode(o.address()),
        encode(i.destination()),
        i.recipientName(),
        i.recipientEmail(),
        tracking,
        Timestamp.from(Instant.now()));
    items(id, i.items());
  }

  private void items(UUID id, List<Item> items) {
    for (int n = 0; n < items.size(); n++) {
      Item i = items.get(n);
      db.update(
          "INSERT INTO orders.order_items(order_id,position,description,quantity,unit_weight_kg)"
              + " VALUES (?,?,?,?,?)",
          id,
          n,
          i.description(),
          i.quantity(),
          new BigDecimal(i.unitWeightKg()));
    }
  }

  public void update(UUID id, Input i, OriginSnapshot o) {
    db.update(
        "UPDATE orders.orders SET"
            + " origin_id=?,origin_json=?,destination_json=?,recipient_name=?,recipient_email=?"
            + " WHERE id=?",
        i.originId(),
        encode(o.address()),
        encode(i.destination()),
        i.recipientName(),
        i.recipientEmail(),
        id);
    db.update("DELETE FROM orders.order_items WHERE order_id=?", id);
    items(id, i.items());
  }

  private View row(ResultSet r) throws SQLException {
    UUID id = r.getObject("id", UUID.class);
    List<Item> its =
        db.query(
            "SELECT description,quantity,unit_weight_kg FROM orders.order_items WHERE order_id=?"
                + " ORDER BY position",
            (a, n) ->
                new Item(
                    a.getString(1), a.getInt(2), a.getBigDecimal(3).setScale(3).toPlainString()),
            id);
    BigDecimal weight =
        its.stream()
            .map(i -> new BigDecimal(i.unitWeightKg()).multiply(BigDecimal.valueOf(i.quantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(3);
    return new View(
        id,
        r.getObject("store_id", UUID.class),
        r.getObject("origin_id", UUID.class),
        OrderStatus.valueOf(r.getString("status")),
        address(r.getString("origin_json")),
        address(r.getString("destination_json")),
        r.getString("recipient_name"),
        r.getString("recipient_email"),
        r.getString("tracking_code"),
        weight,
        null,
        "BRL",
        its,
        r.getTimestamp("created_at").toInstant());
  }

  public View get(UUID id, boolean lock) {
    List<View> rows =
        db.query(
            "SELECT * FROM orders.orders WHERE id=?" + (lock ? " FOR UPDATE" : ""),
            (r, n) -> row(r),
            id);
    if (rows.isEmpty()) throw new ContractException("NOT_FOUND");
    return rows.get(0);
  }

  public View getByTracking(String code) {
    List<View> rows =
        db.query("SELECT * FROM orders.orders WHERE tracking_code=?", (r, n) -> row(r), code);
    if (rows.isEmpty()) throw new ContractException("NOT_FOUND");
    return rows.get(0);
  }

  public Page list(UUID store, int page, int size) {
    String where = store == null ? "" : " WHERE store_id=?";
    Object[] args =
        store == null
            ? new Object[] {size, (long) page * size}
            : new Object[] {store, size, (long) page * size};
    List<View> rows =
        db.query(
            "SELECT * FROM orders.orders" + where + " ORDER BY created_at,id LIMIT ? OFFSET ?",
            (r, n) -> row(r),
            args);
    Long count =
        store == null
            ? db.queryForObject("SELECT count(*) FROM orders.orders", Long.class)
            : db.queryForObject(
                "SELECT count(*) FROM orders.orders WHERE store_id=?", Long.class, store);
    return new Page(rows, page, size, count);
  }

  public void confirm(UUID id, OriginSnapshot o) {
    Instant now = Instant.now();
    db.update(
        "UPDATE orders.orders SET status='CONFIRMED',origin_json=?,"
            + " confirmation_event=?,confirmed_at=? WHERE id=?",
        encode(o.address()),
        UUID.randomUUID(),
        Timestamp.from(now),
        id);
  }

  public void cancel(UUID id) {
    db.update("UPDATE orders.orders SET status='CANCELLED' WHERE id=?", id);
  }

  public Job job(UUID id) {
    var rows =
        db.query(
            "SELECT confirmation_event,recipient_email,confirmed_at,delivery_done,notification_done"
                + " FROM orders.orders WHERE id=? AND status='CONFIRMED'",
            (r, n) ->
                new Job(
                    r.getObject(1, UUID.class),
                    r.getString(2),
                    r.getTimestamp(3).toInstant(),
                    r.getBoolean(4),
                    r.getBoolean(5)),
            id);
    return rows.isEmpty() ? null : rows.get(0);
  }

  public void deliveryDone(UUID id) {
    db.update("UPDATE orders.orders SET delivery_done=true WHERE id=?", id);
  }

  public void notificationDone(UUID id) {
    db.update("UPDATE orders.orders SET notification_done=true WHERE id=?", id);
  }

  public List<UUID> pending() {
    return db.query(
        "SELECT id FROM orders.orders WHERE status='CONFIRMED' AND (delivery_done=false OR"
            + " notification_done=false)",
        (r, n) -> r.getObject(1, UUID.class));
  }
}
