package logistics.identity.infrastructure;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import contracts.v1.*;
import java.math.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.*;

class FakesTest {
  @Test
  void satisfiesPartnerLookupContract() {
    PartnerLookupContract.verify(
        fixture(),
        Fixtures.ORIGIN,
        UUID.fromString("11111111-1111-4111-8111-111111111113"),
        Fixtures.STORE,
        UUID.randomUUID());
  }

  @SuppressWarnings("unchecked")
  Fixtures fixture() {
    return fixture(new java.util.concurrent.atomic.AtomicReference<>(RouteStatus.PLANNED));
  }

  @SuppressWarnings("unchecked")
  Fixtures fixture(java.util.concurrent.atomic.AtomicReference<RouteStatus> routeStatus) {
    ObjectProvider<OrderSnapshotPort> orderProvider = mock(ObjectProvider.class);
    ObjectProvider<RouteSnapshotPort> routeProvider = mock(ObjectProvider.class);
    OrderSnapshotPort orders = mock(OrderSnapshotPort.class);
    RouteSnapshotPort routes = mock(RouteSnapshotPort.class);
    when(orderProvider.getObject()).thenReturn(orders);
    when(routeProvider.getObject()).thenReturn(routes);
    Fixtures[] holder = new Fixtures[1];
    when(routes.getById(any()))
        .thenAnswer(
            call -> {
              assertThat(Thread.holdsLock(holder[0])).isFalse();
              return new RouteSnapshot(
                  call.getArgument(0),
                  Fixtures.STORE,
                  Fixtures.ORIGIN,
                  routeStatus.get(),
                  List.of(UUID.fromString("44444444-4444-4444-8444-444444444444")),
                  null,
                  null,
                  null);
            });
    when(orders.getById(any()))
        .thenAnswer(
            call ->
                new OrderSnapshot(
                    call.getArgument(0),
                    Fixtures.STORE,
                    Fixtures.ORIGIN,
                    OrderStatus.CONFIRMED,
                    Fixtures.ADDRESS,
                    Fixtures.ADDRESS,
                    "name",
                    null,
                    "tracking",
                    new BigDecimal("1.000"),
                    null,
                    "BRL"));
    holder[0] = new Fixtures(orderProvider, routeProvider);
    return holder[0];
  }

  @Test
  void notificationsDeduplicateByEventAndType() {
    Fixtures f = fixture();
    UUID event = UUID.randomUUID(), order = UUID.randomUUID();
    Instant time = Instant.parse("2026-11-14T12:00:00Z");
    var a =
        new NotificationRequest(
            event, NotificationEventType.ORDER_CONFIRMED, order, "a@example.invalid", time);
    var b =
        new NotificationRequest(
            event, NotificationEventType.DELIVERY_DELIVERED, order, "a@example.invalid", time);
    assertThat(f.enqueue(a)).isEqualTo(f.enqueue(a));
    assertThat(f.enqueue(a).notificationId()).isNotEqualTo(f.enqueue(b).notificationId());
    assertThatThrownBy(
            () ->
                f.enqueue(
                    new NotificationRequest(
                        event,
                        NotificationEventType.ORDER_CONFIRMED,
                        order,
                        "other@example.invalid",
                        time)))
        .isInstanceOf(ContractException.class)
        .hasMessage("IDEMPOTENCY_CONFLICT");
  }

  @Test
  void assignmentIsConcurrentAndIdempotent() throws Exception {
    Fixtures f = fixture();
    UUID route = UUID.randomUUID();
    AssignmentRequest a = new AssignmentRequest(route, Fixtures.ORIGIN, new BigDecimal("1.000"));
    try (var pool = Executors.newFixedThreadPool(8)) {
      var tasks = new ArrayList<Callable<AssignmentSnapshot>>();
      for (int i = 0; i < 20; i++) tasks.add(() -> f.assign(a));
      var all = pool.invokeAll(tasks);
      UUID first = all.get(0).get().assignmentId();
      for (var future : all) assertThat(future.get().assignmentId()).isEqualTo(first);
    }
    assertThatThrownBy(
            () ->
                f.assign(
                    new AssignmentRequest(
                        UUID.randomUUID(), Fixtures.ORIGIN, new BigDecimal("1.000"))))
        .isInstanceOf(ContractException.class)
        .hasMessage("NO_ELIGIBLE_RESOURCE");
    assertThatThrownBy(
            () -> f.assign(new AssignmentRequest(route, Fixtures.ORIGIN, BigDecimal.TEN)))
        .isInstanceOf(ContractException.class)
        .hasMessage("IDEMPOTENCY_CONFLICT");
  }

