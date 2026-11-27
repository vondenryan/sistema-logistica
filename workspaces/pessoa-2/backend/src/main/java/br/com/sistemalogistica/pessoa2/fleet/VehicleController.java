package br.com.sistemalogistica.pessoa2.fleet;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

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
@RequestMapping("/api/v1/vehicles")
public class VehicleController {

	private final VehicleService service;

	public VehicleController(VehicleService service) {
		this.service = service;
	}

	@GetMapping
	public FleetPageResponse<VehicleResponse> list(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return service.list(page, size);
	}

	@GetMapping("/{vehicleId}")
	public VehicleResponse get(@PathVariable UUID vehicleId) {
		return service.get(vehicleId);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public VehicleResponse create(@RequestBody JsonNode body) {
		return service.create(FleetRequestParser.vehicleCreate(body));
	}

	@PatchMapping("/{vehicleId}")
	public VehicleResponse patch(@PathVariable UUID vehicleId, @RequestBody JsonNode body) {
		return service.patch(vehicleId, FleetRequestParser.vehiclePatch(body));
	}
}
