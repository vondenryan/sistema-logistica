package logistics.notifications;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import contracts.v1.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import logistics.documents.application.DocumentService;
import logistics.documents.domain.SimulatedDocumentRepository;
import logistics.fakes.FakeCurrentActorPort;
import logistics.fakes.FakeOrderSnapshotPort;
import logistics.notifications.application.MailSenderPort;
import logistics.notifications.application.NotificationService;
import logistics.notifications.domain.NotificationOutboxEntry;
import logistics.notifications.domain.NotificationOutboxRepository;
import logistics.notifications.domain.OutboxStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest
class Sprint3ConcurrencyTest {
  private static final UUID ORDER = FakeOrderSnapshotPort.CANONICAL_ORDER_ID;

  @Autowired private NotificationService notifications;
  @Autowired private DocumentService documents;
  @Autowired private FakeCurrentActorPort actor;
  @Autowired private JdbcTemplate jdbc;
  @MockitoSpyBean private NotificationOutboxRepository outbox;
  @MockitoSpyBean private SimulatedDocumentRepository documentRepository;
  @MockitoBean private MailSenderPort mail;

  private final UUID eventId = UUID.randomUUID();
  private final UUID secondEventId = UUID.randomUUID();
  private final Instant occurredAt = Instant.parse("2026-11-26T15:00:00Z");

  @AfterEach
  void cleanUp() {
    jdbc.update(
        "DELETE FROM notifications.notification_outbox WHERE event_id IN (?, ?)",
        eventId,
        secondEventId);
    jdbc.update(
        "DELETE FROM documents.simulated_documents WHERE order_id = ? AND document_type ="
            + " 'CTE_SIMULATED'",
        ORDER);
  }

  @Test
  void concurrentEquivalentNotificationsReturnOnePersistedId() throws Exception {
    var barrier = new CyclicBarrier(2);
    doAnswer(
            call -> {
              barrier.await(5, TimeUnit.SECONDS);
              return call.callRealMethod();
            })
        .when(outbox)
        .insertIfAbsent(any());

    var results =
        concurrently(
            () -> notifications.enqueue(request("one@example.invalid")),
            () -> notifications.enqueue(request("one@example.invalid")));
    assertEquals(results.getFirst(), results.getLast());
    assertEquals(
        1,
        outbox.findByOrderId(ORDER).stream().filter(e -> eventId.equals(e.getEventId())).count());
  }

  @Test
  void concurrentDifferentPayloadsReturnOneIdAndOneConflict() throws Exception {
    var barrier = new CyclicBarrier(2);
    doAnswer(
            call -> {
              barrier.await(5, TimeUnit.SECONDS);
              return call.callRealMethod();
            })
        .when(outbox)
        .insertIfAbsent(any());

    var results =
        concurrently(
            () -> enqueueOutcome("one@example.invalid"),
            () -> enqueueOutcome("two@example.invalid"));
    assertEquals(1, results.stream().filter("IDEMPOTENCY_CONFLICT"::equals).count());
    var stored = outbox.findByEvent(eventId, NotificationEventType.ORDER_CONFIRMED).orElseThrow();
    assertTrue(results.contains(stored.getId().toString()));
  }

  @Test
  void concurrentPdfRequestsReturnOnePersistedDocument() throws Exception {
    var barrier = new CyclicBarrier(2);
    doAnswer(
            call -> {
              barrier.await(5, TimeUnit.SECONDS);
              return call.callRealMethod();
            })
        .when(documentRepository)
        .insertIfAbsent(any());

    var results = concurrently(this::createDocument, this::createDocument);
    assertEquals(results.getFirst().receipt(), results.getLast().receipt());
    assertEquals(1, results.stream().filter(DocumentService.GenerateOutcome::createdNew).count());
    var stored =
        documentRepository.findByOrderAndType(ORDER, DocumentType.CTE_SIMULATED).orElseThrow();
    assertEquals(stored.toReceipt(), results.getFirst().receipt());
  }

