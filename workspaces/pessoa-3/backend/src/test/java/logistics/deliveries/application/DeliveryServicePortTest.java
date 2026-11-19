package logistics.deliveries.application;

import static org.junit.jupiter.api.Assertions.*;

import contracts.v1.AddressSnapshot;
import contracts.v1.ContractException;
import contracts.v1.DeliverySnapshot;
import contracts.v1.DeliveryStatus;
import contracts.v1.OrderSnapshot;
import contracts.v1.OrderStatus;
import contracts.v1.RouteSnapshot;
import contracts.v1.RouteStatus;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import logistics.deliveries.domain.Delivery;
import logistics.deliveries.domain.DeliveryEvent;
import logistics.deliveries.domain.DeliveryRepository;
import logistics.fakes.FakeCurrentActorPort;
import logistics.fakes.FakeOrderSnapshotPort;
import logistics.fakes.FakeRouteSnapshotPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DeliveryServicePortTest {
  private InMemoryDeliveryRepository deliveryRepository;
  private FakeOrderSnapshotPort fakeOrders;
  private FakeRouteSnapshotPort fakeRoutes;
  private FakeCurrentActorPort fakeActor;
  private DeliveryService deliveryService;

  private UUID canonicalOrderId;
  private UUID canonicalRouteId;

  @BeforeEach
  void setUp() {
    deliveryRepository = new InMemoryDeliveryRepository();
    fakeOrders = new FakeOrderSnapshotPort();
    fakeRoutes = new FakeRouteSnapshotPort();
    fakeActor = new FakeCurrentActorPort();

    deliveryService =
        new DeliveryService(deliveryRepository, fakeOrders, fakeRoutes, fakeActor);

    canonicalOrderId = FakeOrderSnapshotPort.CANONICAL_ORDER_ID;
    canonicalRouteId = FakeRouteSnapshotPort.CANONICAL_ROUTE_ID;
  }

  @Test
  @DisplayName("ensureCreated creates a new PENDING delivery for a CONFIRMED order")
  void testEnsureCreatedSuccess() {
    UUID eventId = UUID.randomUUID();
    DeliverySnapshot snapshot = deliveryService.ensureCreated(canonicalOrderId, eventId);

    assertNotNull(snapshot);
    assertEquals(canonicalOrderId, snapshot.orderId());
    assertEquals(DeliveryStatus.PENDING, snapshot.status());
    assertNull(snapshot.routeId());

    assertEquals(1, deliveryRepository.events.size());
    DeliveryEvent event = deliveryRepository.events.get(0);
    assertEquals(DeliveryStatus.PENDING, event.toStatus());
    assertEquals(eventId, event.id());
  }

  @Test
  @DisplayName("ensureCreated is idempotent and does not create duplicate deliveries")
  void testEnsureCreatedIdempotent() {
    DeliverySnapshot first = deliveryService.ensureCreated(canonicalOrderId, UUID.randomUUID());
    DeliverySnapshot second = deliveryService.ensureCreated(canonicalOrderId, UUID.randomUUID());

    assertEquals(first.deliveryId(), second.deliveryId());
    assertEquals(first.orderId(), second.orderId());
    assertEquals(1, deliveryRepository.deliveries.size());
  }

  @Test
  @DisplayName("ensureCreated throws PRECONDITION_FAILED if order is in DRAFT status")
  void testEnsureCreatedFailsIfDraft() {
    UUID draftOrderId = UUID.randomUUID();
    OrderSnapshot draftOrder =
        new OrderSnapshot(
            draftOrderId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            OrderStatus.DRAFT,
            new AddressSnapshot(
                "Rua", null, "Cidade", "SC", null, "BR", BigDecimal.ZERO, BigDecimal.ZERO),
            new AddressSnapshot(
                "Rua", null, "Cidade", "SC", null, "BR", BigDecimal.ZERO, BigDecimal.ZERO),
            "Cliente",
            null,
            null,
            new BigDecimal("10.000"),
            null,
            "BRL");
    fakeOrders.register(draftOrder);

    ContractException ex =
        assertThrows(
            ContractException.class,
            () -> deliveryService.ensureCreated(draftOrderId, UUID.randomUUID()));
    assertEquals("PRECONDITION_FAILED", ex.code());
  }

  @Test
  @DisplayName("ensureCreated throws NOT_FOUND if order does not exist")
  void testEnsureCreatedFailsIfOrderNotFound() {
    UUID unknownId = UUID.randomUUID();
    ContractException ex =
        assertThrows(
            ContractException.class,
            () -> deliveryService.ensureCreated(unknownId, UUID.randomUUID()));
    assertEquals("NOT_FOUND", ex.code());
  }

  @Test
  @DisplayName("assignRoute assigns route to an existing PENDING delivery")
  void testAssignRouteSuccess() {
    deliveryService.ensureCreated(canonicalOrderId, UUID.randomUUID());

    UUID eventId = UUID.randomUUID();
    DeliverySnapshot snapshot =
        deliveryService.assignRoute(canonicalOrderId, canonicalRouteId, eventId);

    assertEquals(DeliveryStatus.ASSIGNED, snapshot.status());
    assertEquals(canonicalRouteId, snapshot.routeId());

    assertEquals(2, deliveryRepository.events.size());
    DeliveryEvent assignEvent = deliveryRepository.events.get(1);
    assertEquals(DeliveryStatus.ASSIGNED, assignEvent.toStatus());
    assertEquals(eventId, assignEvent.id());
  }

  @Test
  @DisplayName("assignRoute is idempotent when called again with same route")
  void testAssignRouteIdempotent() {
    deliveryService.ensureCreated(canonicalOrderId, UUID.randomUUID());
    DeliverySnapshot first =
        deliveryService.assignRoute(canonicalOrderId, canonicalRouteId, UUID.randomUUID());
    DeliverySnapshot second =
        deliveryService.assignRoute(canonicalOrderId, canonicalRouteId, UUID.randomUUID());

    assertEquals(first.deliveryId(), second.deliveryId());
    assertEquals(first.routeId(), second.routeId());
    assertEquals(DeliveryStatus.ASSIGNED, second.status());
  }

  @Test
  @DisplayName("assignRoute throws CONFLICT if route does not contain the orderId")
  void testAssignRouteConflictIfNotInRoute() {
    deliveryService.ensureCreated(canonicalOrderId, UUID.randomUUID());

    UUID otherRouteId = UUID.randomUUID();
    RouteSnapshot otherRoute =
        new RouteSnapshot(
            otherRouteId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            RouteStatus.ASSIGNED,
            List.of(UUID.randomUUID()), // Does not contain canonicalOrderId
            UUID.randomUUID(),
            UUID.randomUUID(),
            null);
    fakeRoutes.register(otherRoute);

    ContractException ex =
        assertThrows(
            ContractException.class,
            () ->
                deliveryService.assignRoute(
                    canonicalOrderId, otherRouteId, UUID.randomUUID()));
    assertEquals("CONFLICT", ex.code());
  }

  @Test
  @DisplayName("getByOrderId returns snapshot for existing delivery and NOT_FOUND for unknown")
  void testGetByOrderId() {
    deliveryService.ensureCreated(canonicalOrderId, UUID.randomUUID());

    DeliverySnapshot snapshot = deliveryService.getByOrderId(canonicalOrderId);
    assertNotNull(snapshot);
    assertEquals(canonicalOrderId, snapshot.orderId());

    UUID unknown = UUID.randomUUID();
    ContractException ex =
        assertThrows(ContractException.class, () -> deliveryService.getByOrderId(unknown));
    assertEquals("NOT_FOUND", ex.code());
  }

  // In-memory fake repository for fast isolated unit tests
  private static class InMemoryDeliveryRepository implements DeliveryRepository {
    final Map<UUID, Delivery> deliveries = new HashMap<>();
    final Map<UUID, Delivery> byOrderId = new HashMap<>();
    final List<DeliveryEvent> events = new ArrayList<>();

    @Override
    public void save(Delivery delivery) {
      deliveries.put(delivery.getId(), delivery);
      byOrderId.put(delivery.getOrderId(), delivery);
    }

    @Override
    public Optional<Delivery> findById(UUID id) {
      return Optional.ofNullable(deliveries.get(id));
    }

    @Override
    public Optional<Delivery> findByOrderId(UUID orderId) {
      return Optional.ofNullable(byOrderId.get(orderId));
    }

    @Override
    public List<Delivery> findByRouteId(UUID routeId) {
      return deliveries.values().stream()
          .filter(d -> routeId.equals(d.getRouteId()))
          .toList();
    }

    @Override
    public List<Delivery> findByRouteIds(List<UUID> routeIds, int offset, int limit) {
      return deliveries.values().stream()
          .filter(d -> routeIds.contains(d.getRouteId()))
          .skip(offset)
          .limit(limit)
          .toList();
    }

    @Override
    public void recordEvent(DeliveryEvent event) {
      events.add(event);
    }

    @Override
    public List<DeliveryEvent> findEventsByDeliveryId(UUID deliveryId) {
      return events.stream().filter(e -> deliveryId.equals(e.deliveryId())).toList();
    }
  }
}
