package logistics.routing.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import contracts.v1.*;
import java.math.BigDecimal;
import java.util.*;
import logistics.geography.application.RoutingProvider;
import org.junit.jupiter.api.Test;

class RouteServiceTest {
  private final RouteStore store = mock(RouteStore.class);
  private final OrderSnapshotPort orders = mock(OrderSnapshotPort.class);
  private final CurrentActorPort actor =
      () -> new ActorContext(UUID.randomUUID(), Role.ADMIN, null, null);
  private final RoutingProvider provider = mock(RoutingProvider.class);
  private final RouteService service =
      new RouteService(store, orders, actor, null, null, provider, null);
  private final UUID origin = UUID.randomUUID(), shop = UUID.randomUUID();

  private AddressSnapshot address(String lat) {
    return new AddressSnapshot(
        "Rua", null, "Cidade", "SC", null, "BR", new BigDecimal(lat), BigDecimal.ZERO);
  }

  private OrderSnapshot order(UUID id, UUID storeId, UUID originId, String lat) {
    return new OrderSnapshot(
        id,
        storeId,
        originId,
        OrderStatus.CONFIRMED,
        address("0"),
        address(lat),
        "name",
        null,
        "tracking",
        BigDecimal.ONE,
        null,
        "BRL");
  }

  @Test
  void suggestionUsesRoadDistancesAndDoesNotPersist() {
    UUID a = UUID.randomUUID(), b = UUID.randomUUID();
    var first = order(a, shop, origin, "1");
    var second = order(b, shop, origin, "2");
    when(orders.getById(a)).thenReturn(first);
    when(orders.getById(b)).thenReturn(second);
    when(provider.roadDistanceKm(any(), any()))
        .thenAnswer(
            call -> {
              AddressSnapshot from = call.getArgument(0), to = call.getArgument(1);
              return from.latitude().subtract(to.latitude()).abs().setScale(3);
            });
    var result = service.suggest(new RouteService.Request(origin, List.of(b, a)));
    assertThat(result.orderedOrderIds()).containsExactly(a, b);
    assertThat(result.estimatedDistanceKm()).isEqualTo("2.000");
    verify(store, never()).create(any());
  }

  @Test
  void rejectsEmptyTooManyDuplicatesMixedScopeAndActiveOrders() {
    UUID a = UUID.randomUUID(), b = UUID.randomUUID();
    when(orders.getById(a)).thenReturn(order(a, shop, origin, "1"));
    when(orders.getById(b)).thenReturn(order(b, UUID.randomUUID(), origin, "2"));
    assertThatThrownBy(() -> service.suggest(new RouteService.Request(origin, List.of())))
        .hasMessage("VALIDATION_ERROR");
    assertThatThrownBy(
            () ->
                service.suggest(
                    new RouteService.Request(
                        origin,
                        java.util.stream.IntStream.range(0, 11)
                            .mapToObj(i -> UUID.randomUUID())
                            .toList())))
        .hasMessage("VALIDATION_ERROR");
    assertThatThrownBy(() -> service.suggest(new RouteService.Request(origin, List.of(a, a))))
        .hasMessage("VALIDATION_ERROR");
    assertThatThrownBy(() -> service.suggest(new RouteService.Request(origin, List.of(a, b))))
        .hasMessage("CONFLICT");
    when(store.hasActiveOrder(a)).thenReturn(true);
    assertThatThrownBy(() -> service.suggest(new RouteService.Request(origin, List.of(a))))
        .hasMessage("CONFLICT");
  }

  @Test
  void deliveryFailureLeavesDurableAssignmentForRetryAndBlocksStart() {
    UUID routeId = UUID.randomUUID(),
        orderId = UUID.randomUUID(),
        driver = UUID.randomUUID(),
        vehicle = UUID.randomUUID();
    var route =
        new RouteSnapshot(
            routeId, shop, origin, RouteStatus.PLANNED, List.of(orderId), null, null, null);
    var assigned =
        new RouteSnapshot(
            routeId, shop, origin, RouteStatus.ASSIGNED, List.of(orderId), driver, vehicle, null);
    var fleet = mock(FleetAssignmentPort.class);
    var delivery = mock(DeliveryCommandPort.class);
    var state = new java.util.concurrent.atomic.AtomicReference<>(route);
    when(store.get(eq(routeId), anyBoolean())).thenAnswer(call -> state.get());
    when(orders.getById(orderId)).thenReturn(order(orderId, shop, origin, "1"));
    when(fleet.assign(any()))
        .thenReturn(
            new AssignmentSnapshot(
                UUID.randomUUID(), routeId, driver, vehicle, null, FleetType.OWN));
    doAnswer(
            call -> {
              state.set(assigned);
              return null;
            })
        .when(store)
        .assign(eq(routeId), any());
    when(store.pending(routeId)).thenReturn(true);
    when(delivery.assignRoute(eq(orderId), eq(routeId), any()))
        .thenThrow(new ContractException("DEPENDENCY_UNAVAILABLE"))
        .thenReturn(
            new DeliverySnapshot(
                UUID.randomUUID(),
                orderId,
                routeId,
                DeliveryStatus.ASSIGNED,
                java.time.Instant.now(),
                null));
    var db = new org.h2.jdbcx.JdbcDataSource();
    db.setURL("jdbc:h2:mem:route_retry;DB_CLOSE_DELAY=-1");
    var tx =
        new org.springframework.transaction.support.TransactionTemplate(
            new org.springframework.jdbc.datasource.DataSourceTransactionManager(db));
    var routes = new RouteService(store, orders, actor, fleet, delivery, provider, tx);
    assertThatThrownBy(() -> routes.assign(routeId)).hasMessage("DEPENDENCY_UNAVAILABLE");
    assertThat(state.get().status()).isEqualTo(RouteStatus.ASSIGNED);
    assertThatThrownBy(() -> routes.start(routeId)).hasMessage("CONFLICT");
    routes.reconcile(routeId);
    var events = org.mockito.ArgumentCaptor.forClass(UUID.class);
    verify(delivery, times(2)).assignRoute(eq(orderId), eq(routeId), events.capture());
    assertThat(events.getAllValues()).hasSize(2).allMatch(events.getValue()::equals);
    verify(store).done(routeId, orderId);
  }
}
