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
  @SuppressWarnings("unchecked")
  Fixtures fixture() {
    ObjectProvider<OrderSnapshotPort> orderProvider = mock(ObjectProvider.class);
    ObjectProvider<RouteSnapshotPort> routeProvider = mock(ObjectProvider.class);
    OrderSnapshotPort orders = mock(OrderSnapshotPort.class);
    RouteSnapshotPort routes = mock(RouteSnapshotPort.class);
    when(orderProvider.getObject()).thenReturn(orders);
    when(routeProvider.getObject()).thenReturn(routes);
    when(routes.getById(any()))
        .thenAnswer(
            call ->
                new RouteSnapshot(
                    call.getArgument(0),
                    Fixtures.STORE,
                    Fixtures.ORIGIN,
                    RouteStatus.PLANNED,
                    List.of(UUID.fromString("44444444-4444-4444-8444-444444444444")),
                    null,
                    null,
                    null));
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
    return new Fixtures(orderProvider, routeProvider);
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
