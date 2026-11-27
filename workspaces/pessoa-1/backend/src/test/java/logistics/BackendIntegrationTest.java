package logistics;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import contracts.v1.ContractException;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:logistics;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "app.jwt-secret=test-only-secret-at-least-32-bytes",
      "app.demo-password=test-only-password",
      "app.reconcile-delay-ms=3600000",
      "ROUTING_PROVIDER=fake"
    })
@ActiveProfiles("dev")
@AutoConfigureMockMvc
class BackendIntegrationTest {
  @Autowired logistics.identity.infrastructure.Fixtures fixtures;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;

  String login(String email) throws Exception {
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

  String payload() {
    return """
    {"storeId":"22222222-2222-4222-8222-222222222222","originId":"11111111-1111-4111-8111-111111111111","destination":{"line1":"Rua Destino","line2":null,"city":"Cidade","state":"SC","postalCode":"00000-000","country":"BR","latitude":-26.49,"longitude":-49.08},"recipientName":"Cliente","recipientEmail":"client@example.invalid","items":[{"description":"Produto","quantity":2,"unitWeightKg":"20.000"}]}
    """;
  }

  @Test
  void partnerLookupFixtureMatchesV1OriginAndStoreBehavior() {
    var activeOrigin = fixtures.getOrigin(
        java.util.UUID.fromString("11111111-1111-4111-8111-111111111111"));
    assertThat(activeOrigin.active()).isTrue();
    assertThat(fixtures.getStore(activeOrigin.storeId()).id()).isEqualTo(activeOrigin.storeId());

    var inactiveOrigin = fixtures.getOrigin(
        java.util.UUID.fromString("11111111-1111-4111-8111-111111111113"));
    assertThat(inactiveOrigin.active()).isFalse();
    assertThat(inactiveOrigin.storeId()).isEqualTo(activeOrigin.storeId());

    var linkedOrigin = fixtures.getOrigin(
        java.util.UUID.fromString("11111111-1111-4111-8111-111111111112"));
    assertThat(linkedOrigin.active()).isTrue();
    assertThat(fixtures.getStore(linkedOrigin.storeId()).id()).isEqualTo(linkedOrigin.storeId());

    assertThatThrownBy(() -> fixtures.getOrigin(java.util.UUID.randomUUID()))
        .isInstanceOfSatisfying(
            ContractException.class, exception -> assertThat(exception.code()).isEqualTo("NOT_FOUND"));
    assertThatThrownBy(() -> fixtures.getStore(java.util.UUID.randomUUID()))
        .isInstanceOfSatisfying(
            ContractException.class, exception -> assertThat(exception.code()).isEqualTo("NOT_FOUND"));
  }

  @Test
  void loginAndCrossStoreAndDriver() throws Exception {
    String a = login("store@example.invalid"),
        b = login("other-store@example.invalid"),
        d = login("driver@example.invalid");
    String result =
        mvc.perform(
                post("/api/v1/orders")
                    .header("Authorization", "Bearer " + a)
                    .contentType("application/json")
                    .content(payload()))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = json.readTree(result).get("orderId").asText();
    mvc.perform(get("/api/v1/orders/" + id).header("Authorization", "Bearer " + b))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/orders/" + id).header("Authorization", "Bearer " + d))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer bad"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().exists("X-Request-Id"));
    mvc.perform(
            post("/api/v1/auth/login")
                .contentType("application/json")
                .content("{\"email\":\"store@example.invalid\",\"password\":\"wrong\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void confirmIdempotentlyAndValidate() throws Exception {
    String token = login("store@example.invalid");
    String result =
        mvc.perform(
                post("/api/v1/orders")
                    .header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .content(payload()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.totalWeightKg").value("40.000"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = json.readTree(result).get("orderId").asText();
    for (int i = 0; i < 2; i++)
      mvc.perform(
              post("/api/v1/orders/" + id + "/confirm")
                  .header("Authorization", "Bearer " + token)
                  .contentType("application/json")
                  .content("{}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("CONFIRMED"));
    assertThat(
            db.queryForObject(
                "select count(*) from billing.simulated_payments where order_id=?",
                Integer.class,
                java.util.UUID.fromString(id)))
        .isEqualTo(1);
    mvc.perform(
            post("/api/v1/orders/" + id + "/cancel")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"reason\":\"test\"}"))
        .andExpect(status().isConflict());
    mvc.perform(
            post("/api/v1/orders")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(payload().replace("\"20.000\"", "20.000")))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/v1/orders")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(payload().replace("-26.49", "91")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void recoversAfterDeliveryDependencyFailure() throws Exception {
    String token = login("store@example.invalid");
    String result =
        mvc.perform(
                post("/api/v1/orders")
                    .header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .content(payload()))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = json.readTree(result).get("orderId").asText();
    int before = fixtures.deliveryCount();
    fixtures.setDeliveryUnavailable(true);
    try {
      mvc.perform(
              post("/api/v1/orders/" + id + "/confirm")
                  .header("Authorization", "Bearer " + token)
                  .contentType("application/json")
                  .content("{}"))
          .andExpect(status().isServiceUnavailable());
      assertThat(
              db.queryForObject(
                  "select status from orders.orders where id=?",
                  String.class,
                  java.util.UUID.fromString(id)))
          .isEqualTo("CONFIRMED");
    } finally {
      fixtures.setDeliveryUnavailable(false);
    }
    for (int n = 0; n < 2; n++)
      mvc.perform(
              post("/api/v1/orders/" + id + "/confirm")
                  .header("Authorization", "Bearer " + token)
                  .contentType("application/json")
                  .content("{}"))
          .andExpect(status().isOk());
    assertThat(fixtures.deliveryCount()).isEqualTo(before + 1);
    assertThat(
            db.queryForObject(
                "select count(*) from billing.simulated_payments where order_id=?",
                Integer.class,
                java.util.UUID.fromString(id)))
        .isEqualTo(1);
  }

  @Test
  void routeWorkflowAndInvalidRequests() throws Exception {
    String admin = login("admin@example.invalid");
    String store = login("store@example.invalid");
    String driver = login("driver@example.invalid");
    String order =
        json.readTree(
                mvc.perform(
                        post("/api/v1/orders")
                            .header("Authorization", "Bearer " + store)
                            .contentType("application/json")
                            .content(payload()))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("orderId")
            .asText();
    mvc.perform(
            post("/api/v1/orders/" + order + "/confirm")
                .header("Authorization", "Bearer " + store)
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isOk());
    String request =
        "{\"originId\":\"11111111-1111-4111-8111-111111111111\",\"orderIds\":[\"" + order + "\"]}";
    mvc.perform(
            post("/api/v1/routes/suggest")
                .header("Authorization", "Bearer " + admin)
                .contentType("application/json")
                .content(request))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/v1/routes")
                .header("Authorization", "Bearer " + store)
                .contentType("application/json")
                .content(request))
        .andExpect(status().isForbidden());
    String route =
        json.readTree(
                mvc.perform(
                        post("/api/v1/routes")
                            .header("Authorization", "Bearer " + admin)
                            .contentType("application/json")
                            .content(request))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("routeId")
            .asText();
    mvc.perform(get("/api/v1/routes/" + route).header("Authorization", "Bearer " + driver))
        .andExpect(status().isNotFound());
    mvc.perform(
            put("/api/v1/routes/" + route + "/stops")
                .header("Authorization", "Bearer " + admin)
                .contentType("application/json")
                .content("{\"orderedOrderIds\":[\"" + order + "\"]}"))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/v1/routes/" + route + "/assign")
                .header("Authorization", "Bearer " + admin)
                .contentType("application/json")
                .content("null"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    mvc.perform(get("/api/v1/does-not-exist").header("Authorization", "Bearer " + admin))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    mvc.perform(
            post("/api/v1/routes/" + route + "/assign")
                .header("Authorization", "Bearer " + admin)
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/v1/routes/" + route + "/start")
                .header("Authorization", "Bearer " + admin)
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isOk());
  }

  @Test
  void rejectsExpiredAndForgedJwt() throws Exception {
    var claims =
        new com.nimbusds.jwt.JWTClaimsSet.Builder()
            .subject("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1")
            .claim("role", "ADMIN")
            .expirationTime(new java.util.Date(0))
            .build();
    var expired =
        new com.nimbusds.jwt.SignedJWT(
            new com.nimbusds.jose.JWSHeader(com.nimbusds.jose.JWSAlgorithm.HS256), claims);
    expired.sign(
        new com.nimbusds.jose.crypto.MACSigner(
            "test-only-secret-at-least-32-bytes"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + expired.serialize()))
        .andExpect(status().isUnauthorized());
    String token = login("admin@example.invalid");
    String[] parts = token.split("\\.");
    mvc.perform(
            get("/api/v1/auth/me")
                .header(
                    "Authorization",
                    "Bearer "
                        + parts[0]
                        + "."
                        + parts[1]
                        + "."
                        + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"))
        .andExpect(status().isUnauthorized());
  }
}
