package br.com.sistemalogistica.pessoa2.shared.contract;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestControllerAdvice(basePackages = "br.com.sistemalogistica.pessoa2")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ContractProblemHandler {

	@ExceptionHandler(ContractException.class)
	public ResponseEntity<ProblemDetail> handleContractException(ContractException exception) {
		HttpStatus status = statusFor(exception.getCode());
		return problem(status, exception.getCode(), exception.getMessage(), exception.getDetails());
	}

	@ExceptionHandler(contracts.v1.ContractException.class)
	public ResponseEntity<ProblemDetail> handleV1Exception(contracts.v1.ContractException exception) {
		return problem(statusFor(exception.code()), exception.code(), exception.getMessage(), exception.details());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ProblemDetail> handleInvalidBody(MethodArgumentNotValidException exception) {
		List<Map<String, Object>> details = exception.getBindingResult().getFieldErrors().stream()
				.map(this::fieldDetail)
				.toList();
		return problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed.", details);
	}

	@ExceptionHandler(HandlerMethodValidationException.class)
	public ResponseEntity<ProblemDetail> handleInvalidParameters(HandlerMethodValidationException exception) {
		return problem(
				HttpStatus.BAD_REQUEST,
				"VALIDATION_ERROR",
				"Request validation failed.",
				List.of(Map.of("field", "request", "reason", "INVALID_PARAMETER")));
	}

	@ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
	public ResponseEntity<ProblemDetail> handleMalformedRequest(Exception exception) {
		return problem(
				HttpStatus.BAD_REQUEST,
				"VALIDATION_ERROR",
				"Request body or path parameter is invalid.",
				List.of(Map.of("field", "request", "reason", "MALFORMED_REQUEST")));
	}

	private Map<String, Object> fieldDetail(FieldError error) {
		return Map.of(
				"field", error.getField(),
				"reason", reasonFor(error.getCode()));
	}

	private String reasonFor(String code) {
		if (code == null) {
			return "INVALID_VALUE";
		}
		return switch (code) {
			case "DecimalMax", "DecimalMin", "Max", "Min" -> "MUST_BE_IN_RANGE";
			case "Email" -> "MUST_BE_VALID";
			case "NotBlank" -> "MUST_NOT_BE_BLANK";
			case "NotNull" -> "MUST_NOT_BE_NULL";
			case "Pattern" -> "MUST_MATCH_PATTERN";
			case "Size" -> "MUST_BE_WITHIN_LIMITS";
			default -> "INVALID_VALUE";
		};
	}

	private ResponseEntity<ProblemDetail> problem(
			HttpStatus status,
			String code,
			String detail,
			List<?> details) {
		ProblemDetail body = ProblemDetail.forStatus(status);
		body.setType(URI.create("https://example.invalid/problems/" + code.toLowerCase().replace('_', '-')));
		body.setTitle(status.getReasonPhrase());
		body.setDetail(detail);
		body.setProperty("code", code);
		body.setProperty("details", details);
		return ResponseEntity.status(status)
				.contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.body(body);
	}

	private HttpStatus statusFor(String code) {
		return switch (code) {
			case "VALIDATION_ERROR" -> HttpStatus.BAD_REQUEST;
			case "UNAUTHENTICATED" -> HttpStatus.UNAUTHORIZED;
			case "FORBIDDEN" -> HttpStatus.FORBIDDEN;
			case "NOT_FOUND" -> HttpStatus.NOT_FOUND;
			case "CONFLICT", "NO_ELIGIBLE_RESOURCE", "IDEMPOTENCY_CONFLICT" -> HttpStatus.CONFLICT;
			case "PRECONDITION_FAILED" -> HttpStatus.UNPROCESSABLE_ENTITY;
			default -> HttpStatus.INTERNAL_SERVER_ERROR;
		};
	}
}
