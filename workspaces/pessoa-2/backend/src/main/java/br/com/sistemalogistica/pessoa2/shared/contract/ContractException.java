package br.com.sistemalogistica.pessoa2.shared.contract;

import java.util.List;
import java.util.Map;

public class ContractException extends RuntimeException {

	private final String code;
	private final List<Map<String, Object>> details;

	public ContractException(String code, String message) {
		this(code, message, List.of());
	}

	public ContractException(String code, String message, List<Map<String, Object>> details) {
		super(message);
		this.code = code;
		this.details = List.copyOf(details);
	}

	public String getCode() {
		return code;
	}

	public List<Map<String, Object>> getDetails() {
		return details;
	}

	public static ContractException validation(String field, String message) {
		return new ContractException(
				"VALIDATION_ERROR",
				"Request validation failed.",
				List.of(Map.of("field", field, "message", message)));
	}
}
