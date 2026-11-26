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
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import logistics.notifications.domain.NotificationOutboxEntry;
import logistics.notifications.domain.NotificationOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService implements NotificationRequestPort {
  private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
  private static final Pattern EMAIL_PATTERN =
      Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

  private final NotificationOutboxRepository outboxRepository;
  private final EmailTemplateRenderer templateRenderer;
  private final MailSenderPort mailSender;
  private final OrderSnapshotPort orderSnapshotPort;
  private final Clock clock;
  private final int maxAttempts;
  private final long baseRetrySeconds;

  @Autowired
  public NotificationService(
      NotificationOutboxRepository outboxRepository,
      EmailTemplateRenderer templateRenderer,
      MailSenderPort mailSender,
      OrderSnapshotPort orderSnapshotPort,
      @Value("${notifications.outbox.max-attempts:5}") int maxAttempts,
      @Value("${notifications.outbox.base-retry-seconds:5}") long baseRetrySeconds) {
    this(
        outboxRepository,
        templateRenderer,
        mailSender,
        orderSnapshotPort,
        Clock.systemUTC(),
        maxAttempts,
        baseRetrySeconds);
  }

  public NotificationService(
      NotificationOutboxRepository outboxRepository,
      EmailTemplateRenderer templateRenderer,
      MailSenderPort mailSender,
      OrderSnapshotPort orderSnapshotPort,
      Clock clock,
      int maxAttempts,
      long baseRetrySeconds) {
    this.outboxRepository = outboxRepository;
    this.templateRenderer = templateRenderer;
    this.mailSender = mailSender;
    this.orderSnapshotPort = orderSnapshotPort;
    this.clock = clock;
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

    Optional<NotificationOutboxEntry> existing;
    try {
      existing = outboxRepository.findByEvent(request.eventId(), request.eventType());
    } catch (DataAccessException ex) {
      throw new ContractException("DEPENDENCY_UNAVAILABLE");
    }

    if (existing.isPresent()) {
      NotificationOutboxEntry entry = existing.get();
      if (!Objects.equals(entry.getPayloadHash(), payloadHash)) {
        throw new ContractException("IDEMPOTENCY_CONFLICT");
      }
      return new NotificationReceipt(entry.getId(), true);
    }

    EmailTemplateRenderer.RenderedEmail rendered =
        templateRenderer.render(request.eventType(), request.orderId(), request.occurredAt());

    NotificationOutboxEntry entry =
        NotificationOutboxEntry.createPending(
            request.eventId(),
            request.eventType(),
            request.orderId(),
            normalizedTo,
            rendered.subject(),
            rendered.body(),
            payloadHash,
            request.occurredAt(),
            clock.instant());

    try {
      outboxRepository.save(entry);
    } catch (DataAccessException ex) {
      throw new ContractException("DEPENDENCY_UNAVAILABLE");
    }

    return new NotificationReceipt(entry.getId(), true);
  }

  /**
   * Attempts to enqueue a notification for a delivery transition without failing the caller's
   * transaction if the order has no email or if the outbox is temporarily unavailable.
   */
  public void enqueueIfEligible(
      NotificationEventType eventType, UUID orderId, UUID eventId, Instant occurredAt) {
    try {
      OrderSnapshot order = orderSnapshotPort.getById(orderId);
      if (order == null || order.recipientEmail() == null || order.recipientEmail().isBlank()) {
        log.info(
            "Notification {} skipped for order {}: recipient email not available",
            eventType,
            orderId);
        return;
      }
      enqueue(
          new NotificationRequest(
              eventId != null ? eventId : UUID.randomUUID(),
              eventType,
              orderId,
              order.recipientEmail(),
              occurredAt != null ? occurredAt : clock.instant()));
    } catch (Exception ex) {
      log.warn(
          "Could not enqueue notification {} for order {}: {}",
          eventType,
          orderId,
          ex.getMessage());
    }
  }

  @Transactional
  public int processPending(int batchSize) {
    Instant now = clock.instant();
    List<NotificationOutboxEntry> due = outboxRepository.findDuePending(now, batchSize);
    int sentCount = 0;

    for (NotificationOutboxEntry entry : due) {
      try {
        mailSender.send(entry.getRecipientEmail(), entry.getSubject(), entry.getBody());
        entry.markSent(clock.instant());
        sentCount++;
      } catch (Exception ex) {
        log.warn(
            "Failed to send notification id={} attempt={}: {}",
            entry.getId(),
            entry.getAttempts() + 1,
            ex.getMessage());
        entry.recordFailure(ex.getMessage(), maxAttempts, baseRetrySeconds, clock.instant());
      }
      outboxRepository.save(entry);
    }

    return sentCount;
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
