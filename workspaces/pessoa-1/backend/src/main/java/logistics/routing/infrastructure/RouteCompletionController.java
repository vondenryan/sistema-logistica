package logistics.routing.infrastructure;

import contracts.v1.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import logistics.routing.application.RouteStore;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("integration")
@RequestMapping("/api/v1/routes")
class RouteCompletionController {
  private final RouteStore routes;
  private final CurrentActorPort actors;
  private final DeliveryStatusPort deliveries;
  private final FleetReleasePort fleet;
  private final TransactionTemplate transactions;

  RouteCompletionController(
      RouteStore routes,
      CurrentActorPort actors,
      DeliveryStatusPort deliveries,
      FleetReleasePort fleet,
      TransactionTemplate transactions) {
    this.routes = routes;
    this.actors = actors;
    this.deliveries = deliveries;
    this.fleet = fleet;
    this.transactions = transactions;
  }

  @PostMapping("/{id}/complete")
  RouteSnapshot complete(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
    var role = actors.requireActor().role();
    if (role != Role.ADMIN && role != Role.OPERATOR) throw new ContractException("FORBIDDEN");
    if (!body.isEmpty()) throw new ContractException("VALIDATION_ERROR");

    return transactions.execute(
        tx -> {
          var route = routes.get(id, true);
          if (route.status() == RouteStatus.COMPLETED) return route;
          if (route.status() != RouteStatus.IN_PROGRESS) throw new ContractException("CONFLICT");
          for (UUID orderId : route.orderIds()) {
            var delivery = deliveries.getByOrderId(orderId);
            if (!id.equals(delivery.routeId()) || delivery.status() != DeliveryStatus.DELIVERED) {
              throw new ContractException("PRECONDITION_FAILED");
            }
          }
          routes.complete(id);
          UUID eventId =
              UUID.nameUUIDFromBytes(("route-completed:" + id).getBytes(StandardCharsets.UTF_8));
          fleet.release(new ReleaseRequest(id, eventId, ReleaseReason.ROUTE_COMPLETED));
          return routes.get(id, false);
        });
  }
}
