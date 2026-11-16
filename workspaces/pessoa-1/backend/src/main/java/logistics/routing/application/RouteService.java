package logistics.routing.application;

import contracts.v1.*;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import logistics.geography.application.RoutingProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

public class RouteService implements RouteSnapshotPort {
  private final RouteStore store;
  private final OrderSnapshotPort orders;
  private final CurrentActorPort actors;
  private final FleetAssignmentPort fleet;
  private final DeliveryCommandPort delivery;
  private final RoutingProvider provider;
  private final TransactionTemplate tx;

  public RouteService(
      RouteStore store,
      OrderSnapshotPort orders,
      CurrentActorPort actors,
      FleetAssignmentPort fleet,
      DeliveryCommandPort delivery,
      RoutingProvider provider,
      TransactionTemplate tx) {
    this.store = store;
    this.orders = orders;
    this.actors = actors;
    this.fleet = fleet;
    this.delivery = delivery;
    this.provider = provider;
    this.tx = tx;
  }

  public record Request(UUID originId, List<UUID> orderIds) {}

  public record Reorder(List<UUID> orderedOrderIds) {}

  public record Suggestion(UUID originId, List<UUID> orderedOrderIds, String estimatedDistanceKm) {}

  private void operator() {
    var r = actors.requireActor().role();
    if (r != Role.ADMIN && r != Role.OPERATOR) throw new ContractException("FORBIDDEN");
  }

  private List<OrderSnapshot> validate(Request r) {
    if (r == null
        || r.originId() == null
        || r.orderIds() == null
        || r.orderIds().size() < 1
        || r.orderIds().size() > 10
        || r.orderIds().stream().anyMatch(Objects::isNull)
        || new HashSet<>(r.orderIds()).size() != r.orderIds().size())
      throw new ContractException("VALIDATION_ERROR");
    var result = r.orderIds().stream().map(orders::getById).toList();
    UUID shop = result.get(0).storeId();
    for (var o : result)
      if (o.status() != OrderStatus.CONFIRMED
          || !o.originId().equals(r.originId())
          || !o.storeId().equals(shop)
          || store.hasActiveOrder(o.orderId())) throw new ContractException("CONFLICT");
    return result;
  }

  public Suggestion suggest(Request request) {
    operator();
    var remaining = new ArrayList<>(validate(request));
    var current = remaining.get(0).origin();
    BigDecimal total = BigDecimal.ZERO;
    var ids = new ArrayList<UUID>();
    while (!remaining.isEmpty()) {
      OrderSnapshot best = null;
      BigDecimal distance = null;
      for (var order : remaining) {
        BigDecimal d = provider.roadDistanceKm(current, order.destination());
        if (best == null || d.compareTo(distance) < 0) {
          best = order;
          distance = d;
        }
      }
      total = total.add(distance);
      ids.add(best.orderId());
      current = best.destination();
      remaining.remove(best);
    }
    return new Suggestion(
        request.originId(), ids, total.setScale(3, RoundingMode.HALF_UP).toPlainString());
  }

  public RouteSnapshot create(Request request) {
    operator();
    var selected = validate(request);
    UUID id = UUID.randomUUID();
    var route =
        new RouteSnapshot(
            id,
            selected.get(0).storeId(),
            request.originId(),
            RouteStatus.PLANNED,
            List.copyOf(request.orderIds()),
            null,
            null,
            null);
    tx.executeWithoutResult(t -> store.create(route));
    return route;
  }

  public RouteSnapshot get(UUID id) {
    var actor = actors.requireActor();
    if (actor.role() == Role.STORE) throw new ContractException("FORBIDDEN");
    var route = store.get(id, false);
    if (actor.role() == Role.DRIVER && !Objects.equals(actor.driverId(), route.driverId()))
      throw new ContractException("NOT_FOUND");
    return route;
  }

  public RouteSnapshot getById(UUID id) {
    return store.get(id, false);
  }

  public RouteSnapshot reorder(UUID id, Reorder input) {
    operator();
    return tx.execute(
        t -> {
          var route = store.get(id, true);
          if (route.status() != RouteStatus.PLANNED && route.status() != RouteStatus.ASSIGNED)
            throw new ContractException("CONFLICT");
          if (input == null
              || input.orderedOrderIds() == null
              || input.orderedOrderIds().size() != route.orderIds().size()
              || new HashSet<>(input.orderedOrderIds()).size() != route.orderIds().size()
              || !new HashSet<>(input.orderedOrderIds()).equals(new HashSet<>(route.orderIds())))
            throw new ContractException("VALIDATION_ERROR");
          store.reorder(id, input.orderedOrderIds());
          return store.get(id, false);
        });
  }

  public RouteSnapshot assign(UUID id) {
    operator();
    tx.executeWithoutResult(
        t -> {
          var route = store.get(id, true);
          if (route.status() == RouteStatus.ASSIGNED) return;
          if (route.status() != RouteStatus.PLANNED) throw new ContractException("CONFLICT");
          BigDecimal capacity =
              route.orderIds().stream()
                  .map(orders::getById)
                  .map(OrderSnapshot::totalWeightKg)
                  .reduce(BigDecimal.ZERO, BigDecimal::add);
          var assignment = fleet.assign(new AssignmentRequest(id, route.originId(), capacity));
          store.assign(id, assignment);
        });
    reconcile(id);
    return store.get(id, false);
  }

  public void reconcile(UUID id) {
    var route = store.get(id, false);
    if (route.status() != RouteStatus.ASSIGNED) return;
    for (UUID orderId : route.orderIds()) {
      try {
        delivery.assignRoute(
            orderId,
            id,
            UUID.nameUUIDFromBytes(
                ("route-assignment:" + id + ":" + orderId).getBytes(StandardCharsets.UTF_8)));
        store.done(id, orderId);
      } catch (RuntimeException e) {
        throw new ContractException("DEPENDENCY_UNAVAILABLE");
      }
    }
  }

  @Scheduled(fixedDelayString = "${app.reconcile-delay-ms:30000}")
  public void retry() {
    for (var id : store.pendingRoutes())
      try {
        reconcile(id);
      } catch (RuntimeException ignored) {
      }
  }

  public void replayDevFakes() {
    for (UUID id : store.activeRoutes()) {
      var route = store.get(id, false);
      try {
        BigDecimal capacity =
            route.orderIds().stream()
                .map(orders::getById)
                .map(OrderSnapshot::totalWeightKg)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        fleet.assign(new AssignmentRequest(id, route.originId(), capacity));
        store.requeue(id);
        for (UUID orderId : route.orderIds()) {
          delivery.assignRoute(
              orderId,
              id,
              UUID.nameUUIDFromBytes(
                  ("route-assignment:" + id + ":" + orderId).getBytes(StandardCharsets.UTF_8)));
          store.done(id, orderId);
        }
      } catch (RuntimeException e) {
        throw new IllegalStateException("Cannot restore dev fake reservations", e);
      }
    }
  }

  public RouteSnapshot start(UUID id) {
    operator();
    return tx.execute(
        t -> {
          var route = store.get(id, true);
          if (route.status() == RouteStatus.IN_PROGRESS) return route;
          if (route.status() != RouteStatus.ASSIGNED
              || route.driverId() == null
              || route.vehicleId() == null
              || store.pending(id)) throw new ContractException("CONFLICT");
          store.start(id);
          return store.get(id, false);
        });
  }
}
