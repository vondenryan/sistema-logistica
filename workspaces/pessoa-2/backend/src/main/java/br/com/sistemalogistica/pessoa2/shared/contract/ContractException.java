package br.com.sistemalogistica.pessoa2.shared.contract;

import java.util.List;
import java.util.Map;

public class ContractException extends contracts.v1.ContractException {

	private final String code;
	private final String message;
	private final List<Map<String, Object>> details;

	public ContractException(String code, String message) {
		this(code, message, List.of());
	}

	public ContractException(String code, String message, List<Map<String, Object>> details) {
		super(code, details);
		this.code = code;
		this.message = message;
		this.details = List.copyOf(details);
	}

	public String getCode() {
		return code;
	}

	@Override
	public String getMessage() {
		return message;
	}

	public List<Map<String, Object>> getDetails() {
		return details;
	}

	public static ContractException validation(String field, String message) {
		return new ContractException(
				"VALIDATION_ERROR",
				"Request validation failed.",
				List.of(Map.of("field", field, "reason", reasonFor(message))));
	}

	private static String reasonFor(String message) {
		String normalized = message.toLowerCase(java.util.Locale.ROOT);
		if (normalized.contains("between")) {
			return "MUST_BE_IN_RANGE";
		}
		if (normalized.contains("greater than zero")) {
			return "MUST_BE_POSITIVE";
		}
		if (normalized.contains("zero or greater")) {
			return "MUST_BE_NON_NEGATIVE";
		}
		if (normalized.contains("not be blank")) {
			return "MUST_NOT_BE_BLANK";
		}
		if (normalized.contains("not be null")) {
			return "MUST_NOT_BE_NULL";
		}
		if (normalized.contains("at most")) {
			return "MUST_NOT_EXCEED_LIMIT";
		}
		if (normalized.contains("valid")) {
			return "MUST_BE_VALID";
		}
		if (normalized.contains("uuid")) {
			return "MUST_BE_UUID";
		}
		if (normalized.contains("decimal")) {
			return "MUST_BE_DECIMAL";
		}
		if (normalized.contains("unknown")) {
			return "UNKNOWN_FIELD";
		}
		if (normalized.contains("at least one")) {
			return "AT_LEAST_ONE_FIELD_REQUIRED";
		}
		return "INVALID_VALUE";
	}
}
