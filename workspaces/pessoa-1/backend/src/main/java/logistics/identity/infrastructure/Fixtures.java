package logistics.identity.infrastructure;

import contracts.v1.*;
import java.math.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"dev", "test"})
public class Fixtures
    implements PartnerLookupPort,
        FleetAssignmentPort,
        FleetReleasePort,
        DeliveryCommandPort,
        DeliveryStatusPort,
        NotificationRequestPort,
        DocumentRequestPort {
  static final UUID STORE = UUID.fromString("22222222-2222-4222-8222-222222222222"),
      OTHER_STORE = UUID.fromString("22222222-2222-4222-8222-222222222223"),
      ORIGIN = UUID.fromString("11111111-1111-4111-8111-111111111111"),
      OTHER_ORIGIN = UUID.fromString("11111111-1111-4111-8111-111111111112"),
      DRIVER = UUID.fromString("55555555-5555-4555-8555-555555555555"),
      VEHICLE = UUID.fromString("66666666-6666-4666-8666-666666666666");
  static final AddressSnapshot ADDRESS =
      new AddressSnapshot(
          "Rua Exemplo, 100",
          null,
          "Cidade Fictícia",
          "SC",
          "00000-000",
          "BR",
          new BigDecimal("-26.480000"),
          new BigDecimal("-49.070000"));
  private final ObjectProvider<OrderSnapshotPort> orders;
  private final ObjectProvider<RouteSnapshotPort> routes;
  private volatile boolean unavailable;
  private volatile boolean deliveryUnavailable;

  public void setDeliveryUnavailable(boolean value) {
    deliveryUnavailable = value;
  }

  Fixtures(ObjectProvider<OrderSnapshotPort> orders, ObjectProvider<RouteSnapshotPort> routes) {
    this.orders = orders;
    this.routes = routes;
  }

  public int deliveryCount() {
    return deliveries.size();
  }

  public void setUnavailable(boolean value) {
    unavailable = value;
  }

  private void available() {
    if (unavailable) throw new ContractException("DEPENDENCY_UNAVAILABLE");
  }

  private static final Instant FIXED = Instant.parse("2026-11-14T12:00:00Z");
  private final Map<String, NotificationRequest> notifications = new ConcurrentHashMap<>();
  private final Map<UUID, DeliverySnapshot> deliveries = new ConcurrentHashMap<>();
  private final Map<UUID, AssignmentRequest> requests = new ConcurrentHashMap<>();
  private final Map<UUID, AssignmentSnapshot> assignments = new ConcurrentHashMap<>();
  private final Set<UUID> released = ConcurrentHashMap.newKeySet();

  static UUID id(String name) {
    return UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  public OriginSnapshot getOrigin(UUID id) {
    available();
    if (id.equals(UUID.fromString("11111111-1111-4111-8111-111111111113")))
      return new OriginSnapshot(id, OriginType.STORE, STORE, ADDRESS, false);
    if (id.equals(ORIGIN)) return new OriginSnapshot(id, OriginType.STORE, STORE, ADDRESS, true);
    if (id.equals(OTHER_ORIGIN))
      return new OriginSnapshot(id, OriginType.DISTRIBUTION_CENTER, OTHER_STORE, ADDRESS, true);
    throw new ContractException("NOT_FOUND");
  }

  public StoreSnapshot getStore(UUID id) {
    if (id.equals(STORE) || id.equals(OTHER_STORE))
      return new StoreSnapshot(id, true, "Loja fictícia");
    throw new ContractException("NOT_FOUND");
  }

  public synchronized AssignmentSnapshot assign(AssignmentRequest r) {
    if (r.requiredCapacityKg() == null || r.requiredCapacityKg().signum() <= 0)
      throw new ContractException("VALIDATION_ERROR");
    getOrigin(r.originId());
    if (requests.containsKey(r.routeId()) && !requests.get(r.routeId()).equals(r))
      throw new ContractException("IDEMPOTENCY_CONFLICT");
    RouteSnapshot route = routes.getObject().getById(r.routeId());
    if (!route.originId().equals(r.originId())
        || (route.status() != RouteStatus.PLANNED
            && route.status() != RouteStatus.ASSIGNED
            && route.status() != RouteStatus.IN_PROGRESS))
      throw new ContractException("PRECONDITION_FAILED");
    BigDecimal total = BigDecimal.ZERO;
    for (UUID orderId : route.orderIds()) {
      OrderSnapshot order = orders.getObject().getById(orderId);
      if (order.status() != OrderStatus.CONFIRMED
          || !order.originId().equals(route.originId())
          || !order.storeId().equals(route.storeId()))
        throw new ContractException("PRECONDITION_FAILED");
      total = total.add(order.totalWeightKg());
    }
    if (total.compareTo(r.requiredCapacityKg()) != 0)
      throw new ContractException("PRECONDITION_FAILED");
    if (assignments.values().stream()
        .anyMatch(a -> !a.routeId().equals(r.routeId()) && !released.contains(a.routeId())))
      throw new ContractException("NO_ELIGIBLE_RESOURCE");
    requests.put(r.routeId(), r);
    return assignments.computeIfAbsent(
        r.routeId(),
        x -> new AssignmentSnapshot(id("assignment" + x), x, DRIVER, VEHICLE, null, FleetType.OWN));
  }

  public synchronized ReleaseReceipt release(ReleaseRequest r) {
    if (!assignments.containsKey(r.routeId())) throw new ContractException("NOT_FOUND");
    if (r.reason() != ReleaseReason.ROUTE_COMPLETED)
      throw new ContractException("PRECONDITION_FAILED");
    return new ReleaseReceipt(r.routeId(), released.add(r.routeId()));
  }

  public DeliverySnapshot ensureCreated(UUID orderId, UUID eventId) {
    available();
    if (deliveryUnavailable) throw new ContractException("DEPENDENCY_UNAVAILABLE");
    if (orders.getObject().getById(orderId).status() != OrderStatus.CONFIRMED)
      throw new ContractException("PRECONDITION_FAILED");
    return deliveries.computeIfAbsent(
        orderId,
        x ->
            new DeliverySnapshot(id("delivery" + x), x, null, DeliveryStatus.PENDING, FIXED, null));
  }

  public synchronized DeliverySnapshot assignRoute(UUID orderId, UUID routeId, UUID eventId) {
    available();
    if (deliveryUnavailable) throw new ContractException("DEPENDENCY_UNAVAILABLE");
    RouteSnapshot route = routes.getObject().getById(routeId);
    OrderSnapshot order = orders.getObject().getById(orderId);
    if ((route.status() != RouteStatus.ASSIGNED && route.status() != RouteStatus.IN_PROGRESS)
        || !route.orderIds().contains(orderId)
        || order.status() != OrderStatus.CONFIRMED
        || !route.originId().equals(order.originId())
        || !route.storeId().equals(order.storeId()))
      throw new ContractException("PRECONDITION_FAILED");
    DeliverySnapshot d = getByOrderId(orderId);
    if (d.routeId() != null && !d.routeId().equals(routeId))
      throw new ContractException("CONFLICT");
    if (d.routeId() != null) return d;
    var result =
        new DeliverySnapshot(
            d.deliveryId(), orderId, routeId, DeliveryStatus.ASSIGNED, FIXED, null);
    deliveries.put(orderId, result);
    return result;
  }

  public DeliverySnapshot getByOrderId(UUID id) {
    var d = deliveries.get(id);
    if (d == null) throw new ContractException("NOT_FOUND");
    return d;
  }

  public synchronized NotificationReceipt enqueue(NotificationRequest r) {
    available();
    if (r.to() == null
        || !r.to().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
        || r.to().length() > 254) throw new ContractException("VALIDATION_ERROR");
    String key = r.eventId() + ":" + r.eventType();
    var old = notifications.putIfAbsent(key, r);
    if (old != null && !old.equals(r)) throw new ContractException("IDEMPOTENCY_CONFLICT");
    return new NotificationReceipt(id("notification" + r.eventId() + r.eventType()), true);
  }

  public DocumentReceipt ensureGenerated(DocumentRequest r) {
    available();
    if (orders.getObject().getById(r.orderId()).freightAmount() == null)
      throw new ContractException("PRECONDITION_FAILED");
    return new DocumentReceipt(
        id("document" + r.orderId() + r.documentType()),
        r.orderId(),
        r.documentType(),
        DocumentStatus.GENERATED);
  }
}
