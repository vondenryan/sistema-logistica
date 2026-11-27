package br.com.sistemalogistica.pessoa2.fleet;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.sistemalogistica.pessoa2.contracts.v1.AssignmentSnapshot;

@RestController
@RequestMapping("/api/v1/fleet/assignments")
public class FleetAssignmentController {

	private final FleetAssignmentService service;

	public FleetAssignmentController(FleetAssignmentService service) {
		this.service = service;
	}

	@GetMapping("/{routeId}")
	public AssignmentSnapshot get(@PathVariable UUID routeId) {
		return service.get(routeId);
	}
}
