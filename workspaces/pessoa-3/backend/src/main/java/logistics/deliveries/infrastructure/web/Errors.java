package logistics.deliveries.infrastructure.web;

import contracts.v1.ContractException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class Errors {

  public static int status(String code) {
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

  public static ProblemDetail createProblem(String code, String detail, String traceId) {
    int httpStatus = status(code);
    String title = code.replace('_', ' ').toLowerCase(Locale.ROOT);
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatusCode.valueOf(httpStatus),
            detail != null ? detail : title);
    problem.setTitle(title);
    problem.setType(
        URI.create(
            "https://example.invalid/problems/" + code.toLowerCase(Locale.ROOT).replace('_', '-')));
    problem.setProperty("code", code);
    problem.setProperty("details", List.of());
    if (traceId != null) {
      problem.setProperty("traceId", traceId);
    }
    return problem;
  }

  private String getTraceId(HttpServletRequest request) {
    if (request == null) {
      return null;
    }
    Object attr = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTR);
    return attr != null ? attr.toString() : null;
  }

  @ExceptionHandler(ContractException.class)
  public ResponseEntity<ProblemDetail> handleContract(
      ContractException ex, HttpServletRequest request) {
    String traceId = getTraceId(request);
    ProblemDetail problem = createProblem(ex.code(), ex.getMessage(), traceId);
    problem.setProperty("details", ex.details());
    return ResponseEntity.status(status(ex.code()))
        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE)
        .body(problem);
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class,
    MethodArgumentNotValidException.class,
    MissingServletRequestParameterException.class
  })
  public ResponseEntity<ProblemDetail> handleInvalid(
      Exception e, HttpServletRequest request) {
    String traceId = getTraceId(request);
    ProblemDetail problem = createProblem("VALIDATION_ERROR", "Invalid request content", traceId);
    return ResponseEntity.badRequest()
        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE)
        .body(problem);
  }

  @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
  public ResponseEntity<ProblemDetail> handleNotFound(
      Exception e, HttpServletRequest request) {
    String traceId = getTraceId(request);
    ProblemDetail problem = createProblem("NOT_FOUND", "Resource not found", traceId);
    return ResponseEntity.status(404)
        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE)
        .body(problem);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> handleOther(
      Exception e, HttpServletRequest request) {
    String traceId = getTraceId(request);
    ProblemDetail problem = createProblem("INTERNAL_ERROR", "An unexpected error occurred", traceId);
    return ResponseEntity.internalServerError()
        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE)
        .body(problem);
  }
}
