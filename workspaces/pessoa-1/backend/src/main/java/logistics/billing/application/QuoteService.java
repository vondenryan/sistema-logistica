package logistics.billing.application;

import contracts.v1.*;
import java.math.*;
import java.time.Instant;
import java.util.*;
import logistics.geography.application.RoutingProvider;

public class QuoteService {
  private final QuoteStore store;
  private final OrderSnapshotPort orders;
  private final CurrentActorPort actors;
  private final RoutingProvider routing;
  private final String configuredTariff;

  public QuoteService(
      QuoteStore store,
      OrderSnapshotPort orders,
      CurrentActorPort actors,
      RoutingProvider routing,
      String configuredTariff) {
    this.store = store;
    this.orders = orders;
    this.actors = actors;
    this.routing = routing;
    this.configuredTariff = configuredTariff;
  }

  public QuoteStore.Quote quote(UUID id) {
    ActorContext actor = actors.requireActor();
    if (actor.role() == Role.DRIVER) throw new ContractException("FORBIDDEN");
    OrderSnapshot order = orders.getById(id);
    if (actor.role() == Role.STORE && !Objects.equals(actor.storeId(), order.storeId()))
      throw new ContractException("NOT_FOUND");
    if (order.status() != OrderStatus.CONFIRMED) throw new ContractException("PRECONDITION_FAILED");
    QuoteStore.Quote existing = store.latestQuote(id);
    if (existing != null) return existing;
    BigDecimal tariff;
    try {
      tariff = new BigDecimal(configuredTariff).setScale(2, RoundingMode.UNNECESSARY);
      if (tariff.signum() < 0 || tariff.precision() > 12) throw new IllegalArgumentException();
    } catch (Exception e) {
      throw new ContractException("PRECONDITION_FAILED");
    }
    BigDecimal distance =
        routing
            .roadDistanceKm(order.origin(), order.destination())
            .setScale(3, RoundingMode.HALF_UP);
    BigDecimal amount = distance.multiply(tariff).setScale(2, RoundingMode.HALF_UP);
    if (distance.signum() < 0 || distance.precision() > 12 || amount.precision() > 12)
      throw new ContractException("PRECONDITION_FAILED");
    var quote =
        new QuoteStore.Quote(
            UUID.randomUUID(),
            id,
            distance,
            tariff,
            amount,
            "BRL",
            Instant.now(),
            routing.source());
    return store.save(quote);
  }
}
