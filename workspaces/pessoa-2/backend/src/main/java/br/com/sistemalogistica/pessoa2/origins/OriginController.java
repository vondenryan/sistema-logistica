package br.com.sistemalogistica.pessoa2.origins;

import java.util.UUID;

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

@RestController
@RequestMapping("/api/v1/origins")
public class OriginController {

	private final OriginService service;

	public OriginController(OriginService service) {
		this.service = service;
	}

	@GetMapping
	public OriginPageResponse list(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return service.list(page, size);
	}

	@GetMapping("/{originId}")
	public OriginSnapshot get(@PathVariable UUID originId) {
		return service.get(originId);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public OriginSnapshot create(@Valid @RequestBody CreateOriginRequest request) {
		return service.create(request);
	}

	@PatchMapping("/{originId}")
	public OriginSnapshot patch(@PathVariable UUID originId, @Valid @RequestBody PatchOriginRequest request) {
		return service.patch(originId, request);
	}
}
