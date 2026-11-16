package logistics.identity.infrastructure;

import contracts.v1.*;
import java.net.URI;
import java.util.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
class Errors {
  static int status(String code) {
    return switch (code) {
      case "VALIDATION_ERROR" -> 400;
      case "UNAUTHENTICATED" -> 401;
      case "FORBIDDEN" -> 403;
      case "NOT_FOUND" -> 404;
      case "CONFLICT", "IDEMPOTENCY_CONFLICT", "NO_ELIGIBLE_RESOURCE", "ALREADY_ASSIGNED" -> 409;
      case "PRECONDITION_FAILED" -> 422;
      case "RATE_LIMITED" -> 429;
      case "DEPENDENCY_UNAVAILABLE" -> 503;
      default -> 500;
    };
  }

  static ProblemDetail body(String code) {
    int status = status(code);
    ProblemDetail p =
        ProblemDetail.forStatusAndDetail(
            HttpStatusCode.valueOf(status), code.replace('_', ' ').toLowerCase(Locale.ROOT));
    p.setTitle(code.replace('_', ' ').toLowerCase(Locale.ROOT));
    p.setType(
        URI.create(
            "https://example.invalid/problems/" + code.toLowerCase(Locale.ROOT).replace('_', '-')));
    p.setProperty("code", code);
    p.setProperty("details", List.of());
    return p;
  }

  @ExceptionHandler(ContractException.class)
  ResponseEntity<ProblemDetail> contract(ContractException ex) {
    var p = body(ex.code());
    p.setProperty("details", ex.details());
    return ResponseEntity.status(status(ex.code()))
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(p);
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class,
    MethodArgumentNotValidException.class
  })
  ResponseEntity<ProblemDetail> invalid(Exception e) {
    return ResponseEntity.badRequest()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(body("VALIDATION_ERROR"));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> other(Exception e) {
    return ResponseEntity.internalServerError()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(body("INTERNAL_ERROR"));
  }
}
