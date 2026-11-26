package logistics.notifications;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doThrow;

import contracts.v1.AddressSnapshot;
import contracts.v1.ContractException;
import contracts.v1.DeliverySnapshot;
import contracts.v1.DeliveryStatus;
import contracts.v1.NotificationEventType;
import contracts.v1.NotificationReceipt;
import contracts.v1.NotificationRequest;
import contracts.v1.OrderSnapshot;
import contracts.v1.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import logistics.deliveries.application.DeliveryService;
import logistics.fakes.FakeCurrentActorPort;
import logistics.fakes.FakeOrderSnapshotPort;
import logistics.fakes.FakeRouteSnapshotPort;
import logistics.notifications.application.NotificationService;
import logistics.notifications.domain.NotificationOutboxEntry;
import logistics.notifications.domain.NotificationOutboxRepository;
import logistics.notifications.domain.OutboxStatus;
import logistics.notifications.infrastructure.mail.InMemoryMailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class NotificationOutboxTest {

  @Autowired private NotificationService notificationService;
  @Autowired private NotificationOutboxRepository outboxRepository;
  @Autowired private InMemoryMailSender inMemoryMailSender;
  @Autowired private DeliveryService deliveryService;
  @MockitoSpyBean private FakeOrderSnapshotPort fakeOrders;
  @Autowired private FakeRouteSnapshotPort fakeRoutes;
  @Autowired private FakeCurrentActorPort fakeActor;

  @BeforeEach
  void setUp() {
    inMemoryMailSender.reset();
    fakeOrders.resetToCanonical();
    fakeRoutes.resetToCanonical();
    fakeActor.reset();
  }

  @Test
  void enqueueIsIdempotentAndRejectsConflictingPayload() {
    UUID eventId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-11-14T12:00:00.000Z");

    NotificationRequest req =
        new NotificationRequest(
            eventId,
            NotificationEventType.ORDER_CONFIRMED,
            FakeOrderSnapshotPort.CANONICAL_ORDER_ID,
            "cliente@example.invalid",
            occurredAt);

    NotificationReceipt first = notificationService.enqueue(req);
    NotificationReceipt second = notificationService.enqueue(req);

    assertTrue(first.accepted());
    assertEquals(first.notificationId(), second.notificationId());

    NotificationRequest conflicting =
        new NotificationRequest(
            eventId,
            NotificationEventType.ORDER_CONFIRMED,
            FakeOrderSnapshotPort.CANONICAL_ORDER_ID,
            "outro@example.invalid",
            occurredAt);

    ContractException ex =
        assertThrows(ContractException.class, () -> notificationService.enqueue(conflicting));
    assertEquals("IDEMPOTENCY_CONFLICT", ex.code());
  }

  @Test
  void enqueueRejectsInvalidEmail() {
    NotificationRequest invalid =
        new NotificationRequest(
            UUID.randomUUID(),
            NotificationEventType.ORDER_CONFIRMED,
            FakeOrderSnapshotPort.CANONICAL_ORDER_ID,
            "not-an-email",
            Instant.now());

    ContractException ex =
        assertThrows(ContractException.class, () -> notificationService.enqueue(invalid));
    assertEquals("VALIDATION_ERROR", ex.code());
  }

  @Test
  void processPendingHandlesSmtpFailureAndRetries() {
    UUID eventId = UUID.randomUUID();
    notificationService.enqueue(
        new NotificationRequest(
            eventId,
            NotificationEventType.DELIVERY_IN_TRANSIT,
            FakeOrderSnapshotPort.CANONICAL_ORDER_ID,
            "cliente@example.invalid",
            Instant.now()));

    // First attempt fails
    inMemoryMailSender.failNext(1);
    int sentFirst = notificationService.processPending(10);
    assertEquals(0, sentFirst);

    NotificationOutboxEntry entry =
        outboxRepository
            .findByEvent(eventId, NotificationEventType.DELIVERY_IN_TRANSIT)
            .orElseThrow();
    assertEquals(OutboxStatus.PENDING, entry.getStatus());
    assertEquals(1, entry.getAttempts());
    assertNotNull(entry.getLastError());

    // Simulate retry window reached
    NotificationOutboxEntry readyForRetry =
        new NotificationOutboxEntry(
            entry.getId(),
            entry.getEventId(),
            entry.getEventType(),
            entry.getOrderId(),
            entry.getRecipientEmail(),
            entry.getSubject(),
            entry.getBody(),
            entry.getPayloadHash(),
            entry.getStatus(),
            entry.getAttempts(),
            Instant.now().minusSeconds(1),
            entry.getLastError(),
            entry.getOccurredAt(),
            entry.getCreatedAt(),
            entry.getUpdatedAt());
    outboxRepository.save(readyForRetry);

    int sentRetry = notificationService.processPending(10);
    assertEquals(1, sentRetry);

    NotificationOutboxEntry completed =
        outboxRepository
            .findByEvent(eventId, NotificationEventType.DELIVERY_IN_TRANSIT)
            .orElseThrow();
    assertEquals(OutboxStatus.SENT, completed.getStatus());
    assertEquals(1, inMemoryMailSender.getSentMessages().size());
    assertEquals("Entrega em trânsito", inMemoryMailSender.getSentMessages().get(0).subject());
  }

  @Test
  void deliveryTransitionsEnqueueNotificationsWithoutBreakingOnSmtpFailureOrMissingEmail() {
    inMemoryMailSender.setAlwaysFail(true);

    fakeActor.asOperator();
    DeliverySnapshot created =
        deliveryService.ensureCreated(FakeOrderSnapshotPort.CANONICAL_ORDER_ID, UUID.randomUUID());
    deliveryService.assignRoute(
        FakeOrderSnapshotPort.CANONICAL_ORDER_ID,
        FakeRouteSnapshotPort.CANONICAL_ROUTE_ID,
        UUID.randomUUID());

    fakeActor.asDriver(FakeRouteSnapshotPort.CANONICAL_DRIVER_ID);
    DeliverySnapshot inTransit =
        deliveryService.updateStatus(created.deliveryId(), "IN_TRANSIT", null);
    assertEquals(DeliveryStatus.IN_TRANSIT, inTransit.status());

    DeliverySnapshot delivered =
        deliveryService.completeDelivery(created.deliveryId(), "Maria Silva");
    assertEquals(DeliveryStatus.DELIVERED, delivered.status());

    // Both transitions enqueued in outbox even though SMTP is failing
    List<NotificationOutboxEntry> entries =
        outboxRepository.findByOrderId(FakeOrderSnapshotPort.CANONICAL_ORDER_ID);
    assertEquals(2, entries.size());

    // Order without recipientEmail skips notification gracefully
    UUID noEmailOrderId = UUID.randomUUID();
    AddressSnapshot addr =
        new AddressSnapshot(
            "Rua Sem Email, 1",
            null,
            "Cidade",
            "SC",
            "00000-000",
            "BR",
            BigDecimal.ZERO,
            BigDecimal.ZERO);
    fakeOrders.register(
        new OrderSnapshot(
            noEmailOrderId,
            FakeOrderSnapshotPort.CANONICAL_STORE_ID,
            FakeOrderSnapshotPort.CANONICAL_ORIGIN_ID,
            OrderStatus.CONFIRMED,
            addr,
            addr,
            "Sem Email",
            null,
            "TRK-NOEMAIL-123",
            new BigDecimal("5.000"),
            new BigDecimal("15.00"),
            "BRL"));

    fakeActor.asOperator();
    DeliverySnapshot noEmailDelivery =
        deliveryService.ensureCreated(noEmailOrderId, UUID.randomUUID());
    assertEquals(DeliveryStatus.PENDING, noEmailDelivery.status());
    notificationService.recordIntent(
        NotificationEventType.ORDER_CONFIRMED, noEmailOrderId, UUID.randomUUID(), Instant.now());
    notificationService.processPending(10);
    assertEquals(
        OutboxStatus.SKIPPED,
        outboxRepository.findByOrderId(noEmailOrderId).getFirst().getStatus());
    assertTrue(inMemoryMailSender.getSentMessages().isEmpty());
  }

  @Test
  void transitionPersistsIntentWhenOrderLookupFailsAndLaterSendsIt() {
    UUID orderId = FakeOrderSnapshotPort.CANONICAL_ORDER_ID;
    fakeActor.asOperator();
    var created = deliveryService.ensureCreated(orderId, UUID.randomUUID());
    deliveryService.assignRoute(
        orderId, FakeRouteSnapshotPort.CANONICAL_ROUTE_ID, UUID.randomUUID());
    var order = fakeOrders.getById(orderId);
    doThrow(new ContractException("DEPENDENCY_UNAVAILABLE"))
        .doReturn(order)
        .when(fakeOrders)
        .getById(orderId);

    assertEquals(
        DeliveryStatus.IN_TRANSIT,
        deliveryService.updateStatus(created.deliveryId(), "IN_TRANSIT", null).status());
    var pending = outboxRepository.findByOrderId(orderId).getFirst();
    var deliveryEvent = deliveryService.getEvents(created.deliveryId()).getLast();
    assertEquals(deliveryEvent.id(), pending.getEventId());
    assertNull(pending.getRecipientEmail());

    assertEquals(0, notificationService.processPending(10));
    var retry = outboxRepository.findByOrderId(orderId).getFirst();
    assertEquals(OutboxStatus.PENDING, retry.getStatus());
    assertEquals(1, retry.getAttempts());
    assertTrue(inMemoryMailSender.getSentMessages().isEmpty());
    assertEquals(0, notificationService.processPending(10));

    // Move only the persisted retry time forward without sleeping.
    outboxRepository.save(
        new NotificationOutboxEntry(
            retry.getId(),
            retry.getEventId(),
            retry.getEventType(),
            retry.getOrderId(),
            retry.getRecipientEmail(),
            retry.getSubject(),
            retry.getBody(),
            retry.getPayloadHash(),
            retry.getStatus(),
            retry.getAttempts(),
            Instant.now().minusSeconds(1),
            retry.getLastError(),
            retry.getOccurredAt(),
            retry.getCreatedAt(),
            retry.getUpdatedAt()));
    assertEquals(1, notificationService.processPending(10));
    var sent = outboxRepository.findByOrderId(orderId).getFirst();
    assertEquals(pending.getId(), sent.getId());
    assertEquals(OutboxStatus.SENT, sent.getStatus());
    assertEquals(1, inMemoryMailSender.getSentMessages().size());
  }
}
