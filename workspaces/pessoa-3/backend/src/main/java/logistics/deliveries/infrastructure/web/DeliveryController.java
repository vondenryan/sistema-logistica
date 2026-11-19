package logistics.deliveries.infrastructure.web;

import contracts.v1.DeliverySnapshot;
import java.util.List;
import java.util.UUID;
import logistics.deliveries.application.DeliveryService;
import logistics.deliveries.domain.DeliveryEvent;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/deliveries")
public class DeliveryController {
  private final DeliveryService deliveryService;

  public DeliveryController(DeliveryService deliveryService) {
    this.deliveryService = deliveryService;
  }

  @GetMapping("/my")
  public ResponseEntity<List<DeliverySnapshot>> getMyDeliveries(
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "20") int size) {
    List<DeliverySnapshot> deliveries = deliveryService.getMyDeliveries(page, size);
    return ResponseEntity.ok(deliveries);
  }

  @GetMapping("/{id}")
  public ResponseEntity<DeliverySnapshot> getById(@PathVariable("id") UUID id) {
    DeliverySnapshot delivery = deliveryService.getById(id);
    return ResponseEntity.ok(delivery);
  }

  @GetMapping("/{id}/events")
  public ResponseEntity<List<DeliveryEvent>> getEvents(@PathVariable("id") UUID id) {
    List<DeliveryEvent> events = deliveryService.getEvents(id);
    return ResponseEntity.ok(events);
  }

  @PatchMapping("/{id}/status")
  public ResponseEntity<DeliverySnapshot> updateStatus(
      @PathVariable("id") UUID id, @RequestBody UpdateStatusRequest request) {
    DeliverySnapshot snapshot =
        deliveryService.updateStatus(id, request.status(), request.reason());
    return ResponseEntity.ok(snapshot);
  }

  @PostMapping("/{id}/complete")
  public ResponseEntity<DeliverySnapshot> completeDelivery(
      @PathVariable("id") UUID id, @RequestBody CompleteDeliveryRequest request) {
    DeliverySnapshot snapshot = deliveryService.completeDelivery(id, request.receiverName());
    return ResponseEntity.ok(snapshot);
  }
}
