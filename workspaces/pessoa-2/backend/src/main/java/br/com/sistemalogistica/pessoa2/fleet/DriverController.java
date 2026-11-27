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
@RequestMapping("/api/v1/drivers")
public class DriverController {

	private final DriverService service;

	public DriverController(DriverService service) {
		this.service = service;
	}

	@GetMapping
	public FleetPageResponse<DriverResponse> list(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return service.list(page, size);
	}

	@GetMapping("/{driverId}")
	public DriverResponse get(@PathVariable UUID driverId) {
		return service.get(driverId);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public DriverResponse create(@RequestBody JsonNode body) {
		return service.create(FleetRequestParser.driverCreate(body));
	}

	@PatchMapping("/{driverId}")
	public DriverResponse patch(@PathVariable UUID driverId, @RequestBody JsonNode body) {
		return service.patch(driverId, FleetRequestParser.driverPatch(body));
	}
}
