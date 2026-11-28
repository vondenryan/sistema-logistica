package logistics.identity.infrastructure;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import contracts.v1.*;
import java.net.URI;
import java.util.*;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
@Order(-1)
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
      case "UPSTREAM_ERROR" -> 502;
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

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ProblemDetail> unreadable(HttpMessageNotReadableException error) {
    var problem = body("VALIDATION_ERROR");
    problem.setDetail("O corpo da requisição contém JSON inválido ou campos em formato incorreto.");
    String field = "body";
    String reason = "MALFORMED_REQUEST";
    if (error.getCause() instanceof JsonMappingException mapping) {
      reason = mapping instanceof UnrecognizedPropertyException ? "UNKNOWN_FIELD" : "INVALID_VALUE";
      var parts = new ArrayList<String>();
      for (var entry : mapping.getPath()) {
        String name = entry.getFieldName();
        if (name == null && entry.getIndex() >= 0) {
          parts.add(Integer.toString(entry.getIndex()));
        } else if (name != null && !(mapping instanceof UnrecognizedPropertyException)) {
          parts.add(name);
        }
      }
      if (!parts.isEmpty()) field = String.join(".", parts);
    }
    problem.setProperty("details", List.of(Map.of("field", field, "reason", reason)));
    return ResponseEntity.badRequest()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problem);
  }

  @ExceptionHandler({
    MethodArgumentTypeMismatchException.class,
    MethodArgumentNotValidException.class,
    org.springframework.web.bind.MissingServletRequestParameterException.class
  })
  ResponseEntity<ProblemDetail> invalid(Exception e) {
    return ResponseEntity.badRequest()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(body("VALIDATION_ERROR"));
  }

  @ExceptionHandler({
    org.springframework.web.servlet.resource.NoResourceFoundException.class,
    org.springframework.web.servlet.NoHandlerFoundException.class
  })
  ResponseEntity<ProblemDetail> missing(Exception e) {
    return ResponseEntity.status(404)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(body("NOT_FOUND"));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> other(Exception e) {
    return ResponseEntity.internalServerError()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(body("INTERNAL_ERROR"));
  }
}
