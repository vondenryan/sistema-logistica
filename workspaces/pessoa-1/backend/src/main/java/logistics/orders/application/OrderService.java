package logistics.orders.application;

import contracts.v1.*;
import java.math.*;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import logistics.orders.domain.OrderData.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class OrderService implements OrderSnapshotPort {
  private final OrderStore repo;
  private final CurrentActorPort actor;
  private final PartnerLookupPort partner;
  private final DeliveryCommandPort deliveries;
  private final NotificationRequestPort notifications;
  private final TransactionTemplate tx;
  private final SimulatedPaymentPort payments;
  private final SecureRandom random = new SecureRandom();

  public OrderService(
      OrderStore repo,
      CurrentActorPort actor,
      PartnerLookupPort partner,
      DeliveryCommandPort deliveries,
      NotificationRequestPort notifications,
      TransactionTemplate tx,
      SimulatedPaymentPort payments) {
    this.repo = repo;
    this.actor = actor;
    this.partner = partner;
    this.deliveries = deliveries;
    this.notifications = notifications;
    this.tx = tx;
    this.payments = payments;
  }

  private ActorContext permitted() {
    ActorContext a = actor.requireActor();
    if (a.role() == Role.DRIVER) throw new ContractException("FORBIDDEN");
    return a;
  }

  private void scope(ActorContext a, UUID store) {
    if (a.role() == Role.STORE && !Objects.equals(a.storeId(), store))
      throw new ContractException("NOT_FOUND");
  }

  private void validate(Input in) {
    if (in == null
        || in.storeId() == null
        || in.originId() == null
        || in.destination() == null
        || in.recipientName() == null
        || in.recipientName().isBlank()
        || in.recipientName().length() > 150
        || in.items() == null
        || in.items().isEmpty()
        || in.items().size() > 100) throw new ContractException("VALIDATION_ERROR");
    var d = in.destination();
    if (d.line1() == null
        || d.line1().isBlank()
        || d.line1().length() > 200
        || d.city() == null
        || d.city().isBlank()
        || d.city().length() > 100
        || d.state() == null
        || !d.state().matches("[A-Z]{2}")
        || !"BR".equals(d.country())
        || (d.line2() != null && d.line2().length() > 100)
        || (d.postalCode() != null && !d.postalCode().matches("[0-9]{5}-?[0-9]{3}"))
        || (d.latitude() != null
            && (d.latitude().compareTo(new BigDecimal("-90")) < 0
                || d.latitude().compareTo(new BigDecimal("90")) > 0))
        || (d.longitude() != null
            && (d.longitude().compareTo(new BigDecimal("-180")) < 0
                || d.longitude().compareTo(new BigDecimal("180")) > 0))
        || (in.recipientEmail() != null
            && (!in.recipientEmail().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
                || in.recipientEmail().length() > 254)))
      throw new ContractException("VALIDATION_ERROR");
    for (Item i : in.items()) {
      try {
        BigDecimal w = new BigDecimal(i.unitWeightKg());
        if (i.description() == null
            || i.description().isBlank()
            || i.description().length() > 200
            || i.quantity() == null
            || i.quantity() < 1
            || i.quantity() > 10000
            || w.signum() <= 0
            || w.scale() > 3
            || w.precision() > 12) throw new Exception();
      } catch (Exception e) {
        throw new ContractException("VALIDATION_ERROR");
      }
    }
  }

  private OriginSnapshot origin(Input in) {
    OriginSnapshot o = partner.getOrigin(in.originId());
    StoreSnapshot s = partner.getStore(in.storeId());
    if (!o.storeId().equals(in.storeId())) throw new ContractException("NOT_FOUND");
    if (!o.active() || !s.active()) throw new ContractException("PRECONDITION_FAILED");
    return o;
  }

  public View create(Input input) {
    ActorContext a = permitted();
    validate(input);
    scope(a, input.storeId());
    OriginSnapshot o = origin(input);
    byte[] bytes = new byte[18];
    random.nextBytes(bytes);
    String tracking = "TRK-" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    UUID id = UUID.randomUUID();
    tx.executeWithoutResult(t -> repo.insert(id, input, o, tracking));
    return repo.get(id, false);
  }

  public View update(UUID id, Input input) {
    ActorContext a = permitted();
    validate(input);
    return tx.execute(
        t -> {
          View old = repo.get(id, true);
          scope(a, old.storeId());
          scope(a, input.storeId());
          if (!old.storeId().equals(input.storeId()) || old.status() != OrderStatus.DRAFT)
            throw new ContractException("CONFLICT");
          OriginSnapshot o = origin(input);
          repo.update(id, input, o);
          return repo.get(id, false);
        });
  }

  public View get(UUID id) {
    ActorContext a = permitted();
    View v = repo.get(id, false);
    scope(a, v.storeId());
    return v;
  }

  public Page list(int page, int size) {
    ActorContext a = permitted();
    if (page < 0 || size < 1 || size > 100) throw new ContractException("VALIDATION_ERROR");
    return repo.list(a.role() == Role.STORE ? a.storeId() : null, page, size);
  }

  public View confirm(UUID id) {
    ActorContext a = permitted();
    tx.executeWithoutResult(
        t -> {
          View v = repo.get(id, true);
          scope(a, v.storeId());
          if (v.status() == OrderStatus.CANCELLED) throw new ContractException("CONFLICT");
          if (v.status() == OrderStatus.CONFIRMED) return;
          Input input =
              new Input(
                  v.storeId(),
                  v.originId(),
                  v.destination(),
                  v.recipientName(),
                  v.recipientEmail(),
                  v.items());
          validate(input);
          OriginSnapshot o = origin(input);
          if (v.destination().latitude() == null
              || v.destination().longitude() == null
              || o.address().latitude() == null
              || o.address().longitude() == null)
            throw new ContractException("PRECONDITION_FAILED");
          repo.confirm(id, o);
          payments.ensureApproved(id);
        });
    reconcile(id);
    return get(id);
  }

  public View cancel(UUID id, Cancel cancel) {
    ActorContext a = permitted();
    if (cancel == null
        || cancel.reason() == null
        || cancel.reason().isBlank()
        || cancel.reason().length() > 500) throw new ContractException("VALIDATION_ERROR");
    return tx.execute(
        t -> {
          View v = repo.get(id, true);
          scope(a, v.storeId());
          if (v.status() != OrderStatus.DRAFT) throw new ContractException("CONFLICT");
          repo.cancel(id);
          return repo.get(id, false);
        });
  }

  public void reconcile(UUID id) {
    var job = repo.job(id);
    if (job == null) return;
    try {
      if (!job.deliveryDone()) {
        deliveries.ensureCreated(id, job.eventId());
        repo.deliveryDone(id);
      }
      if (!job.notificationDone()) {
        if (job.email() != null)
          notifications.enqueue(
              new NotificationRequest(
                  job.eventId(), NotificationEventType.ORDER_CONFIRMED, id, job.email(), job.at()));
        repo.notificationDone(id);
      }
    } catch (RuntimeException ex) {
      throw new ContractException("DEPENDENCY_UNAVAILABLE");
    }
  }

  @Scheduled(fixedDelayString = "${app.reconcile-delay-ms:30000}")
  public void retry() {
    for (UUID id : repo.pending())
      try {
        reconcile(id);
      } catch (RuntimeException ignored) {
      }
  }

  public OrderSnapshot getById(UUID id) {
    return snapshot(repo.get(id, false));
  }

  public OrderSnapshot findByTrackingCode(String code) {
    return snapshot(repo.getByTracking(code));
  }

  private OrderSnapshot snapshot(View v) {
    return new OrderSnapshot(
        v.orderId(),
        v.storeId(),
        v.originId(),
        v.status(),
        v.origin(),
        v.destination(),
        v.recipientName(),
        v.recipientEmail(),
        v.trackingCode(),
        v.totalWeightKg(),
        v.freightAmount(),
        v.currency());
  }
}
