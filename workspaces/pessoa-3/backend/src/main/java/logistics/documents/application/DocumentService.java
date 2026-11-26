package logistics.documents.application;

import contracts.v1.ActorContext;
import contracts.v1.ContractException;
import contracts.v1.CurrentActorPort;
import contracts.v1.DocumentReceipt;
import contracts.v1.DocumentRequest;
import contracts.v1.DocumentRequestPort;
import contracts.v1.DocumentStatus;
import contracts.v1.DocumentType;
import contracts.v1.OrderSnapshot;
import contracts.v1.OrderSnapshotPort;
import contracts.v1.Role;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import logistics.documents.domain.SimulatedDocument;
import logistics.documents.domain.SimulatedDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentService implements DocumentRequestPort {
  private final SimulatedDocumentRepository documentRepository;
  private final OrderSnapshotPort orderSnapshotPort;
  private final CurrentActorPort currentActorPort;
  private final PdfDocumentGenerator pdfGenerator;

  public record GenerateOutcome(DocumentReceipt receipt, boolean createdNew) {}

  public DocumentService(
      SimulatedDocumentRepository documentRepository,
      OrderSnapshotPort orderSnapshotPort,
      CurrentActorPort currentActorPort,
      PdfDocumentGenerator pdfGenerator) {
    this.documentRepository = documentRepository;
    this.orderSnapshotPort = orderSnapshotPort;
    this.currentActorPort = currentActorPort;
    this.pdfGenerator = pdfGenerator;
  }

  @Override
  @Transactional
  public DocumentReceipt ensureGenerated(DocumentRequest request) {
    return ensureGeneratedInternal(request).receipt();
  }

  @Transactional
  public GenerateOutcome createOrGetForApi(UUID orderId, DocumentType documentType) {
    if (orderId == null || documentType == null) {
      throw new ContractException("VALIDATION_ERROR");
    }

    ActorContext actor = currentActorPort.requireActor();
    if (actor.role() == Role.DRIVER) {
      throw new ContractException("FORBIDDEN");
    }

    OrderSnapshot order = orderSnapshotPort.getById(orderId);
    validateStoreScope(actor, order);

    return ensureGeneratedInternal(new DocumentRequest(orderId, documentType));
  }

  @Transactional(readOnly = true)
  public DocumentReceipt getReceipt(UUID documentId) {
    if (documentId == null) {
      throw new ContractException("VALIDATION_ERROR");
    }
    ActorContext actor = currentActorPort.requireActor();
    if (actor.role() == Role.DRIVER) {
      throw new ContractException("FORBIDDEN");
    }

    SimulatedDocument doc =
        documentRepository
            .findById(documentId)
            .orElseThrow(() -> new ContractException("NOT_FOUND"));
    OrderSnapshot order = orderSnapshotPort.getById(doc.orderId());
    validateStoreScope(actor, order);

    return doc.toReceipt();
  }

  @Transactional(readOnly = true)
  public byte[] getPdfBytes(UUID documentId) {
    if (documentId == null) {
      throw new ContractException("VALIDATION_ERROR");
    }
    ActorContext actor = currentActorPort.requireActor();
    if (actor.role() == Role.DRIVER) {
      throw new ContractException("FORBIDDEN");
    }

    SimulatedDocument doc =
        documentRepository
            .findById(documentId)
            .orElseThrow(() -> new ContractException("NOT_FOUND"));
    OrderSnapshot order = orderSnapshotPort.getById(doc.orderId());
    validateStoreScope(actor, order);

    return doc.pdfContent();
  }

  private GenerateOutcome ensureGeneratedInternal(DocumentRequest request) {
    if (request == null || request.orderId() == null || request.documentType() == null) {
      throw new ContractException("VALIDATION_ERROR");
    }

    Optional<SimulatedDocument> existing =
        documentRepository.findByOrderAndType(request.orderId(), request.documentType());
    if (existing.isPresent()) {
      return new GenerateOutcome(existing.get().toReceipt(), false);
    }

    OrderSnapshot order = orderSnapshotPort.getById(request.orderId());
    if (order.freightAmount() == null) {
      throw new ContractException("PRECONDITION_FAILED");
    }

    UUID docId = UUID.randomUUID();
    Instant now = Instant.now();
    byte[] pdfBytes = pdfGenerator.generate(docId, request.documentType(), order, now);

    SimulatedDocument created =
        new SimulatedDocument(
            docId,
            request.orderId(),
            request.documentType(),
            DocumentStatus.GENERATED,
            pdfBytes,
            now);
    if (documentRepository.insertIfAbsent(created)) {
      return new GenerateOutcome(created.toReceipt(), true);
    }
    SimulatedDocument persisted =
        documentRepository
            .findByOrderAndType(request.orderId(), request.documentType())
            .orElseThrow();
    return new GenerateOutcome(persisted.toReceipt(), false);
  }

  private static void validateStoreScope(ActorContext actor, OrderSnapshot order) {
    if (actor.role() == Role.ADMIN || actor.role() == Role.OPERATOR) {
      return;
    }
    if (actor.role() == Role.STORE) {
      if (actor.storeId() == null || !Objects.equals(actor.storeId(), order.storeId())) {
        // Hide cross-store resources with 404 without revealing existence
        throw new ContractException("NOT_FOUND");
      }
      return;
    }
    throw new ContractException("FORBIDDEN");
  }
}