  @Test
  void fleetReleaseRequiresCompletedRouteAndKeepsVehicleReservedUntilThen() {
    var status = new java.util.concurrent.atomic.AtomicReference<>(RouteStatus.PLANNED);
    Fixtures f = fixture(status);
    UUID route = UUID.randomUUID();
    UUID next = UUID.randomUUID();
    f.assign(new AssignmentRequest(route, Fixtures.ORIGIN, new BigDecimal("1.000")));
    var release = new ReleaseRequest(route, UUID.randomUUID(), ReleaseReason.ROUTE_COMPLETED);
    status.set(RouteStatus.IN_PROGRESS);
    assertThatThrownBy(() -> f.release(release))
        .isInstanceOf(ContractException.class)
        .hasMessage("PRECONDITION_FAILED");
    status.set(RouteStatus.PLANNED);
    assertThatThrownBy(
            () -> f.assign(new AssignmentRequest(next, Fixtures.ORIGIN, new BigDecimal("1.000"))))
        .isInstanceOf(ContractException.class)
        .hasMessage("NO_ELIGIBLE_RESOURCE");
    status.set(RouteStatus.COMPLETED);
    assertThat(f.release(release).released()).isTrue();
    assertThat(f.release(release).released()).isFalse();
    status.set(RouteStatus.PLANNED);
    assertThat(f.assign(new AssignmentRequest(next, Fixtures.ORIGIN, new BigDecimal("1.000"))))
        .isNotNull();
  }

  @Test
  @SuppressWarnings("unchecked")
  void assignmentDoesNotHoldReservationMonitorDuringSnapshotReads() {
    ObjectProvider<OrderSnapshotPort> orderProvider = mock(ObjectProvider.class);
    ObjectProvider<RouteSnapshotPort> routeProvider = mock(ObjectProvider.class);
    OrderSnapshotPort orders = mock(OrderSnapshotPort.class);
    RouteSnapshotPort routes = mock(RouteSnapshotPort.class);
    when(orderProvider.getObject()).thenReturn(orders);
    when(routeProvider.getObject()).thenReturn(routes);
    Fixtures f = new Fixtures(orderProvider, routeProvider);
    UUID orderId = UUID.randomUUID();
    when(routes.getById(any()))
        .thenAnswer(
            call -> {
              assertThat(Thread.holdsLock(f)).isFalse();
              return new RouteSnapshot(
                  call.getArgument(0),
                  Fixtures.STORE,
                  Fixtures.ORIGIN,
                  RouteStatus.PLANNED,
                  List.of(orderId),
                  null,
                  null,
                  null);
            });
    when(orders.getById(orderId))
        .thenAnswer(
            call -> {
              assertThat(Thread.holdsLock(f)).isFalse();
              return new OrderSnapshot(
                  orderId,
                  Fixtures.STORE,
                  Fixtures.ORIGIN,
                  OrderStatus.CONFIRMED,
                  Fixtures.ADDRESS,
                  Fixtures.ADDRESS,
                  "name",
                  null,
                  "tracking",
                  BigDecimal.ONE,
                  null,
                  "BRL");
            });
    assertThat(f.assign(new AssignmentRequest(UUID.randomUUID(), Fixtures.ORIGIN, BigDecimal.ONE)))
        .isNotNull();
  }

  @Test
  void unavailableAndMalformedRequestsAreContractErrors() {
    Fixtures f = fixture();
    f.setUnavailable(true);
    assertThatThrownBy(() -> f.getStore(Fixtures.STORE))
        .isInstanceOf(ContractException.class)
        .hasMessage("DEPENDENCY_UNAVAILABLE");
    assertThatThrownBy(() -> f.getByOrderId(UUID.randomUUID()))
        .isInstanceOf(ContractException.class)
        .hasMessage("DEPENDENCY_UNAVAILABLE");
    f.setUnavailable(false);
    assertThatThrownBy(() -> f.ensureGenerated(new DocumentRequest(UUID.randomUUID(), null)))
        .isInstanceOf(ContractException.class)
        .hasMessage("VALIDATION_ERROR");
    assertThatThrownBy(() -> f.assign(null))
        .isInstanceOf(ContractException.class)
        .hasMessage("VALIDATION_ERROR");
  }

  @Test
  void loginRateLimitUsesMatchingProblemStatus() throws Exception {
    var filter = new LoginRateLimitFilter(new ObjectMapper().findAndRegisterModules());
    for (int n = 0; n < 31; n++) {
      var req = new MockHttpServletRequest("POST", "/api/v1/auth/login");
      req.setRemoteAddr("192.0.2.1");
      var res = new MockHttpServletResponse();
      filter.doFilter(req, res, new MockFilterChain());
      if (n == 30) {
        assertThat(res.getStatus()).isEqualTo(429);
        assertThat(res.getHeader("Retry-After")).isEqualTo("60");
        assertThat(res.getContentAsString()).contains("\"status\":429");
      }
    }
  }

  @Test
  void refusesMixedProductionAndDevelopmentProfiles() {
    var env = new org.springframework.mock.env.MockEnvironment();
    env.setActiveProfiles("dev", "prod");
    assertThatThrownBy(() -> new SecurityConfig().profileGuard(env).afterPropertiesSet())
        .isInstanceOf(IllegalStateException.class);
    env.setActiveProfiles("test", "integration");
    assertThatThrownBy(() -> new SecurityConfig().profileGuard(env).afterPropertiesSet())
        .isInstanceOf(IllegalStateException.class);
  }
}
