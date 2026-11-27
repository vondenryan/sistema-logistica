package br.com.sistemalogistica.pessoa2.fleet;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.sistemalogistica.pessoa2.contracts.v1.AssignmentRequest;
import br.com.sistemalogistica.pessoa2.contracts.v1.AssignmentSnapshot;
import br.com.sistemalogistica.pessoa2.contracts.v1.FleetAssignmentPort;
import br.com.sistemalogistica.pessoa2.contracts.v1.FleetReleasePort;
import br.com.sistemalogistica.pessoa2.contracts.v1.ReleaseReceipt;
import br.com.sistemalogistica.pessoa2.contracts.v1.ReleaseRequest;
import br.com.sistemalogistica.pessoa2.contracts.v1.RouteSnapshotPort;
import br.com.sistemalogistica.pessoa2.security.CurrentActorPort;
import br.com.sistemalogistica.pessoa2.shared.contract.ContractException;

@Service
public class FleetAssignmentService implements FleetAssignmentPort, FleetReleasePort {

	private final FleetAssignmentRepository assignmentRepository;
	private final FleetAssignmentTransactionService transactionService;
	private final RouteSnapshotPort routeSnapshotPort;
	private final CurrentActorPort currentActorPort;

	public FleetAssignmentService(
			FleetAssignmentRepository assignmentRepository,
			FleetAssignmentTransactionService transactionService,
			RouteSnapshotPort routeSnapshotPort,
			CurrentActorPort currentActorPort) {
		this.assignmentRepository = assignmentRepository;
		this.transactionService = transactionService;
		this.routeSnapshotPort = routeSnapshotPort;
		this.currentActorPort = currentActorPort;
	}

	@Override
	public AssignmentSnapshot assign(AssignmentRequest request) {
		validateAssignmentRequest(request);
		return transactionService.reserve(request);
	}

	@Override
	public ReleaseReceipt release(ReleaseRequest request) {
		validateReleaseRequest(request);
		return transactionService.release(request);
	}

	@Transactional(readOnly = true)
	public AssignmentSnapshot get(UUID routeId) {
		FleetSupport.requireAdministrativeActor(currentActorPort);
		routeSnapshotPort.getById(routeId);
		FleetAssignment assignment = assignmentRepository.findByRouteId(routeId)
				.orElseThrow(FleetAssignmentService::notFound);
		return new AssignmentSnapshot(
				assignment.getAssignmentId(),
				assignment.getRouteId(),
				assignment.getDriverId(),
				assignment.getVehicleId(),
				assignment.getCarrierId(),
				assignment.getFleetType());
	}

	private static void validateAssignmentRequest(AssignmentRequest request) {
		if (request == null) {
			throw ContractException.validation("request", "Must not be null.");
		}
		if (request.routeId() == null) {
			throw ContractException.validation("routeId", "Must not be null.");
		}
		if (request.originId() == null) {
			throw ContractException.validation("originId", "Must not be null.");
		}
		BigDecimal capacity = request.requiredCapacityKg();
		if (capacity == null || capacity.signum() <= 0) {
			throw ContractException.validation("requiredCapacityKg", "Must be greater than zero.");
		}
		if (capacity.scale() > 3 || capacity.precision() - capacity.scale() > 9) {
			throw ContractException.validation(
					"requiredCapacityKg",
					"Must fit decimal(12,3).");
		}
	}

	private static void validateReleaseRequest(ReleaseRequest request) {
		if (request == null) {
			throw ContractException.validation("request", "Must not be null.");
		}
		if (request.routeId() == null) {
			throw ContractException.validation("routeId", "Must not be null.");
		}
		if (request.eventId() == null) {
			throw ContractException.validation("eventId", "Must not be null.");
		}
		if (request.reason() == null) {
			throw ContractException.validation("reason", "Must not be null.");
		}
	}

	private static ContractException notFound() {
		return new ContractException("NOT_FOUND", "Fleet assignment was not found.");
	}

}
