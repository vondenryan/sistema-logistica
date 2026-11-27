package logistics.routing.infrastructure;

import contracts.v1.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import logistics.geography.application.RoutingProvider;
import logistics.routing.application.*;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;

@Repository
class RouteAdapter implements RouteStore, DriverRouteLookupPort {
  private final JdbcTemplate db;

  RouteAdapter(JdbcTemplate db) {
    this.db = db;
  }

  public void create(RouteSnapshot r) {
    try {
      db.update(
          "INSERT INTO routing.routes(id,store_id,origin_id,status) VALUES (?,?,?,?)",
          r.routeId(),
          r.storeId(),
          r.originId(),
          r.status().name());
      for (int i = 0; i < r.orderIds().size(); i++)
        db.update(
            "INSERT INTO routing.route_stops(route_id,order_id,position) VALUES (?,?,?)",
            r.routeId(),
            r.orderIds().get(i),
            i);
    } catch (DataIntegrityViolationException e) {
      throw new ContractException("CONFLICT");
    }
  }

  @Override
  public List<UUID> findRouteIdsByDriver(UUID driverId) {
    return db.query(
        "SELECT id FROM routing.routes WHERE driver_id = ?",
        (row, index) -> row.getObject("id", UUID.class),
        driverId);
  }

  public RouteSnapshot get(UUID id, boolean lock) {
    var rows =
        db.query(
            "SELECT * FROM routing.routes WHERE id=?" + (lock ? " FOR UPDATE" : ""),
            (r, n) ->
                new RouteSnapshot(
                    r.getObject("id", UUID.class),
                    r.getObject("store_id", UUID.class),
                    r.getObject("origin_id", UUID.class),
                    RouteStatus.valueOf(r.getString("status")),
                    db.query(
                        "SELECT order_id FROM routing.route_stops WHERE route_id=? ORDER BY"
                            + " position",
                        (s, i) -> s.getObject(1, UUID.class),
                        id),
                    r.getObject("driver_id", UUID.class),
                    r.getObject("vehicle_id", UUID.class),
                    r.getTimestamp("started_at") == null
                        ? null
                        : r.getTimestamp("started_at").toInstant()),
            id);
    if (rows.isEmpty()) throw new ContractException("NOT_FOUND");
    return rows.get(0);
  }

  public void reorder(UUID id, List<UUID> ids) {
    db.update("UPDATE routing.route_stops SET position=-position-1 WHERE route_id=?", id);
    for (int i = 0; i < ids.size(); i++)
      db.update(
          "UPDATE routing.route_stops SET position=? WHERE route_id=? AND order_id=?",
          i,
          id,
          ids.get(i));
  }

  public void assign(UUID id, AssignmentSnapshot a) {
    db.update(
        "UPDATE routing.routes SET status='ASSIGNED',driver_id=?,vehicle_id=? WHERE id=?",
        a.driverId(),
        a.vehicleId(),
        id);
  }

  public void start(UUID id) {
    db.update(
        "UPDATE routing.routes SET status='IN_PROGRESS',started_at=? WHERE id=?",
        Timestamp.from(Instant.now()),
        id);
  }

  public void complete(UUID id) {
    db.update("UPDATE routing.routes SET status='COMPLETED' WHERE id=?", id);
  }

  public void done(UUID id, UUID order) {
    db.update(
        "UPDATE routing.route_stops SET delivery_done=true WHERE route_id=? AND order_id=?",
        id,
        order);
  }

  public boolean pending(UUID id) {
    return Boolean.TRUE.equals(
        db.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM routing.route_stops WHERE route_id=? AND"
                + " delivery_done=false)",
            Boolean.class,
            id));
  }

  public boolean hasActiveOrder(UUID id) {
    return Boolean.TRUE.equals(
        db.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM routing.route_stops WHERE order_id=?)",
            Boolean.class,
            id));
  }

  public List<UUID> activeRoutes() {
    return db.query(
        "SELECT id FROM routing.routes WHERE status IN ('ASSIGNED','IN_PROGRESS') ORDER BY id",
        (r, n) -> r.getObject(1, UUID.class));
  }

  public void requeue(UUID id) {
    db.update("UPDATE routing.route_stops SET delivery_done=false WHERE route_id=?", id);
  }

  public List<UUID> pendingRoutes() {
    return db.query(
        "SELECT DISTINCT r.id FROM routing.routes r JOIN routing.route_stops s ON s.route_id=r.id"
            + " WHERE r.status='ASSIGNED' AND s.delivery_done=false",
        (r, n) -> r.getObject(1, UUID.class));
  }
}

@Configuration
class RouteConfig {
  @Bean
  RouteService routeService(
      RouteStore store,
      OrderSnapshotPort orders,
      CurrentActorPort actors,
      FleetAssignmentPort fleet,
      DeliveryCommandPort delivery,
      RoutingProvider provider,
      TransactionTemplate tx) {
    return new RouteService(store, orders, actors, fleet, delivery, provider, tx);
  }
}

@RestController
@RequestMapping("/api/v1/routes")
class RouteController {
  private final RouteService service;

  RouteController(RouteService service) {
    this.service = service;
  }

  private void empty(Map<String, Object> body) {
    if (!body.isEmpty()) throw new ContractException("VALIDATION_ERROR");
  }

  @PostMapping("/suggest")
  RouteService.Suggestion suggest(@RequestBody RouteService.Request r) {
    return service.suggest(r);
  }

  @PostMapping
  ResponseEntity<RouteSnapshot> create(@RequestBody RouteService.Request r) {
    return ResponseEntity.status(201).body(service.create(r));
  }

  @GetMapping("/{id}")
  RouteSnapshot get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PostMapping("/{id}/assign")
  RouteSnapshot assign(@PathVariable UUID id, @RequestBody Map<String, Object> b) {
    empty(b);
    return service.assign(id);
  }

  @PostMapping("/{id}/start")
  RouteSnapshot start(@PathVariable UUID id, @RequestBody Map<String, Object> b) {
    empty(b);
    return service.start(id);
  }

  @PutMapping("/{id}/stops")
  RouteSnapshot reorder(@PathVariable UUID id, @RequestBody RouteService.Reorder r) {
    return service.reorder(id, r);
  }
}
