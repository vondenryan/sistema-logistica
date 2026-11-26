package br.com.sistemalogistica.pessoa2.fleet;

import java.math.BigDecimal;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.sistemalogistica.pessoa2.contracts.v1.AssignmentRequest;
import br.com.sistemalogistica.pessoa2.contracts.v1.AssignmentSnapshot;
import br.com.sistemalogistica.pessoa2.security.CurrentActorPort;
import br.com.sistemalogistica.pessoa2.shared.contract.ContractException;

@RestController
@RequestMapping("/api/v1/fleet/assignments")
@Profile("dev")
public class DevFleetAssignmentController {

	private final FleetAssignmentService service;
	private final CurrentActorPort currentActorPort;

	public DevFleetAssignmentController(FleetAssignmentService service, CurrentActorPort currentActorPort) {
		this.service = service;
		this.currentActorPort = currentActorPort;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public AssignmentSnapshot assign(@RequestBody DevFleetAssignmentRequest body) {
		FleetSupport.requireAdministrativeActor(currentActorPort);
		return service.assign(toAssignmentRequest(body));
	}

	private AssignmentRequest toAssignmentRequest(DevFleetAssignmentRequest body) {
		if (body == null) {
			throw ContractException.validation("body", "Must not be null.");
		}
		if (body.requiredCapacityKg() == null || body.requiredCapacityKg().isBlank()) {
			throw ContractException.validation("requiredCapacityKg", "Must be a decimal string.");
		}
		try {
			return new AssignmentRequest(
					body.routeId(),
					body.originId(),
					new BigDecimal(body.requiredCapacityKg()));
		} catch (NumberFormatException exception) {
			throw ContractException.validation("requiredCapacityKg", "Must be a decimal string.");
		}
	}
}
