package logistics.deliveries.application;

import static org.junit.jupiter.api.Assertions.*;

import contracts.v1.ContractException;
import contracts.v1.DeliverySnapshot;
import contracts.v1.DeliveryStatus;
import contracts.v1.RouteSnapshot;
import contracts.v1.RouteStatus;
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

class DeliveryAuthorizationTest {
  private InMemoryDeliveryRepo repository;
  private FakeOrderSnapshotPort fakeOrders;
  private FakeRouteSnapshotPort fakeRoutes;
  private FakeCurrentActorPort fakeActor;
  private DeliveryService service;

  private UUID orderId;
  private UUID deliveryId;
  private UUID driverId;
  private UUID otherDriverId;
  private UUID routeId;

  @BeforeEach
  void setUp() {
    repository = new InMemoryDeliveryRepo();
    fakeOrders = new FakeOrderSnapshotPort();
    fakeRoutes = new FakeRouteSnapshotPort();
    fakeActor = new FakeCurrentActorPort();

    service = new DeliveryService(repository, fakeOrders, fakeRoutes, fakeActor);

    orderId = FakeOrderSnapshotPort.CANONICAL_ORDER_ID;
    driverId = FakeCurrentActorPort.DEFAULT_DRIVER_ID;
    otherDriverId = UUID.randomUUID();
    routeId = FakeRouteSnapshotPort.CANONICAL_ROUTE_ID;

    // Create delivery and assign route
    fakeActor.asOperator();
    DeliverySnapshot created = service.ensureCreated(orderId, UUID.randomUUID());
    deliveryId = created.deliveryId();
    service.assignRoute(orderId, routeId, UUID.randomUUID());
  }

  @Test
  @DisplayName("Assigned driver can transition delivery to IN_TRANSIT")
  void testAssignedDriverCanUpdate() {
    fakeActor.asDriver(driverId);
    DeliverySnapshot updated = service.updateStatus(deliveryId, "IN_TRANSIT", null);
    assertEquals(DeliveryStatus.IN_TRANSIT, updated.status());
  }

  @Test
  @DisplayName("Different driver cannot update delivery status (FORBIDDEN)")
  void testOtherDriverForbidden() {
    fakeActor.asDriver(otherDriverId);
    ContractException ex =
        assertThrows(
            ContractException.class,
            () -> service.updateStatus(deliveryId, "IN_TRANSIT", null));
    assertEquals("FORBIDDEN", ex.code());
  }

  @Test
  @DisplayName("Operator can update delivery status for any driver")
  void testOperatorCanUpdate() {
    fakeActor.asOperator();
    DeliverySnapshot updated = service.updateStatus(deliveryId, "IN_TRANSIT", null);
    assertEquals(DeliveryStatus.IN_TRANSIT, updated.status());
  }

  @Test
  @DisplayName("Admin can update delivery status for any driver")
  void testAdminCanUpdate() {
    fakeActor.asAdmin();
    DeliverySnapshot updated = service.updateStatus(deliveryId, "IN_TRANSIT", null);
    assertEquals(DeliveryStatus.IN_TRANSIT, updated.status());
  }

  @Test
  @DisplayName("Store role is FORBIDDEN from updating delivery status")
  void testStoreRoleForbidden() {
    fakeActor.asStore(UUID.randomUUID());
    ContractException ex =
        assertThrows(
            ContractException.class,
            () -> service.updateStatus(deliveryId, "IN_TRANSIT", null));
    assertEquals("FORBIDDEN", ex.code());
  }

  @Test
  @DisplayName("Unauthenticated request throws UNAUTHENTICATED")
  void testUnauthenticatedThrows() {
    fakeActor.unauthenticated();
    ContractException ex =
        assertThrows(
            ContractException.class,
            () -> service.updateStatus(deliveryId, "IN_TRANSIT", null));
    assertEquals("UNAUTHENTICATED", ex.code());
  }

  @Test
  @DisplayName("Assigned driver can complete delivery, but other driver cannot")
  void testCompleteAuthorization() {
    fakeActor.asDriver(driverId);
    service.updateStatus(deliveryId, "IN_TRANSIT", null);

    // Other driver cannot complete
    fakeActor.asDriver(otherDriverId);
    ContractException ex =
        assertThrows(
            ContractException.class,
            () -> service.completeDelivery(deliveryId, "Carlos"));
    assertEquals("FORBIDDEN", ex.code());

    // Assigned driver can complete
    fakeActor.asDriver(driverId);
    DeliverySnapshot completed = service.completeDelivery(deliveryId, "Carlos");
    assertEquals(DeliveryStatus.DELIVERED, completed.status());
  }

  private static class InMemoryDeliveryRepo implements DeliveryRepository {
    final Map<UUID, Delivery> map = new HashMap<>();
    final Map<UUID, Delivery> byOrder = new HashMap<>();
    final List<DeliveryEvent> events = new ArrayList<>();

    @Override
    public void save(Delivery d) {
      map.put(d.getId(), d);
      byOrder.put(d.getOrderId(), d);
    }

    @Override
    public Optional<Delivery> findById(UUID id) {
      return Optional.ofNullable(map.get(id));
    }

    @Override
    public Optional<Delivery> findByOrderId(UUID oId) {
      return Optional.ofNullable(byOrder.get(oId));
    }

    @Override
    public List<Delivery> findByRouteId(UUID rId) {
      return map.values().stream().filter(d -> rId.equals(d.getRouteId())).toList();
    }

    @Override
    public List<Delivery> findByRouteIds(List<UUID> rIds, int off, int lim) {
      return map.values().stream()
          .filter(d -> rIds.contains(d.getRouteId()))
          .skip(off)
          .limit(lim)
          .toList();
    }

    @Override
    public void recordEvent(DeliveryEvent e) {
      events.add(e);
    }

    @Override
    public List<DeliveryEvent> findEventsByDeliveryId(UUID dId) {
      return events.stream().filter(e -> dId.equals(e.deliveryId())).toList();
    }
  }
}
