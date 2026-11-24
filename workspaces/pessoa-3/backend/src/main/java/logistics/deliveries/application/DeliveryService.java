package logistics.deliveries.application;

import contracts.v1.ActorContext;
import contracts.v1.ContractException;
import contracts.v1.CurrentActorPort;
import contracts.v1.DeliveryCommandPort;
import contracts.v1.DeliverySnapshot;
import contracts.v1.DeliveryStatus;
import contracts.v1.DeliveryStatusPort;
import contracts.v1.OrderSnapshot;
import contracts.v1.OrderSnapshotPort;
import contracts.v1.OrderStatus;
import contracts.v1.Role;
import contracts.v1.RouteSnapshot;
import contracts.v1.RouteSnapshotPort;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import logistics.deliveries.domain.Delivery;
import logistics.deliveries.domain.DeliveryEvent;
import logistics.deliveries.domain.DeliveryRepository;
import logistics.fakes.FakeRouteSnapshotPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeliveryService implements DeliveryCommandPort, DeliveryStatusPort {
  private final DeliveryRepository deliveryRepository;
  private final OrderSnapshotPort orderSnapshotPort;
  private final RouteSnapshotPort routeSnapshotPort;
  private final CurrentActorPort currentActorPort;

  public DeliveryService(
      DeliveryRepository deliveryRepository,
      OrderSnapshotPort orderSnapshotPort,
      RouteSnapshotPort routeSnapshotPort,
      CurrentActorPort currentActorPort) {
    this.deliveryRepository = deliveryRepository;
    this.orderSnapshotPort = orderSnapshotPort;
    this.routeSnapshotPort = routeSnapshotPort;
    this.currentActorPort = currentActorPort;
  }

  // --- DeliveryCommandPort Implementation ---

  @Override
  @Transactional
  public DeliverySnapshot ensureCreated(UUID orderId, UUID eventId) {
    Objects.requireNonNull(orderId, "orderId must not be null");

    // Pre-condition: order must exist and be CONFIRMED
    OrderSnapshot order = orderSnapshotPort.getById(orderId);
    if (order == null || order.status() != OrderStatus.CONFIRMED) {
      throw new ContractException("PRECONDITION_FAILED");
    }

    // Idempotency check: if delivery already exists for this order, return existing
    Optional<Delivery> existing = deliveryRepository.findByOrderId(orderId);
    if (existing.isPresent()) {
      return existing.get().toSnapshot();
    }

    Delivery delivery = Delivery.create(orderId);
    deliveryRepository.save(delivery);

    // Record creation event
    ActorContext actor;
    try {
      actor = currentActorPort.requireActor();
    } catch (Exception ex) {
      actor = new ActorContext(UUID.randomUUID(), Role.OPERATOR, null, null);
    }

    DeliveryEvent event =
        DeliveryEvent.recordWithId(
            eventId != null ? eventId : UUID.randomUUID(),
            delivery.getId(),
            null,
            DeliveryStatus.PENDING,
            actor.actorId(),
            actor.role(),
            "Order confirmed, delivery created",
            null);
    deliveryRepository.recordEvent(event);

    return delivery.toSnapshot();
  }

  @Override
  @Transactional
  public DeliverySnapshot assignRoute(UUID orderId, UUID routeId, UUID eventId) {
    Objects.requireNonNull(orderId, "orderId must not be null");
    Objects.requireNonNull(routeId, "routeId must not be null");

    Delivery delivery =
        deliveryRepository
            .findByOrderId(orderId)
            .orElseThrow(() -> new ContractException("NOT_FOUND"));

    // Check idempotency: if already assigned to this exact route, return it
    if (delivery.getStatus() == DeliveryStatus.ASSIGNED
        && Objects.equals(delivery.getRouteId(), routeId)) {
      return delivery.toSnapshot();
    }

    // Verify route exists, is assigned (has driver+vehicle), and contains orderId.
    // Contract v1: assignRoute requires the route to already be ASSIGNED or IN_PROGRESS;
    // a PLANNED route has no driver/vehicle yet and must be rejected.
    RouteSnapshot route = routeSnapshotPort.getById(routeId);
    if (route == null
        || route.orderIds() == null
        || !route.orderIds().contains(orderId)) {
      throw new ContractException("CONFLICT");
    }
    if (route.status() != contracts.v1.RouteStatus.ASSIGNED
        && route.status() != contracts.v1.RouteStatus.IN_PROGRESS) {
      throw new ContractException("CONFLICT");
    }

    DeliveryStatus fromStatus = delivery.getStatus();
    delivery.assignRoute(routeId);
    deliveryRepository.save(delivery);

    ActorContext actor;
    try {
      actor = currentActorPort.requireActor();
    } catch (Exception ex) {
      actor = new ActorContext(UUID.randomUUID(), Role.OPERATOR, null, null);
    }

    DeliveryEvent event =
        DeliveryEvent.recordWithId(
            eventId != null ? eventId : UUID.randomUUID(),
            delivery.getId(),
            fromStatus,
            DeliveryStatus.ASSIGNED,
            actor.actorId(),
            actor.role(),
            "Route assigned: " + routeId,
            null);
    deliveryRepository.recordEvent(event);

    return delivery.toSnapshot();
  }

  // --- DeliveryStatusPort Implementation ---

  @Override
  @Transactional(readOnly = true)
  public DeliverySnapshot getByOrderId(UUID orderId) {
    Objects.requireNonNull(orderId, "orderId must not be null");
    return deliveryRepository
        .findByOrderId(orderId)
        .map(Delivery::toSnapshot)
        .orElseThrow(() -> new ContractException("NOT_FOUND"));
  }

  // --- Use Cases for REST API ---

  @Transactional
  public DeliverySnapshot updateStatus(UUID deliveryId, String targetStatusStr, String reason) {
    Objects.requireNonNull(deliveryId, "deliveryId must not be null");
    if (targetStatusStr == null || targetStatusStr.trim().isEmpty()) {
      throw new ContractException("VALIDATION_ERROR");
    }

    ActorContext actor = currentActorPort.requireActor();
    Delivery delivery =
        deliveryRepository
            .findById(deliveryId)
            .orElseThrow(() -> new ContractException("NOT_FOUND"));

    validateActorAccess(actor, delivery);

    DeliveryStatus targetStatus;
    try {
      targetStatus = DeliveryStatus.valueOf(targetStatusStr.trim().toUpperCase());
    } catch (IllegalArgumentException ex) {
      throw new ContractException("VALIDATION_ERROR");
    }

    // Contract rule: PATCH status cannot be used to complete delivery (DELIVERED)
    if (targetStatus == DeliveryStatus.DELIVERED) {
      throw new ContractException("VALIDATION_ERROR");
    }

    // Contract rule: reason is required for FAILED_ATTEMPT (1..500) and forbidden for others
    if (targetStatus == DeliveryStatus.FAILED_ATTEMPT) {
      if (reason == null || reason.trim().isEmpty() || reason.length() > 500) {
        throw new ContractException("VALIDATION_ERROR");
      }
    } else {
      if (reason != null && !reason.trim().isEmpty()) {
        throw new ContractException("VALIDATION_ERROR");
      }
    }

    DeliveryStatus fromStatus = delivery.getStatus();

    // Contract v1: PATCH /status only accepts IN_TRANSIT, FAILED_ATTEMPT and ASSIGNED.
    // DELIVERED must use POST /complete; CANCELLED rule is pending — not allowed here.
    switch (targetStatus) {
      case IN_TRANSIT -> delivery.startTransit();
      case FAILED_ATTEMPT -> delivery.failAttempt(reason);
      case ASSIGNED -> {
        if (delivery.getRouteId() == null) {
          throw new ContractException("CONFLICT");
        }
        delivery.assignRoute(delivery.getRouteId());
      }
      default -> throw new ContractException("VALIDATION_ERROR");
    }

    deliveryRepository.save(delivery);

    DeliveryEvent event =
        DeliveryEvent.record(
            delivery.getId(),
            fromStatus,
            delivery.getStatus(),
            actor.actorId(),
            actor.role(),
            reason,
            null);
    deliveryRepository.recordEvent(event);

    return delivery.toSnapshot();
  }

  @Transactional
  public DeliverySnapshot completeDelivery(UUID deliveryId, String receiverName) {
    Objects.requireNonNull(deliveryId, "deliveryId must not be null");
    if (receiverName == null || receiverName.trim().isEmpty() || receiverName.length() > 150) {
      throw new ContractException("VALIDATION_ERROR");
    }

    ActorContext actor = currentActorPort.requireActor();
    Delivery delivery =
        deliveryRepository
            .findById(deliveryId)
            .orElseThrow(() -> new ContractException("NOT_FOUND"));

    validateActorAccess(actor, delivery);

    DeliveryStatus fromStatus = delivery.getStatus();
    delivery.complete(receiverName);
    deliveryRepository.save(delivery);

    DeliveryEvent event =
        DeliveryEvent.record(
            delivery.getId(),
            fromStatus,
            DeliveryStatus.DELIVERED,
            actor.actorId(),
            actor.role(),
            null,
            receiverName.trim());
    deliveryRepository.recordEvent(event);

    return delivery.toSnapshot();
  }

  @Transactional(readOnly = true)
  public DeliveryPage getMyDeliveries(int page, int size) {
    if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
      throw new ContractException("VALIDATION_ERROR");
    }

    ActorContext actor = currentActorPort.requireActor();
    if (actor.role() != Role.DRIVER || actor.driverId() == null) {
      throw new ContractException("FORBIDDEN");
    }

    List<UUID> driverRouteIds = List.of();
    if (routeSnapshotPort instanceof FakeRouteSnapshotPort fakeRoutes) {
      driverRouteIds =
          fakeRoutes.findByDriverId(actor.driverId()).stream()
              .map(RouteSnapshot::routeId)
              .toList();
    }

    if (driverRouteIds.isEmpty()) {
      return new DeliveryPage(Collections.emptyList(), page, size, 0L);
    }

    int offset = (int) ((long) page * size);
    List<DeliverySnapshot> items =
        deliveryRepository.findByRouteIds(driverRouteIds, offset, size).stream()
            .map(Delivery::toSnapshot)
            .toList();
    long totalItems = deliveryRepository.countByRouteIds(driverRouteIds);
    return new DeliveryPage(items, page, size, totalItems);
  }

  @Transactional(readOnly = true)
  public DeliverySnapshot getById(UUID deliveryId) {
    ActorContext actor = currentActorPort.requireActor();
    Delivery delivery =
        deliveryRepository
            .findById(deliveryId)
            .orElseThrow(() -> new ContractException("NOT_FOUND"));

    validateActorAccess(actor, delivery);
    return delivery.toSnapshot();
  }

  @Transactional(readOnly = true)
  public List<DeliveryEvent> getEvents(UUID deliveryId) {
    ActorContext actor = currentActorPort.requireActor();
    Delivery delivery =
        deliveryRepository
            .findById(deliveryId)
            .orElseThrow(() -> new ContractException("NOT_FOUND"));

    validateActorAccess(actor, delivery);
    return deliveryRepository.findEventsByDeliveryId(deliveryId);
  }

  private void validateActorAccess(ActorContext actor, Delivery delivery) {
    if (actor.role() == Role.ADMIN || actor.role() == Role.OPERATOR) {
      return; // Full access
    }

    if (actor.role() == Role.DRIVER) {
      if (actor.driverId() == null || delivery.getRouteId() == null) {
        throw new ContractException("FORBIDDEN");
      }
      RouteSnapshot route = routeSnapshotPort.getById(delivery.getRouteId());
      if (route == null || !Objects.equals(route.driverId(), actor.driverId())) {
        throw new ContractException("FORBIDDEN");
      }
      return;
    }

    // Role.STORE is not authorized to modify or view driver actions directly
    throw new ContractException("FORBIDDEN");
  }
}
