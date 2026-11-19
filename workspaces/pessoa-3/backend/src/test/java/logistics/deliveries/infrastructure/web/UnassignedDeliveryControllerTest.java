package logistics.deliveries.infrastructure.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import contracts.v1.DeliveryStatus;
import java.util.UUID;
import logistics.deliveries.application.DeliveryService;
import logistics.fakes.FakeCurrentActorPort;
import logistics.fakes.FakeOrderSnapshotPort;
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
class UnassignedDeliveryControllerTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private DeliveryService deliveryService;
  @Autowired private FakeCurrentActorPort fakeActor;

  @Test
  void patchAssignedWithoutRouteReturnsConflictAndPreservesDeliveryAndHistory() throws Exception {
    fakeActor.asOperator();
    var pending =
        deliveryService.ensureCreated(FakeOrderSnapshotPort.CANONICAL_ORDER_ID, UUID.randomUUID());
    var stored = deliveryService.getById(pending.deliveryId());
    var history = deliveryService.getEvents(pending.deliveryId());
    assertEquals(DeliveryStatus.PENDING, pending.status());
    assertNull(pending.routeId());

    mockMvc
        .perform(
            patch("/api/v1/deliveries/{id}/status", pending.deliveryId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ASSIGNED\"}"))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.code").value("CONFLICT"));

    assertEquals(stored, deliveryService.getById(pending.deliveryId()));
    assertEquals(history, deliveryService.getEvents(pending.deliveryId()));
  }
}
