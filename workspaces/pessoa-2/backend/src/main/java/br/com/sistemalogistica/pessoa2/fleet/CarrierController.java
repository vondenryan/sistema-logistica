package br.com.sistemalogistica.pessoa2.fleet;

import java.util.UUID;

import tools.jackson.databind.JsonNode;

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
@RequestMapping("/api/v1/carriers")
public class CarrierController {

	private final CarrierService service;

	public CarrierController(CarrierService service) {
		this.service = service;
	}

	@GetMapping
	public FleetPageResponse<CarrierResponse> list(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return service.list(page, size);
	}

	@GetMapping("/{carrierId}")
	public CarrierResponse get(@PathVariable UUID carrierId) {
		return service.get(carrierId);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public CarrierResponse create(@RequestBody JsonNode body) {
		return service.create(FleetRequestParser.carrierCreate(body));
	}

	@PatchMapping("/{carrierId}")
	public CarrierResponse patch(@PathVariable UUID carrierId, @RequestBody JsonNode body) {
		return service.patch(carrierId, FleetRequestParser.carrierPatch(body));
	}
}
