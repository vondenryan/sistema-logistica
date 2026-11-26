package contracts.v1;

import java.util.List;

public class ContractException extends RuntimeException {

	private final String code;
	private final List<?> details;

	public ContractException(String code) {
		this(code, List.of());
	}

	public ContractException(String code, List<?> details) {
		super(code);
		this.code = code;
		this.details = List.copyOf(details);
	}

	public String code() {
		return code;
	}

	public List<?> details() {
		return details;
	}
}
