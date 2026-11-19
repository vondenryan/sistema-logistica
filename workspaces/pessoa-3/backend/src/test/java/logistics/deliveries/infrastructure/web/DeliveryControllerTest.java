package logistics.deliveries.infrastructure.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class DeliveryControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private DeliveryService deliveryService;
  @Autowired private FakeCurrentActorPort fakeActor;

  private UUID deliveryId;

  @BeforeEach
  void setUp() {
    // Create delivery as operator
    fakeActor.asOperator();
    DeliverySnapshot snap =
        deliveryService.ensureCreated(
            FakeOrderSnapshotPort.CANONICAL_ORDER_ID, UUID.randomUUID());

    // Assign route so the delivery enters ASSIGNED state
    deliveryService.assignRoute(
        FakeOrderSnapshotPort.CANONICAL_ORDER_ID,
        FakeRouteSnapshotPort.CANONICAL_ROUTE_ID,
        UUID.randomUUID());

    deliveryId = snap.deliveryId();
  }

  // ------------------------------------------------------------------ //
  //  PATCH /api/v1/deliveries/{id}/status                               //
  // ------------------------------------------------------------------ //

  @Test
  void patchStatus_inTransit_succeeds() throws Exception {
    fakeActor.asDriver(FakeRouteSnapshotPort.CANONICAL_DRIVER_ID);

    mockMvc
        .perform(
            patch("/api/v1/deliveries/{id}/status", deliveryId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"IN_TRANSIT\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("IN_TRANSIT"));
  }

  @Test
  void patchStatus_failedAttempt_withReason_succeeds() throws Exception {
    fakeActor.asDriver(FakeRouteSnapshotPort.CANONICAL_DRIVER_ID);

    // First move to IN_TRANSIT
    deliveryService.updateStatus(deliveryId, "IN_TRANSIT", null);

    mockMvc
        .perform(
            patch("/api/v1/deliveries/{id}/status", deliveryId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"FAILED_ATTEMPT\",\"reason\":\"Nobody answered the door\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("FAILED_ATTEMPT"));
  }

  @Test
  void patchStatus_failedAttempt_withoutReason_returns400() throws Exception {
    fakeActor.asDriver(FakeRouteSnapshotPort.CANONICAL_DRIVER_ID);

    // Move to IN_TRANSIT first
    deliveryService.updateStatus(deliveryId, "IN_TRANSIT", null);

    mockMvc
        .perform(
            patch("/api/v1/deliveries/{id}/status", deliveryId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"FAILED_ATTEMPT\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void patchStatus_delivered_returns400() throws Exception {
    fakeActor.asDriver(FakeRouteSnapshotPort.CANONICAL_DRIVER_ID);

    mockMvc
        .perform(
            patch("/api/v1/deliveries/{id}/status", deliveryId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"DELIVERED\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void patchStatus_cancelled_returns400() throws Exception {
    // [P2] Contract v1 forbids CANCELLED on this endpoint — cancellation rule is pending
    fakeActor.asOperator();

    mockMvc
        .perform(
            patch("/api/v1/deliveries/{id}/status", deliveryId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"CANCELLED\"}"))
        .andExpect(status().isBadRequest());
  }

  // ------------------------------------------------------------------ //
  //  POST /api/v1/deliveries/{id}/complete                              //
  // ------------------------------------------------------------------ //

  @Test
  void complete_withReceiverName_succeeds() throws Exception {
    fakeActor.asDriver(FakeRouteSnapshotPort.CANONICAL_DRIVER_ID);

    // Move to IN_TRANSIT first
    deliveryService.updateStatus(deliveryId, "IN_TRANSIT", null);

    mockMvc
        .perform(
            post("/api/v1/deliveries/{id}/complete", deliveryId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"receiverName\":\"Maria Silva\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DELIVERED"));
  }

  @Test
  void complete_withBlankReceiverName_returns400() throws Exception {
    fakeActor.asDriver(FakeRouteSnapshotPort.CANONICAL_DRIVER_ID);

    // Move to IN_TRANSIT first
    deliveryService.updateStatus(deliveryId, "IN_TRANSIT", null);

    mockMvc
        .perform(
            post("/api/v1/deliveries/{id}/complete", deliveryId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"receiverName\":\"   \"}"))
        .andExpect(status().isBadRequest());
  }

  // ------------------------------------------------------------------ //
  //  GET /api/v1/deliveries/{id}/events                                 //
  // ------------------------------------------------------------------ //

  @Test
  void getEvents_returnsHistory() throws Exception {
    fakeActor.asOperator();

    mockMvc
        .perform(get("/api/v1/deliveries/{id}/events", deliveryId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
  }
}
