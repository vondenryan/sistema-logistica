package logistics;

import contracts.v1.DeliverySnapshot;
import java.util.UUID;
import logistics.deliveries.application.DeliveryService;
import logistics.fakes.FakeCurrentActorPort;
import logistics.fakes.FakeOrderSnapshotPort;
import logistics.fakes.FakeRouteSnapshotPort;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
public class DevSeed implements ApplicationRunner {

  private final DeliveryService deliveryService;
  private final FakeCurrentActorPort fakeCurrentActorPort;

  public DevSeed(DeliveryService deliveryService, FakeCurrentActorPort fakeCurrentActorPort) {
    this.deliveryService = deliveryService;
    this.fakeCurrentActorPort = fakeCurrentActorPort;
  }

  @Override
  public void run(ApplicationArguments args) {
    fakeCurrentActorPort.asOperator();

    UUID eventId = UUID.randomUUID();
    DeliverySnapshot delivery =
        deliveryService.ensureCreated(FakeOrderSnapshotPort.CANONICAL_ORDER_ID, eventId);

    deliveryService.assignRoute(
        FakeOrderSnapshotPort.CANONICAL_ORDER_ID,
        FakeRouteSnapshotPort.CANONICAL_ROUTE_ID,
        UUID.randomUUID());

    System.out.printf(
        "[DevSeed] Delivery seeded: id=%s orderId=%s%n",
        delivery.deliveryId(), FakeOrderSnapshotPort.CANONICAL_ORDER_ID);
  }
}
