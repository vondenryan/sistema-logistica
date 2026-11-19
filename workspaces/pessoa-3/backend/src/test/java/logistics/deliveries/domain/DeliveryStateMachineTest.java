package logistics.deliveries.domain;

import static org.junit.jupiter.api.Assertions.*;

import contracts.v1.ContractException;
import contracts.v1.DeliverySnapshot;
import contracts.v1.DeliveryStatus;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DeliveryStateMachineTest {
  private UUID orderId;
  private UUID routeId;
  private Delivery delivery;

  @BeforeEach
  void setUp() {
    orderId = UUID.randomUUID();
    routeId = UUID.randomUUID();
    delivery = Delivery.create(orderId);
  }

  @Test
  @DisplayName("Delivery starts in PENDING status with null route and receiver")
  void testInitialState() {
    assertEquals(DeliveryStatus.PENDING, delivery.getStatus());
    assertNull(delivery.getRouteId());
    assertNull(delivery.getReceiverName());
    assertEquals(orderId, delivery.getOrderId());
    assertNotNull(delivery.getId());
    assertNotNull(delivery.getCreatedAt());
    assertNotNull(delivery.getUpdatedAt());
  }

  @Test
  @DisplayName("Can assign route from PENDING status")
  void testAssignRouteFromPending() {
    delivery.assignRoute(routeId);
    assertEquals(DeliveryStatus.ASSIGNED, delivery.getStatus());
    assertEquals(routeId, delivery.getRouteId());
  }

  @Test
  @DisplayName("Assign route is idempotent when called with same routeId")
  void testAssignRouteIdempotent() {
    delivery.assignRoute(routeId);
    assertDoesNotThrow(() -> delivery.assignRoute(routeId));
    assertEquals(DeliveryStatus.ASSIGNED, delivery.getStatus());
    assertEquals(routeId, delivery.getRouteId());
  }

  @Test
  @DisplayName("Can start transit from ASSIGNED status")
  void testStartTransitFromAssigned() {
    delivery.assignRoute(routeId);
    delivery.startTransit();
    assertEquals(DeliveryStatus.IN_TRANSIT, delivery.getStatus());
  }

  @Test
  @DisplayName("Start transit is idempotent")
  void testStartTransitIdempotent() {
    delivery.assignRoute(routeId);
    delivery.startTransit();
    assertDoesNotThrow(() -> delivery.startTransit());
    assertEquals(DeliveryStatus.IN_TRANSIT, delivery.getStatus());
  }

  @Test
  @DisplayName("Cannot start transit directly from PENDING")
  void testCannotStartTransitFromPending() {
    ContractException ex = assertThrows(ContractException.class, () -> delivery.startTransit());
    assertEquals("CONFLICT", ex.code());
  }

  @Test
  @DisplayName("Can register failed attempt from IN_TRANSIT with valid reason")
  void testFailAttempt() {
    delivery.assignRoute(routeId);
    delivery.startTransit();
    delivery.failAttempt("Destinatário não atendeu a campainha");
    assertEquals(DeliveryStatus.FAILED_ATTEMPT, delivery.getStatus());
  }

  @Test
  @DisplayName("Failed attempt requires a non-blank reason")
  void testFailAttemptRequiresReason() {
    delivery.assignRoute(routeId);
    delivery.startTransit();

    ContractException ex1 = assertThrows(ContractException.class, () -> delivery.failAttempt(null));
    assertEquals("VALIDATION_ERROR", ex1.code());

    ContractException ex2 = assertThrows(ContractException.class, () -> delivery.failAttempt("   "));
    assertEquals("VALIDATION_ERROR", ex2.code());

    ContractException ex3 =
        assertThrows(ContractException.class, () -> delivery.failAttempt("A".repeat(501)));
    assertEquals("VALIDATION_ERROR", ex3.code());
  }

  @Test
  @DisplayName("Can reassign route after FAILED_ATTEMPT (retry flow)")
  void testRetryAfterFailedAttempt() {
    delivery.assignRoute(routeId);
    delivery.startTransit();
    delivery.failAttempt("Endereço não localizado");
    assertEquals(DeliveryStatus.FAILED_ATTEMPT, delivery.getStatus());

    UUID nextRouteId = UUID.randomUUID();
    delivery.assignRoute(nextRouteId);
    assertEquals(DeliveryStatus.ASSIGNED, delivery.getStatus());
    assertEquals(nextRouteId, delivery.getRouteId());
  }

  @Test
  @DisplayName("Can complete delivery from IN_TRANSIT with receiver name")
  void testCompleteDelivery() {
    delivery.assignRoute(routeId);
    delivery.startTransit();
    delivery.complete("Maria Silva");

    assertEquals(DeliveryStatus.DELIVERED, delivery.getStatus());
    assertEquals("Maria Silva", delivery.getReceiverName());
  }

  @Test
  @DisplayName("Complete delivery requires non-blank receiver name")
  void testCompleteRequiresReceiverName() {
    delivery.assignRoute(routeId);
    delivery.startTransit();

    ContractException ex1 = assertThrows(ContractException.class, () -> delivery.complete(null));
    assertEquals("VALIDATION_ERROR", ex1.code());

    ContractException ex2 = assertThrows(ContractException.class, () -> delivery.complete("   "));
    assertEquals("VALIDATION_ERROR", ex2.code());

    ContractException ex3 =
        assertThrows(ContractException.class, () -> delivery.complete("A".repeat(151)));
    assertEquals("VALIDATION_ERROR", ex3.code());
  }

  @Test
  @DisplayName("Complete delivery is idempotent with same receiver name")
  void testCompleteIdempotentWithSameName() {
    delivery.assignRoute(routeId);
    delivery.startTransit();
    delivery.complete("Maria Silva");

    assertDoesNotThrow(() -> delivery.complete("Maria Silva"));
    assertEquals(DeliveryStatus.DELIVERED, delivery.getStatus());
    assertEquals("Maria Silva", delivery.getReceiverName());
  }

  @Test
  @DisplayName("Complete delivery throws CONFLICT when repeated with different receiver name")
  void testCompleteConflictWithDifferentName() {
    delivery.assignRoute(routeId);
    delivery.startTransit();
    delivery.complete("Maria Silva");

    ContractException ex =
        assertThrows(ContractException.class, () -> delivery.complete("Outro Nome"));
    assertEquals("CONFLICT", ex.code());
  }

  @Test
  @DisplayName("Cannot complete delivery if not IN_TRANSIT")
  void testCannotCompleteIfNotInTransit() {
    ContractException ex1 = assertThrows(ContractException.class, () -> delivery.complete("Carlos"));
    assertEquals("CONFLICT", ex1.code());

    delivery.assignRoute(routeId);
    ContractException ex2 = assertThrows(ContractException.class, () -> delivery.complete("Carlos"));
    assertEquals("CONFLICT", ex2.code());
  }

  @Test
  @DisplayName("DELIVERED is a terminal state and cannot transition")
  void testDeliveredIsTerminal() {
    delivery.assignRoute(routeId);
    delivery.startTransit();
    delivery.complete("Carlos");

    assertThrows(ContractException.class, () -> delivery.assignRoute(UUID.randomUUID()));
    assertThrows(ContractException.class, () -> delivery.startTransit());
    assertThrows(ContractException.class, () -> delivery.failAttempt("motivo"));
    assertThrows(ContractException.class, () -> delivery.cancel("motivo"));
  }

  @Test
  @DisplayName("Can cancel from PENDING, ASSIGNED, or FAILED_ATTEMPT")
  void testCancellation() {
    delivery.cancel("Pedido cancelado pelo cliente");
    assertEquals(DeliveryStatus.CANCELLED, delivery.getStatus());

    // Idempotent cancel
    assertDoesNotThrow(() -> delivery.cancel("novo motivo"));
    assertEquals(DeliveryStatus.CANCELLED, delivery.getStatus());
  }

  @Test
  @DisplayName("Cannot cancel delivery if IN_TRANSIT")
  void testCannotCancelInTransit() {
    delivery.assignRoute(routeId);
    delivery.startTransit();
    ContractException ex =
        assertThrows(ContractException.class, () -> delivery.cancel("Cancelar agora"));
    assertEquals("CONFLICT", ex.code());
  }

  @Test
  @DisplayName("toSnapshot produces an immutable DeliverySnapshot")
  void testToSnapshot() {
    delivery.assignRoute(routeId);
    DeliverySnapshot snapshot = delivery.toSnapshot();

    assertEquals(delivery.getId(), snapshot.deliveryId());
    assertEquals(orderId, snapshot.orderId());
    assertEquals(routeId, snapshot.routeId());
    assertEquals(DeliveryStatus.ASSIGNED, snapshot.status());
    assertNull(snapshot.receiverName());
    assertEquals(delivery.getUpdatedAt(), snapshot.updatedAt());
  }
}
