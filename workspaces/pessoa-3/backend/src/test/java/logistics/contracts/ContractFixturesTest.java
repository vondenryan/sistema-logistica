package logistics.contracts;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import contracts.v1.DeliverySnapshot;
import contracts.v1.DeliveryStatus;
import contracts.v1.OrderSnapshot;
import contracts.v1.OrderStatus;
import contracts.v1.RouteSnapshot;
import contracts.v1.RouteStatus;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import logistics.deliveries.application.PublicTrackingView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ContractFixturesTest {

  @Autowired private ObjectMapper objectMapper;

  private InputStream loadFixture(String name) {
    InputStream is =
        getClass().getClassLoader().getResourceAsStream("contracts/v1/fixtures/" + name);
    assertNotNull(is, "Missing fixture: " + name);
    return is;
  }

  @Test
  @DisplayName("OrderSnapshot deserializes canonical order-confirmed.json fixture")
  void testOrderConfirmedFixture() throws Exception {
    try (InputStream is = loadFixture("order-confirmed.json")) {
      OrderSnapshot order = objectMapper.readValue(is, OrderSnapshot.class);

      assertEquals(UUID.fromString("44444444-4444-4444-8444-444444444444"), order.orderId());
      assertEquals(UUID.fromString("22222222-2222-4222-8222-222222222222"), order.storeId());
      assertEquals(UUID.fromString("11111111-1111-4111-8111-111111111111"), order.originId());
      assertEquals(OrderStatus.CONFIRMED, order.status());
      assertEquals("TRK-7M4Q9X2K8P", order.trackingCode());
      assertEquals(new BigDecimal("40.000"), order.totalWeightKg());
      assertEquals(new BigDecimal("30.00"), order.freightAmount());
      assertEquals("BRL", order.currency());
      assertNotNull(order.origin());
      assertNotNull(order.destination());
    }
  }

  @Test
  @DisplayName("RouteSnapshot deserializes canonical route-assigned.json fixture")
  void testRouteAssignedFixture() throws Exception {
    try (InputStream is = loadFixture("route-assigned.json")) {
      RouteSnapshot route = objectMapper.readValue(is, RouteSnapshot.class);

      assertEquals(UUID.fromString("33333333-3333-4333-8333-333333333333"), route.routeId());
      assertEquals(UUID.fromString("22222222-2222-4222-8222-222222222222"), route.storeId());
      assertEquals(UUID.fromString("11111111-1111-4111-8111-111111111111"), route.originId());
      assertEquals(RouteStatus.ASSIGNED, route.status());
      assertEquals(
          List.of(UUID.fromString("44444444-4444-4444-8444-444444444444")), route.orderIds());
      assertEquals(UUID.fromString("55555555-5555-4555-8555-555555555555"), route.driverId());
      assertEquals(UUID.fromString("66666666-6666-4666-8666-666666666666"), route.vehicleId());
      assertNull(route.startedAt());
    }
  }

  @Test
  @DisplayName("DeliverySnapshot round-trips canonical delivery-assigned.json fixture")
  void testDeliveryAssignedFixture() throws Exception {
    try (InputStream is = loadFixture("delivery-assigned.json")) {
      JsonNode expectedTree = objectMapper.readTree(is);
      DeliverySnapshot delivery = objectMapper.treeToValue(expectedTree, DeliverySnapshot.class);

      assertEquals(DeliveryStatus.ASSIGNED, delivery.status());
      assertNull(delivery.receiverName());

      JsonNode actualTree = objectMapper.valueToTree(delivery);
      assertEquals(expectedTree, actualTree);
    }
  }

  @Test
  @DisplayName("PublicTrackingView matches tracking-in-transit.json and contains no PII or internal IDs")
  void testTrackingFixtureAndNoSensitiveFields() throws Exception {
    try (InputStream is = loadFixture("tracking-in-transit.json")) {
      JsonNode expectedTree = objectMapper.readTree(is);
      PublicTrackingView view = objectMapper.treeToValue(expectedTree, PublicTrackingView.class);

      assertEquals(DeliveryStatus.IN_TRANSIT, view.status());
      assertEquals(3, view.milestones().size());

      JsonNode actualTree = objectMapper.valueToTree(view);
      assertEquals(expectedTree, actualTree);

      Set<String> forbiddenKeys =
          Set.of(
              "orderId",
              "deliveryId",
              "routeId",
              "recipientName",
              "recipientEmail",
              "destination",
              "origin",
              "receiverName",
              "driverId",
              "freightAmount");
      actualTree.fieldNames().forEachRemaining(k -> assertFalse(forbiddenKeys.contains(k)));
    }
  }
}
