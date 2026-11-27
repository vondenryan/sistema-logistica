package br.com.sistemalogistica.pessoa2.stores;

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

import java.util.UUID;

import br.com.sistemalogistica.pessoa2.shared.contract.ContractException;

@RestController
@RequestMapping("/api/v1/stores")
public class StoreController {

	private final StoreService service;

	public StoreController(StoreService service) {
		this.service = service;
	}

	@GetMapping
	public StorePageResponse list(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		if (page < 0) {
			throw ContractException.validation("page", "Must be zero or greater.");
		}
		if (size < 1 || size > 100) {
			throw ContractException.validation("size", "Must be between 1 and 100.");
		}
		return service.list(page, size);
	}

	@GetMapping("/{storeId}")
	public StoreResponse get(@PathVariable UUID storeId) {
		return service.get(storeId);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public StoreResponse create(@Valid @RequestBody CreateStoreRequest request) {
		return service.create(request);
	}

	@PatchMapping("/{storeId}")
	public StoreResponse patch(@PathVariable UUID storeId, @Valid @RequestBody PatchStoreRequest request) {
		return service.patch(storeId, request);
	}
}
