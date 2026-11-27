package logistics.documents.infrastructure.web;

import contracts.v1.DocumentType;
import java.util.UUID;

public record CreateDocumentRequest(UUID orderId, DocumentType documentType) {}
