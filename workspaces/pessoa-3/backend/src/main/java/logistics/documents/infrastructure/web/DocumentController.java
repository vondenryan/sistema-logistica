package logistics.documents.infrastructure.web;

import contracts.v1.DocumentReceipt;
import java.util.UUID;
import logistics.documents.application.DocumentService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {
  private final DocumentService documentService;

  public DocumentController(DocumentService documentService) {
    this.documentService = documentService;
  }

  @PostMapping
  public ResponseEntity<DocumentReceipt> create(@RequestBody CreateDocumentRequest request) {
    DocumentService.GenerateOutcome outcome =
        documentService.createOrGetForApi(request.orderId(), request.documentType());
    HttpStatus status = outcome.createdNew() ? HttpStatus.CREATED : HttpStatus.OK;
    return ResponseEntity.status(status).body(outcome.receipt());
  }

  @GetMapping("/{documentId}")
  public ResponseEntity<DocumentReceipt> getById(@PathVariable("documentId") UUID documentId) {
    return ResponseEntity.ok(documentService.getReceipt(documentId));
  }

  @GetMapping("/{documentId}/pdf")
  public ResponseEntity<byte[]> getPdf(@PathVariable("documentId") UUID documentId) {
    byte[] pdf = documentService.getPdfBytes(documentId);
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "inline; filename=\"document-" + documentId + ".pdf\"")
        .body(pdf);
  }
}
