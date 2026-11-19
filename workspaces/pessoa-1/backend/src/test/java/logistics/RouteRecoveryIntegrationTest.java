package logistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import contracts.v1.AssignmentSnapshot;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.UUID;
import logistics.identity.infrastructure.Fixtures;
import logistics.routing.application.RouteStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:route_recovery;MODE=PostgreSQL;DB_CLOSE_DELAY=0",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "app.jwt-secret=test-only-secret-at-least-32-bytes",
      "app.demo-password=test-only-password",
      "app.reconcile-delay-ms=3600000",
      "ROUTING_PROVIDER=fake"
    })
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RouteRecoveryIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;
  @Autowired Fixtures fixtures;
  @MockitoSpyBean RouteStore routeStore;

  private String login(String email) throws Exception {
    String response =
        mvc.perform(
                post("/api/v1/auth/login")
                    .contentType("application/json")
                    .content(
                        json.writeValueAsString(
                            java.util.Map.of("email", email, "password", "test-only-password"))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(response).get("accessToken").asText();
  }

  private String otherDriverToken() throws Exception {
    var claims =
        new JWTClaimsSet.Builder()
            .subject(UUID.randomUUID().toString())
            .claim("role", "DRIVER")
            .claim("driverId", "55555555-5555-4555-8555-555555555556")
            .expirationTime(new Date(System.currentTimeMillis() + 600_000))
            .build();
    var token = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
    token.sign(
        new MACSigner("test-only-secret-at-least-32-bytes".getBytes(StandardCharsets.UTF_8)));
    return token.serialize();
  }

  private String createConfirmedOrder(String token) throws Exception {
    String payload =
        """
        {"storeId":"22222222-2222-4222-8222-222222222222","originId":"11111111-1111-4111-8111-111111111111","destination":{"line1":"Rua Destino","line2":null,"city":"Cidade","state":"SC","postalCode":"00000-000","country":"BR","latitude":-26.49,"longitude":-49.08},"recipientName":"Cliente","recipientEmail":"client@example.invalid","items":[{"description":"Produto","quantity":2,"unitWeightKg":"20.000"}]}
        """;
    String response =
        mvc.perform(
                post("/api/v1/orders")
                    .header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .content(payload))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = json.readTree(response).get("orderId").asText();
    mvc.perform(
            post("/api/v1/orders/" + id + "/confirm")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isOk());
    return id;
  }

  private String createRoute(String token, String order) throws Exception {
    String payload =
        "{\"originId\":\"11111111-1111-4111-8111-111111111111\",\"orderIds\":[\"" + order + "\"]}";
    String response =
        mvc.perform(
                post("/api/v1/routes")
                    .header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .content(payload))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(response).get("routeId").asText();
  }

  private org.springframework.test.web.servlet.ResultActions action(
      String route, String action, String token) throws Exception {
    return mvc.perform(
        post("/api/v1/routes/" + route + "/" + action)
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .content("{}"));
  }

  private int payments(String order) {
    return db.queryForObject(
        "select count(*) from billing.simulated_payments where order_id=?",
        Integer.class,
        UUID.fromString(order));
  }

  @Test
  void deliveryFailurePersistsAssignmentAndRetryAllowsStartAndDriverAccess() throws Exception {
    String admin = login("admin@example.invalid");
    String order = createConfirmedOrder(admin);
    String route = createRoute(admin, order);
    fixtures.setDeliveryUnavailable(true);
    try {
      action(route, "assign", admin)
          .andExpect(status().isServiceUnavailable())
          .andExpect(jsonPath("$.code").value("DEPENDENCY_UNAVAILABLE"));
      mvc.perform(get("/api/v1/routes/" + route).header("Authorization", "Bearer " + admin))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("ASSIGNED"));
      action(route, "start", admin).andExpect(status().isConflict());
    } finally {
      fixtures.setDeliveryUnavailable(false);
    }
    action(route, "assign", admin)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ASSIGNED"));
    action(route, "assign", admin).andExpect(status().isOk());
    assertThat(fixtures.deliveryCount()).isEqualTo(1);
    assertThat(payments(order)).isEqualTo(1);
    String driver = login("driver@example.invalid");
    mvc.perform(get("/api/v1/routes/" + route).header("Authorization", "Bearer " + driver))
        .andExpect(status().isOk());
    mvc.perform(
            get("/api/v1/routes/" + route).header("Authorization", "Bearer " + otherDriverToken()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    action(route, "start", admin)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
  }

  @Test
  void sqlAssignmentFailureRollsBackAndRetryReusesReservation() throws Exception {
    String admin = login("admin@example.invalid");
    String order = createConfirmedOrder(admin);
    String route = createRoute(admin, order);
    var seen = new ArrayList<AssignmentSnapshot>();
    doAnswer(
            call -> {
              seen.add(call.getArgument(1));
              Object result = call.callRealMethod();
              if (seen.size() == 1)
                throw new DataAccessResourceFailureException("injected failure");
              return result;
            })
        .when(routeStore)
        .assign(any(UUID.class), any(AssignmentSnapshot.class));

    action(route, "assign", admin)
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    assertThat(
            db.queryForObject(
                "select status from routing.routes where id=?",
                String.class,
                UUID.fromString(route)))
        .isEqualTo("PLANNED");
    action(route, "assign", admin)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ASSIGNED"));
    assertThat(seen).hasSize(2);
    assertThat(seen.get(1)).isEqualTo(seen.get(0));
    assertThat(payments(order)).isEqualTo(1);
    action(route, "start", admin).andExpect(status().isOk());
  }
}
