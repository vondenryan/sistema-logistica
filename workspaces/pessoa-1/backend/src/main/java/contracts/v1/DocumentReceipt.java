package contracts.v1;

import java.math.*;
import java.time.*;
import java.util.*;

public record DocumentReceipt(
    UUID documentId, UUID orderId, DocumentType documentType, DocumentStatus status) {}
