package logistics.notifications.application;

import contracts.v1.ContractException;
import contracts.v1.NotificationEventType;
import contracts.v1.NotificationReceipt;
import contracts.v1.NotificationRequest;
import contracts.v1.NotificationRequestPort;
import contracts.v1.OrderSnapshot;
import contracts.v1.OrderSnapshotPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import logistics.notifications.domain.NotificationOutboxEntry;
import logistics.notifications.domain.NotificationOutboxRepository;
import logistics.notifications.domain.OutboxStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class NotificationService implements NotificationRequestPort {
  private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
  private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

  private final NotificationOutboxRepository outboxRepository;
  private final EmailTemplateRenderer templateRenderer;
  private final MailSenderPort mailSender;
  private final OrderSnapshotPort orderSnapshotPort;
  private final Clock clock;
  private final TransactionTemplate messageTransaction;
  private final int maxAttempts;
  private final long baseRetrySeconds;

  @Autowired
  public NotificationService(
      NotificationOutboxRepository outboxRepository,
      EmailTemplateRenderer templateRenderer,
      MailSenderPort mailSender,
      OrderSnapshotPort orderSnapshotPort,
      PlatformTransactionManager transactionManager,
      @Value("${notifications.outbox.max-attempts:5}") int maxAttempts,
      @Value("${notifications.outbox.base-retry-seconds:5}") long baseRetrySeconds) {
    this(
        outboxRepository,
        templateRenderer,
        mailSender,
        orderSnapshotPort,
        transactionManager,
        Clock.systemUTC(),
        maxAttempts,
        baseRetrySeconds);
  }

  public NotificationService(
      NotificationOutboxRepository outboxRepository,
      EmailTemplateRenderer templateRenderer,
      MailSenderPort mailSender,
      OrderSnapshotPort orderSnapshotPort,
      PlatformTransactionManager transactionManager,
      Clock clock,
      int maxAttempts,
      long baseRetrySeconds) {
    this.outboxRepository = outboxRepository;
    this.templateRenderer = templateRenderer;
    this.mailSender = mailSender;
    this.orderSnapshotPort = orderSnapshotPort;
    this.clock = clock;
    this.messageTransaction = new TransactionTemplate(transactionManager);
    this.messageTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.maxAttempts = maxAttempts;
    this.baseRetrySeconds = baseRetrySeconds;
  }

  @Override
  @Transactional
  public NotificationReceipt enqueue(NotificationRequest request) {
    if (request == null
        || request.eventId() == null
        || request.eventType() == null
        || request.orderId() == null
        || request.occurredAt() == null) {
      throw new ContractException("VALIDATION_ERROR");
    }

    String normalizedTo = normalizeAndValidateEmail(request.to());
    String payloadHash = computeHash(request, normalizedTo);

    try {
      Optional<NotificationOutboxEntry> existing =
          outboxRepository.findByEvent(request.eventId(), request.eventType());
      if (existing.isPresent()) {
        return receiptFor(existing.get(), payloadHash);
      }
      NotificationOutboxEntry entry = pendingEntry(request, normalizedTo, payloadHash);
      if (outboxRepository.insertIfAbsent(entry)) {
        return new NotificationReceipt(entry.getId(), true);
      }
      return receiptFor(
          outboxRepository.findByEvent(request.eventId(), request.eventType()).orElseThrow(),
          payloadHash);
    } catch (DataAccessException ex) {
      throw new ContractException("DEPENDENCY_UNAVAILABLE");
    }
  }

  private static NotificationReceipt receiptFor(NotificationOutboxEntry entry, String payloadHash) {
    if (!Objects.equals(entry.getPayloadHash(), payloadHash)) {
      throw new ContractException("IDEMPOTENCY_CONFLICT");
    }
    return new NotificationReceipt(entry.getId(), true);
  }

  private NotificationOutboxEntry pendingEntry(
      NotificationRequest request, String email, String payloadHash) {
    var rendered =
        templateRenderer.render(request.eventType(), request.orderId(), request.occurredAt());
    return NotificationOutboxEntry.createPending(
        request.eventId(),
        request.eventType(),
        request.orderId(),
        email,
        rendered.subject(),
        rendered.body(),
        payloadHash,
        request.occurredAt(),
        clock.instant());
  }

  /** Persists the intent in the same transaction as the delivery event. */
  @Transactional
  public void recordIntent(
      NotificationEventType eventType, UUID orderId, UUID eventId, Instant occurredAt) {
    outboxRepository.insertIfAbsent(
        pendingEntry(
            new NotificationRequest(eventId, eventType, orderId, null, occurredAt), null, null));
  }

  public int processPending(int batchSize) {
    Instant cutoff = clock.instant();
    int sentCount = 0;
    for (int i = 0; i < Math.max(1, batchSize); i++) {
      ProcessResult result = messageTransaction.execute(status -> processNext(cutoff));
      if (result == ProcessResult.EMPTY) {
        break;
      }
      if (result == ProcessResult.SENT) {
        sentCount++;
      }
    }
    return sentCount;
  }

  private enum ProcessResult {
    EMPTY,
    NOT_SENT,
    SENT
  }

  private ProcessResult processNext(Instant cutoff) {
    var due = outboxRepository.lockNextDuePending(cutoff);
    if (due.isEmpty()) {
      return ProcessResult.EMPTY;
    }
    NotificationOutboxEntry entry = due.get();
    if (entry.getRecipientEmail() == null && !resolveRecipient(entry)) {
      outboxRepository.save(entry);
      return ProcessResult.NOT_SENT;
    }
    try {
      mailSender.send(entry.getRecipientEmail(), entry.getSubject(), entry.getBody());
      entry.markSent(clock.instant());
    } catch (Exception ex) {
      recordFailure(entry, "SMTP delivery error");
    }
    outboxRepository.save(entry);
    return entry.getStatus() == OutboxStatus.SENT ? ProcessResult.SENT : ProcessResult.NOT_SENT;
  }

  private boolean resolveRecipient(NotificationOutboxEntry entry) {
    OrderSnapshot order;
    try {
      order = orderSnapshotPort.getById(entry.getOrderId());
    } catch (RuntimeException ex) {
      recordFailure(entry, "Order lookup failed");
      return false;
    }
    if (order == null) {
      recordFailure(entry, "Order lookup failed");
      return false;
    }
    String email;
    try {
      email = normalizeAndValidateEmail(order.recipientEmail());
    } catch (ContractException ex) {
      entry.markSkipped(clock.instant());
      return false;
    }
    var request =
        new NotificationRequest(
            entry.getEventId(),
            entry.getEventType(),
            entry.getOrderId(),
            email,
            entry.getOccurredAt());
    entry.resolveRecipient(email, computeHash(request, email));
    return true;
  }

  private void recordFailure(NotificationOutboxEntry entry, String reason) {
    log.warn("Notification id={} attempt={}: {}", entry.getId(), entry.getAttempts() + 1, reason);
    entry.recordFailure(reason, maxAttempts, baseRetrySeconds, clock.instant());
  }

  private static String normalizeAndValidateEmail(String email) {
    if (email == null) {
      throw new ContractException("VALIDATION_ERROR");
    }
    String trimmed = email.trim();
    if (trimmed.isEmpty() || trimmed.length() > 254 || !EMAIL_PATTERN.matcher(trimmed).matches()) {
      throw new ContractException("VALIDATION_ERROR");
    }
    return trimmed.toLowerCase(Locale.ROOT);
  }

  private static String computeHash(NotificationRequest req, String normalizedTo) {
    String canonical =
        req.eventId()
            + "|"
            + req.eventType().name()
            + "|"
            + req.orderId()
            + "|"
            + normalizedTo
            + "|"
            + req.occurredAt().toEpochMilli();
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(canonical.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 not available", ex);
    }
  }
}
