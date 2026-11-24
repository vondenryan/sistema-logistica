package logistics.deliveries.application;

import contracts.v1.ContractException;
import contracts.v1.OrderSnapshot;
import contracts.v1.OrderSnapshotPort;
import java.util.List;
import logistics.deliveries.domain.Delivery;
import logistics.deliveries.domain.DeliveryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TrackingService {
  private final OrderSnapshotPort orderSnapshotPort;
  private final DeliveryRepository deliveryRepository;

  public TrackingService(
      OrderSnapshotPort orderSnapshotPort, DeliveryRepository deliveryRepository) {
    this.orderSnapshotPort = orderSnapshotPort;
    this.deliveryRepository = deliveryRepository;
  }

  @Transactional(readOnly = true)
  public PublicTrackingView trackByCode(String code) {
    // Reject blank, whitespace, or overly long codes with a generic NOT_FOUND
    // to avoid leaking valid code format rules to enumeration probes.
    if (code == null || code.isBlank() || code.length() > 64 || !code.equals(code.trim())) {
      throw new ContractException("NOT_FOUND");
    }

    OrderSnapshot order;
    try {
      order = orderSnapshotPort.findByTrackingCode(code);
    } catch (ContractException ex) {
      throw new ContractException("NOT_FOUND");
    }

    if (order == null) {
      throw new ContractException("NOT_FOUND");
    }

    Delivery delivery =
        deliveryRepository
            .findByOrderId(order.orderId())
            .orElseThrow(() -> new ContractException("NOT_FOUND"));

    List<TrackingMilestone> milestones =
        deliveryRepository.findEventsByDeliveryId(delivery.getId()).stream()
            .map(event -> new TrackingMilestone(event.toStatus(), event.occurredAt()))
            .toList();

    return new PublicTrackingView(delivery.getStatus(), milestones);
  }
}