  @Test
  void secondProcessorSkipsRowReservedDuringSend() throws Exception {
    notifications.enqueue(request("one@example.invalid"));
    var sending = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    doAnswer(
            call -> {
              sending.countDown();
              assertTrue(release.await(10, TimeUnit.SECONDS));
              return null;
            })
        .when(mail)
        .send(any(), any(), any());

    try (var executor = Executors.newFixedThreadPool(2)) {
      var first = executor.submit(() -> notifications.processPending(1));
      try {
        assertTrue(sending.await(5, TimeUnit.SECONDS));
        var second = executor.submit(() -> notifications.processPending(1));
        assertEquals(0, second.get(5, TimeUnit.SECONDS));
      } finally {
        release.countDown();
      }
      assertEquals(1, first.get(5, TimeUnit.SECONDS));
    }
    org.mockito.Mockito.verify(mail).send(any(), any(), any());
  }

  @Test
  void failedSecondWriteDoesNotRollBackFirstSend() {
    var first = notifications.enqueue(request("one@example.invalid"));
    var second =
        notifications.enqueue(
            new NotificationRequest(
                secondEventId,
                NotificationEventType.ORDER_CONFIRMED,
                ORDER,
                "two@example.invalid",
                occurredAt));
    // Ensure the first row is selected before the second regardless of timestamp precision.
    jdbc.update(
        "UPDATE notifications.notification_outbox SET next_retry_at = ? WHERE id = ?",
        java.sql.Timestamp.from(occurredAt),
        first.notificationId());
    doAnswer(
            call -> {
              NotificationOutboxEntry entry = call.getArgument(0);
              if (entry.getId().equals(second.notificationId())) {
                throw new DataAccessResourceFailureException(
                    "Simulated second result write failure");
              }
              return call.callRealMethod();
            })
        .when(outbox)
        .save(any());

    assertThrows(DataAccessResourceFailureException.class, () -> notifications.processPending(2));
    var sends = inOrder(mail);
    sends.verify(mail).send(eq("one@example.invalid"), any(), any());
    sends.verify(mail).send(eq("two@example.invalid"), any(), any());
    assertEquals(
        OutboxStatus.SENT,
        outbox
            .findByEvent(eventId, NotificationEventType.ORDER_CONFIRMED)
            .orElseThrow()
            .getStatus());
    assertEquals(
        OutboxStatus.PENDING,
        outbox
            .findByEvent(secondEventId, NotificationEventType.ORDER_CONFIRMED)
            .orElseThrow()
            .getStatus());

    doCallRealMethod().when(outbox).save(any());
    assertEquals(1, notifications.processPending(2));
    verify(mail, times(1)).send(eq("one@example.invalid"), any(), any());
    verify(mail, times(2)).send(eq("two@example.invalid"), any(), any());
    assertEquals(
        OutboxStatus.SENT,
        outbox
            .findByEvent(secondEventId, NotificationEventType.ORDER_CONFIRMED)
            .orElseThrow()
            .getStatus());
  }

  private NotificationRequest request(String email) {
    return new NotificationRequest(
        eventId, NotificationEventType.ORDER_CONFIRMED, ORDER, email, occurredAt);
  }

  private String enqueueOutcome(String email) {
    try {
      return notifications.enqueue(request(email)).notificationId().toString();
    } catch (ContractException ex) {
      return ex.code();
    }
  }

  private DocumentService.GenerateOutcome createDocument() {
    actor.asOperator();
    return documents.createOrGetForApi(ORDER, DocumentType.CTE_SIMULATED);
  }

  private static <T> List<T> concurrently(Callable<T> first, Callable<T> second) throws Exception {
    try (var executor = Executors.newFixedThreadPool(2)) {
      var a = executor.submit(first);
      var b = executor.submit(second);
      return List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
    }
  }
}
