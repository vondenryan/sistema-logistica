package logistics.documents.domain;

import contracts.v1.DocumentReceipt;
import contracts.v1.DocumentStatus;
import contracts.v1.DocumentType;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record SimulatedDocument(
    UUID id,
    UUID orderId,
    DocumentType documentType,
    DocumentStatus status,
    byte[] pdfContent,
    Instant createdAt) {

  public SimulatedDocument {
    Objects.requireNonNull(id);
    Objects.requireNonNull(orderId);
    Objects.requireNonNull(documentType);
    Objects.requireNonNull(status);
    Objects.requireNonNull(pdfContent);
    Objects.requireNonNull(createdAt);
  }

  public DocumentReceipt toReceipt() {
    return new DocumentReceipt(id, orderId, documentType, status);
  }
}
