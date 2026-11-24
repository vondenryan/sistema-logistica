package logistics.deliveries.infrastructure.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import contracts.v1.DeliverySnapshot;
import java.util.UUID;
import logistics.deliveries.application.DeliveryService;
import logistics.fakes.FakeCurrentActorPort;
import logistics.fakes.FakeOrderSnapshotPort;
import logistics.fakes.FakeRouteSnapshotPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TrackingControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private DeliveryService deliveryService;
  @Autowired private FakeCurrentActorPort fakeActor;
  @Autowired private TrackingRateLimiter rateLimiter;

  private UUID deliveryId;

  @BeforeEach
  void setUp() {
    rateLimiter.reset();

    fakeActor.asOperator();
    DeliverySnapshot created =
        deliveryService.ensureCreated(
            FakeOrderSnapshotPort.CANONICAL_ORDER_ID, UUID.randomUUID());
    deliveryService.assignRoute(
        FakeOrderSnapshotPort.CANONICAL_ORDER_ID,
        FakeRouteSnapshotPort.CANONICAL_ROUTE_ID,
        UUID.randomUUID());
    deliveryId = created.deliveryId();

    fakeActor.asDriver(FakeRouteSnapshotPort.CANONICAL_DRIVER_ID);
    deliveryService.updateStatus(deliveryId, "IN_TRANSIT", null);
  }

  @Test
  void trackByValidCode_succeedsWithoutAuth_andExposesOnlyStatusAndMilestones() throws Exception {
    // Public tracking endpoint must work even when actor is unauthenticated
    fakeActor.unauthenticated();

    mockMvc
        .perform(
            get("/api/v1/tracking/{code}", FakeOrderSnapshotPort.CANONICAL_TRACKING_CODE)
                .header("X-Forwarded-For", "198.51.100.10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("IN_TRANSIT"))
        .andExpect(jsonPath("$.milestones").isArray())
        .andExpect(jsonPath("$.milestones.length()").value(3))
        .andExpect(jsonPath("$.milestones[0].status").value("PENDING"))
        .andExpect(jsonPath("$.milestones[1].status").value("ASSIGNED"))
        .andExpect(jsonPath("$.milestones[2].status").value("IN_TRANSIT"))
        .andExpect(jsonPath("$.orderId").doesNotExist())
        .andExpect(jsonPath("$.deliveryId").doesNotExist())
        .andExpect(jsonPath("$.routeId").doesNotExist())
        .andExpect(jsonPath("$.receiverName").doesNotExist())
        .andExpect(jsonPath("$.recipientName").doesNotExist())
        .andExpect(jsonPath("$.recipientEmail").doesNotExist())
        .andExpect(jsonPath("$.destination").doesNotExist())
        .andExpect(content().string(not(containsString("Cliente Fictício"))))
        .andExpect(content().string(not(containsString("Rua Destino"))));
  }

  @Test
  void trackByUnknownCode_returns404ProblemDetail() throws Exception {
    fakeActor.unauthenticated();

    mockMvc
        .perform(
            get("/api/v1/tracking/{code}", "TRK-UNKNOWN-999")
                .header("X-Forwarded-For", "198.51.100.11"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  void trackByOrderUuid_returnsGeneric404ToPreventEnumeration() throws Exception {
    fakeActor.unauthenticated();

    // Passing internal orderId as tracking code must fail with generic 404
    mockMvc
        .perform(
            get("/api/v1/tracking/{code}", FakeOrderSnapshotPort.CANONICAL_ORDER_ID.toString())
                .header("X-Forwarded-For", "198.51.100.12"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  void trackByOverlyLongCode_returnsGeneric404() throws Exception {
    fakeActor.unauthenticated();

    mockMvc
        .perform(
            get("/api/v1/tracking/{code}", "A".repeat(80))
                .header("X-Forwarded-For", "198.51.100.13"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  void trackingRateLimit_returns429WithRetryAfterHeader() throws Exception {
    fakeActor.unauthenticated();
    String probeIp = "203.0.113.77";

    for (int i = 0; i < 30; i++) {
      mockMvc
          .perform(
              get("/api/v1/tracking/{code}", "TRK-PROBE-" + i)
                  .header("X-Forwarded-For", probeIp))
          .andExpect(status().isNotFound());
    }

    // 31st request from same IP must be rate-limited
    mockMvc
        .perform(
            get("/api/v1/tracking/{code}", FakeOrderSnapshotPort.CANONICAL_TRACKING_CODE)
                .header("X-Forwarded-For", probeIp))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(429))
        .andExpect(jsonPath("$.code").value("RATE_LIMITED"));

    // Different client IP is not affected
    mockMvc
        .perform(
            get("/api/v1/tracking/{code}", FakeOrderSnapshotPort.CANONICAL_TRACKING_CODE)
                .header("X-Forwarded-For", "203.0.113.88"))
        .andExpect(status().isOk());
  }
}
