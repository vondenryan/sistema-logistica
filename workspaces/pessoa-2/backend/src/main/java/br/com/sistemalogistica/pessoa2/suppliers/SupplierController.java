package br.com.sistemalogistica.pessoa2.suppliers;

import java.util.UUID;
import java.util.Map;
import java.util.Set;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.sistemalogistica.pessoa2.shared.contract.ContractException;

@RestController
@RequestMapping("/api/v1/suppliers")
public class SupplierController {

	private final SupplierService service;

	public SupplierController(SupplierService service) {
		this.service = service;
	}

	@GetMapping
	public SupplierPageResponse list(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return service.list(page, size);
	}

	@GetMapping("/{supplierId}")
	public SupplierResponse get(@PathVariable UUID supplierId) {
		return service.get(supplierId);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public SupplierResponse create(@Valid @RequestBody CreateSupplierRequest request) {
		return service.create(request);
	}

	@PatchMapping("/{supplierId}")
	public SupplierResponse patch(
			@PathVariable UUID supplierId,
			@RequestBody Map<String, Object> body) {
		return service.patch(supplierId, patchRequest(body));
	}

	private PatchSupplierRequest patchRequest(Map<String, Object> body) {
		if (body == null) {
			throw ContractException.validation("body", "A JSON object must be provided.");
		}

		Set<String> allowed = Set.of("name", "contactEmail", "storeId", "active");
		for (String field : body.keySet()) {
			if (!allowed.contains(field)) {
				throw ContractException.validation(field, "Unknown field.");
			}
		}

		if (body.containsKey("contactEmail") && body.get("contactEmail") == null) {
			throw ContractException.validation("contactEmail", "Must not be null.");
		}
		if (body.containsKey("name") && body.get("name") == null) {
			throw ContractException.validation("name", "Must not be null.");
		}
		if (body.containsKey("storeId") && body.get("storeId") == null) {
			throw ContractException.validation("storeId", "Must not be null.");
		}

		String name = body.containsKey("name") && body.get("name") != null
				? (String) validateType(body, "name", String.class)
				: null;
		String contactEmail = body.containsKey("contactEmail") && body.get("contactEmail") != null
				? (String) validateType(body, "contactEmail", String.class)
				: null;

		UUID storeId = null;
		if (body.containsKey("storeId") && body.get("storeId") != null) {
			String value = (String) validateType(body, "storeId", String.class);
			try {
				storeId = UUID.fromString(value);
			} catch (IllegalArgumentException exception) {
				throw ContractException.validation("storeId", "Must be a UUID.");
			}
		}

		Boolean active = null;
		if (body.containsKey("active") && body.get("active") != null) {
			active = (Boolean) validateType(body, "active", Boolean.class);
		}

		return new PatchSupplierRequest(name, contactEmail, storeId, active);
	}

	private Object validateType(Map<String, Object> body, String field, Class<?> type) {
		Object value = body.get(field);
		if (!type.isInstance(value)) {
			throw ContractException.validation(field, "Invalid value type.");
		}
		return value;
	}
}
