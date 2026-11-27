package logistics.documents.domain;

import contracts.v1.DocumentType;
import java.util.Optional;
import java.util.UUID;

public interface SimulatedDocumentRepository {
  Optional<SimulatedDocument> findById(UUID id);

  Optional<SimulatedDocument> findByOrderAndType(UUID orderId, DocumentType documentType);

  boolean insertIfAbsent(SimulatedDocument document);
}
