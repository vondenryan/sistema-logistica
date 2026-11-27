package logistics.documents.infrastructure.web;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import contracts.v1.AddressSnapshot;
import contracts.v1.OrderSnapshot;
import contracts.v1.OrderStatus;
import java.math.BigDecimal;
import java.util.UUID;
import logistics.documents.application.PdfDocumentGenerator;
import logistics.fakes.FakeCurrentActorPort;
import logistics.fakes.FakeOrderSnapshotPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DocumentControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private FakeCurrentActorPort fakeActor;
  @Autowired private FakeOrderSnapshotPort fakeOrders;

  @BeforeEach
  void setUp() {
    fakeOrders.resetToCanonical();
    fakeActor.reset();
  }

  @Test
  void postDocument_isIdempotentAndGeneratesPdfWithAcademicWatermark() throws Exception {
    fakeActor.asOperator();

    String payload =
        "{\"orderId\":\""
            + FakeOrderSnapshotPort.CANONICAL_ORDER_ID
            + "\",\"documentType\":\"CTE_SIMULATED\"}";

    MvcResult first =
        mockMvc
            .perform(
                post("/api/v1/documents")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.orderId").value(FakeOrderSnapshotPort.CANONICAL_ORDER_ID.toString()))
            .andExpect(jsonPath("$.documentType").value("CTE_SIMULATED"))
            .andExpect(jsonPath("$.status").value("GENERATED"))
            .andReturn();

    JsonNode firstJson = objectMapper.readTree(first.getResponse().getContentAsString());
    String documentId = firstJson.get("documentId").asText();

    // Repeated call returns 200 OK with the exact same documentId
    mockMvc
        .perform(
            post("/api/v1/documents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.documentId").value(documentId));

    // GET receipt
    mockMvc
        .perform(get("/api/v1/documents/{id}", documentId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.documentId").value(documentId));

    // GET PDF bytes and verify academic watermark text
    byte[] pdfBytes =
        mockMvc
            .perform(get("/api/v1/documents/{id}/pdf", documentId))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_PDF))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

    assertTrue(pdfBytes.length > 100);
    try (PdfReader reader = new PdfReader(pdfBytes)) {
      PdfTextExtractor extractor = new PdfTextExtractor(reader);
      String text = extractor.getTextFromPage(1);
      assertTrue(
          text.contains(PdfDocumentGenerator.ACADEMIC_WATERMARK),
          "PDF must contain academic watermark, got: " + text);
    }
  }

  @Test
  void postDocument_withoutFreightQuote_returns422PreconditionFailed() throws Exception {
    UUID unquotedOrderId = UUID.randomUUID();
    AddressSnapshot addr =
        new AddressSnapshot(
            "Rua Sem Frete, 10", null, "Cidade", "SC", "00000-000", "BR", BigDecimal.ZERO, BigDecimal.ZERO);
    fakeOrders.register(
        new OrderSnapshot(
            unquotedOrderId,
            FakeOrderSnapshotPort.CANONICAL_STORE_ID,
            FakeOrderSnapshotPort.CANONICAL_ORIGIN_ID,
            OrderStatus.CONFIRMED,
            addr,
            addr,
            "Cliente",
            null,
            "TRK-UNQUOTED",
            new BigDecimal("10.000"),
            null,
            "BRL"));

    fakeActor.asOperator();
    mockMvc
        .perform(
            post("/api/v1/documents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"orderId\":\"" + unquotedOrderId + "\",\"documentType\":\"NFE_SIMULATED\"}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("PRECONDITION_FAILED"));
  }

  @Test
  void postDocument_invalidType_returns400() throws Exception {
    fakeActor.asOperator();
    mockMvc
        .perform(
            post("/api/v1/documents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"orderId\":\""
                        + FakeOrderSnapshotPort.CANONICAL_ORDER_ID
                        + "\",\"documentType\":\"NFE\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  @Test
  void documentsAuthorization_enforcesStoreScopeDriverForbiddenAndUnauthenticated()
      throws Exception {
    String payload =
        "{\"orderId\":\""
            + FakeOrderSnapshotPort.CANONICAL_ORDER_ID
            + "\",\"documentType\":\"NFE_SIMULATED\"}";

    // Unauthenticated -> 401
    fakeActor.unauthenticated();
    mockMvc
        .perform(
            post("/api/v1/documents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

    // Driver -> 403
    fakeActor.asDriver(FakeCurrentActorPort.DEFAULT_DRIVER_ID);
    mockMvc
        .perform(
            post("/api/v1/documents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));

    // Cross-store STORE actor -> 404 (hide existence)
    fakeActor.asStore(UUID.randomUUID());
    mockMvc
        .perform(
            post("/api/v1/documents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));

    // Owning STORE actor -> 201 Created
    fakeActor.asStore(FakeOrderSnapshotPort.CANONICAL_STORE_ID);
    mockMvc
        .perform(
            post("/api/v1/documents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("GENERATED"));
  }
}
